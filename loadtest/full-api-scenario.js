import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// Comprehensive load test for meetjyou-backend local dev instance.
// Covers every REST endpoint that is safe to hit from a local dev DB: auth, user profile,
// post, plan, marker, party (join/approve/reject), chat (read-side), push-token,
// notification-center, notice (read-only), terms (read-only), version (read-only).
//
// Deliberately EXCLUDED:
// - OracleObjectStorageController (all /*/img/par/* endpoints) — these call real OCI Object
//   Storage even under the dev profile (no mock), so they are out of scope for a local test.
// - Notice/Terms(write)/Version(write) admin endpoints and GET /users (all admin-only,
//   @PreAuthorize ADMIN) — except addVersion, which setup() calls once to seed a version row
//   (see below); it never touches OCI, unlike Terms.publishTerms.
// - Chat message send (ChatController.handleMessage is a STOMP @MessageMapping, not REST —
//   needs a SockJS/STOMP client, see .claude/rules/chat.md for the integration-test pattern).
// - PartyController.banMember / deleteParty / leaveParty / completeParty — destructive to the
//   shared party the VU itself owns and not needed to exercise the read/write paths.
//
// KNOWN GAP: PATCH /users/me/marketing-consent 404s on a fresh DB because it requires a
// published Terms(type=MARKETING_SNS_EVENTS) row, and Terms.publishTerms validates the content
// was actually uploaded to OCI (hash check) — there's no way to seed it without OCI, so this
// check is intentionally left accepting 404 rather than treated as a failure.
//
// Requires: SPRING_PROFILES_ACTIVE=dev,db,secrets (dev bypass enabled)
// Run: k6 run loadtest/full-api-scenario.js
// Override target: k6 run -e BASE_URL=http://localhost:8080 loadtest/full-api-scenario.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_PASSPHRASE = __ENV.ADMIN_PASSPHRASE || 'dev-admin-passphrase';

const errorRate = new Rate('errors');

// Per-group latency, so the end-of-test summary shows which section of the journey (not just
// the overall p95) is disproportionately slow.
const groupTrends = {
  'profile setup': new Trend('group_profile_setup_ms'),
  browse: new Trend('group_browse_ms'),
  'content creation': new Trend('group_content_creation_ms'),
  'social interaction': new Trend('group_social_interaction_ms'),
  'push & chat read': new Trend('group_push_chat_read_ms'),
  'notification center': new Trend('group_notification_center_ms'),
};

function timedGroup(name, fn) {
  const start = Date.now();
  group(name, fn);
  groupTrends[name].add(Date.now() - start);
}

// Seeds an AppVersion(platform=ANDROID, version=1.0.0) row via the admin API so
// PushTokenController.register (which validates appVersion against this table) doesn't 404
// on a fresh DB. addVersion is local-DB-only (no OCI call), unlike Terms.publishTerms.
export function setup() {
  const unique = `${Date.now()}${Math.floor(Math.random() * 100000)}`;
  const regRes = http.post(
    `${BASE_URL}/api/v1/dev/auth/register`,
    JSON.stringify({ email: `loadtest-setup-${unique}@local.test`, nickname: 'setupadm' }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (regRes.status !== 201) {
    console.warn(`setup: dev register failed (${regRes.status}), version seed skipped`);
    return {};
  }
  const promoteRes = http.post(
    `${BASE_URL}/api/v1/auth/promote-admin`,
    JSON.stringify({ passphrase: ADMIN_PASSPHRASE }),
    { headers: { Authorization: `Bearer ${regRes.json('accessToken')}`, 'Content-Type': 'application/json' } },
  );
  if (promoteRes.status !== 200) {
    console.warn(`setup: promote-admin failed (${promoteRes.status}), version seed skipped`);
    return {};
  }
  const adminToken = promoteRes.json('accessToken');
  const versionRes = http.post(
    `${BASE_URL}/api/v1/android/versions`,
    JSON.stringify({
      platform: 'ANDROID',
      version: '1.0.0',
      forceUpdate: false,
      message: null,
      storeReleased: true,
      releasedAt: '2026-01-01T00:00:00Z',
    }),
    { headers: { Authorization: `Bearer ${adminToken}`, 'Content-Type': 'application/json' } },
  );
  if (![201, 409].includes(versionRes.status)) {
    console.warn(`setup: addVersion failed (${versionRes.status}): ${versionRes.body}`);
  }
  return {};
}

export const options = {
  scenarios: {
    // Main journey: register once per VU, then repeatedly browse/create/interact.
    full_journey: {
      executor: 'ramping-vus',
      exec: 'journey',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 10 },
        { duration: '40s', target: 10 },
        { duration: '20s', target: 30 },
        { duration: '40s', target: 30 },
        { duration: '20s', target: 50 },
        { duration: '40s', target: 50 },
        { duration: '20s', target: 0 },
      ],
    },
    // Separate, low-volume scenario for session lifecycle (refresh/logout/withdraw) so the
    // main journey VUs don't lose their session mid-test.
    auth_lifecycle: {
      executor: 'per-vu-iterations',
      exec: 'lifecycle',
      vus: 5,
      iterations: 3,
      maxDuration: '3m',
      startTime: '5s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<800'],
    errors: ['rate<0.05'],
  },
};

const PERSONALITIES = ['INTROVERTED', 'EXTROVERTED', 'SOCIAL', 'OPTIMISTIC', 'FREE', 'PRACTICAL', 'CAREFUL', 'BOLD'];
const TRAVEL_STYLES = ['ACTIVITY', 'RELAX', 'CULTURE', 'FOOD', 'SPORTS', 'BUDGET', 'LUXURY', 'ADVENTURE', 'NATURE', 'URBAN', 'ART', 'SHOP'];
const DIETS = ['VEGETARIAN', 'GLUTEN_FREE', 'VEGAN', 'SPECIFIC', 'ANYTHING'];
const ETCS = ['SMOKE', 'DRINK', 'DONT_CARE'];

function pick(arr, n) {
  return arr.slice(0, n);
}

function registerVU(tag) {
  const unique = `${Date.now()}${Math.floor(Math.random() * 100000)}`;
  const email = `loadtest-${tag}${__VU}-${unique}@local.test`;
  const seed = __VU * 1e6 + __ITER * 1000 + Math.floor(Math.random() * 1000);
  const nickname = `u${seed.toString(36)}`.slice(0, 8);
  const res = http.post(
    `${BASE_URL}/api/v1/dev/auth/register`,
    JSON.stringify({ email, nickname }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(res, { 'register status 201': (r) => r.status === 201 }) || errorRate.add(1);
  if (res.status !== 201) return null;
  const body = res.json();
  return { uuid: body.uuid, accessToken: body.accessToken, refreshToken: body.refreshToken, nickname };
}

function authHeaders(session) {
  return { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.accessToken}` } };
}

let vuSession = null;
let vuState = {}; // ownPostUuid, ownPartyUuid, ownPlanUuid, ownRoomUuid, joinedPartyUuid

export function journey() {
  if (!vuSession) {
    vuSession = registerVU('vu');
    if (!vuSession) {
      sleep(1);
      return;
    }
  }
  const h = authHeaders(vuSession);

  timedGroup('profile setup', () => {
    const dup = http.get(`${BASE_URL}/api/v1/users/is-duplicate-nickname?nickname=${vuSession.nickname}`, h);
    check(dup, { 'nickname dup check 200': (r) => r.status === 200 }) || errorRate.add(1);

    const updateRes = http.put(
      `${BASE_URL}/api/v1/users/me`,
      JSON.stringify({
        nickname: vuSession.nickname,
        bio: 'k6 load test bio',
        gender: 'O',
        age: 'TWENTY',
        personalities: pick(PERSONALITIES, 2),
        travelStyles: pick(TRAVEL_STYLES, 2),
        diet: pick(DIETS, 1),
        etc: pick(ETCS, 1),
      }),
      h,
    );
    check(updateRes, { 'update profile 200': (r) => r.status === 200 }) || errorRate.add(1);

    const consentRes = http.patch(
      `${BASE_URL}/api/v1/users/me/marketing-consent`,
      JSON.stringify({ snsConsented: true, emailConsented: false }),
      h,
    );
    // 404 is expected on a DB with no published MARKETING_SNS_EVENTS terms — see KNOWN GAP above.
    check(consentRes, { 'marketing consent 204/404(no terms seeded)': (r) => r.status === 204 || r.status === 404 }) || errorRate.add(1);

    const settingsGet = http.get(`${BASE_URL}/api/v1/users/me/notification-settings`, h);
    check(settingsGet, { 'get notification settings 200': (r) => r.status === 200 }) || errorRate.add(1);

    const settingsPut = http.put(
      `${BASE_URL}/api/v1/users/me/notification-settings`,
      JSON.stringify({ globalEnabled: true, categories: null }),
      h,
    );
    check(settingsPut, { 'update notification settings 204': (r) => r.status === 204 }) || errorRate.add(1);

    const myProfile = http.get(`${BASE_URL}/api/v1/users/me/profile`, h);
    check(myProfile, { 'my profile ok or preference-missing': (r) => r.status === 200 || r.status === 404 }) || errorRate.add(1);
  });

  sleep(Math.random() * 1);

  timedGroup('browse', () => {
    const listPosts = http.get(`${BASE_URL}/api/v1/posts?page=0&size=20`, h);
    check(listPosts, { 'list posts 200': (r) => r.status === 200 }) || errorRate.add(1);

    check(http.get(`${BASE_URL}/api/v1/users/me/posts`, h), { 'my posts 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/users/me/plans`, h), { 'my plans 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/users/me/parties`, h), { 'my parties 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/users/me/applications`, h), { 'my applications 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/chat/rooms`, h), { 'list chat rooms 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/notices?page=0&size=10`, h), { 'list notices 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/terms/active`, h), { 'active terms 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/android/versions/latest`, h), { 'latest version 200/404': (r) => r.status === 200 || r.status === 404 }) || errorRate.add(1);

    if (vuSession.uuid) {
      check(http.get(`${BASE_URL}/api/v1/users/${vuSession.uuid}/basic-info`, h), { 'basic-info 200': (r) => r.status === 200 }) || errorRate.add(1);
      check(http.get(`${BASE_URL}/api/v1/users/${vuSession.uuid}/advanced-info`, h), { 'advanced-info 200': (r) => r.status === 200 }) || errorRate.add(1);
    }
  });

  sleep(Math.random() * 1);

  timedGroup('content creation', () => {
    const postPayload = JSON.stringify({
      title: `k6 trip ${__VU}`.slice(0, 20),
      content: 'Generated by k6 full-api load test',
      isInstant: false,
      itinStart: '2026-08-01T00:00:00Z',
      itinFinish: '2026-08-05T00:00:00Z',
      location: '제주',
      capacity: 4,
    });
    const postRes = http.post(`${BASE_URL}/api/v1/posts`, postPayload, h);
    check(postRes, { 'create post 200': (r) => r.status === 200 }) || errorRate.add(1);
    if (postRes.status === 200) {
      const body = postRes.json();
      vuState.ownPostUuid = body.uuid;
      vuState.ownRoomUuid = body.chatRoom && body.chatRoom.uuid;

      const detail = http.get(`${BASE_URL}/api/v1/posts/${vuState.ownPostUuid}`, h);
      check(detail, { 'get own post 200': (r) => r.status === 200 }) || errorRate.add(1);
      if (detail.status === 200) vuState.ownPartyUuid = detail.json('partyUuid');

      const updatePostRes = http.put(
        `${BASE_URL}/api/v1/posts/${vuState.ownPostUuid}`,
        JSON.stringify({
          title: `k6 trip ${__VU} v2`.slice(0, 20),
          content: 'Updated by k6 full-api load test',
          isInstant: false,
          itinStart: '2026-08-01T00:00:00Z',
          itinFinish: '2026-08-06T00:00:00Z',
          location: '제주',
          capacity: 4,
        }),
        h,
      );
      check(updatePostRes, { 'update post 200': (r) => r.status === 200 }) || errorRate.add(1);
    }

    const planPayload = JSON.stringify({
      title: `k6 plan ${__VU}`.slice(0, 20),
      itinStart: '2026-08-01T00:00:00Z',
      itinFinish: '2026-08-05T00:00:00Z',
      location: '제주',
      centerLat: 33.4996,
      centerLng: 126.5312,
      markers: [],
    });
    const planRes = http.post(`${BASE_URL}/api/v1/plans`, planPayload, h);
    check(planRes, { 'create plan 200': (r) => r.status === 200 }) || errorRate.add(1);
    if (planRes.status === 200) {
      vuState.ownPlanUuid = planRes.json('uuid');

      const replaceMarkersRes = http.put(
        `${BASE_URL}/api/v1/plans/${vuState.ownPlanUuid}/markers`,
        JSON.stringify({
          markers: [
            { lat: 33.5, lng: 126.53, date: '2026-08-01T00:00:00Z', dayNum: 1, idx: 0, place: '공항', memo: null },
          ],
        }),
        h,
      );
      check(replaceMarkersRes, { 'replace markers 200': (r) => r.status === 200 }) || errorRate.add(1);

      check(http.get(`${BASE_URL}/api/v1/plans/${vuState.ownPlanUuid}/markers`, h), { 'get markers 200': (r) => r.status === 200 }) || errorRate.add(1);

      const updatePlanRes = http.put(
        `${BASE_URL}/api/v1/plans/${vuState.ownPlanUuid}`,
        JSON.stringify({
          title: `k6 plan ${__VU} v2`.slice(0, 20),
          itinStart: '2026-08-01T00:00:00Z',
          itinFinish: '2026-08-06T00:00:00Z',
          location: '제주',
          centerLat: 33.4996,
          centerLng: 126.5312,
          memo: 'updated',
          favorite: true,
        }),
        h,
      );
      check(updatePlanRes, { 'update plan 200': (r) => r.status === 200 }) || errorRate.add(1);
    }
  });

  sleep(Math.random() * 1);

  timedGroup('social interaction', () => {
    // Try to find someone else's post to apply to.
    const listRes = http.get(`${BASE_URL}/api/v1/posts?page=0&size=20`, h);
    if (listRes.status === 200) {
      const content = listRes.json('content') || [];
      const other = content.find((p) => p.authorUuid !== vuSession.uuid);
      if (other) {
        const joinRes = http.post(
          `${BASE_URL}/api/v1/parties/${other.partyUuid}/join-requests`,
          JSON.stringify({ applicationNote: 'k6 load test application' }),
          h,
        );
        check(joinRes, { 'join request 201/400/409': (r) => [201, 400, 409].includes(r.status) }) || errorRate.add(1);
      }
    }

    if (vuState.ownPartyUuid) {
      check(http.get(`${BASE_URL}/api/v1/parties/${vuState.ownPartyUuid}/members`, h), { 'party members 200': (r) => r.status === 200 }) || errorRate.add(1);

      const pendingRes = http.get(`${BASE_URL}/api/v1/parties/${vuState.ownPartyUuid}/join-requests`, h);
      check(pendingRes, { 'pending join requests 200': (r) => r.status === 200 }) || errorRate.add(1);
      if (pendingRes.status === 200) {
        const pending = pendingRes.json('requests') || [];
        if (Array.isArray(pending) && pending.length > 0 && pending[0].userUuid) {
          const approveRes = http.post(
            `${BASE_URL}/api/v1/parties/${vuState.ownPartyUuid}/join-requests/${pending[0].userUuid}/approve`,
            null,
            h,
          );
          check(approveRes, { 'approve join request 204/404': (r) => r.status === 204 || r.status === 404 }) || errorRate.add(1);
        }
      }
    }
  });

  sleep(Math.random() * 1);

  timedGroup('push & chat read', () => {
    const token = `k6-fake-token-${__VU}-${__ITER}-${Math.floor(Math.random() * 1e6)}`;
    const registerPush = http.post(
      `${BASE_URL}/api/v1/push-tokens`,
      JSON.stringify({ token, platform: 'ANDROID', appVersion: '1.0.0', deviceModel: 'k6-virtual' }),
      h,
    );
    check(registerPush, { 'register push token 200': (r) => r.status === 200 }) || errorRate.add(1);

    const deactivatePush = http.del(`${BASE_URL}/api/v1/push-tokens?token=${encodeURIComponent(token)}`, null, h);
    check(deactivatePush, { 'deactivate push token 200/204': (r) => r.status === 200 || r.status === 204 }) || errorRate.add(1);

    if (vuState.ownRoomUuid) {
      check(http.get(`${BASE_URL}/api/v1/chat/rooms/${vuState.ownRoomUuid}/messages`, h), { 'chat messages 200': (r) => r.status === 200 }) || errorRate.add(1);
      check(http.get(`${BASE_URL}/api/v1/chat/rooms/${vuState.ownRoomUuid}/unread-count`, h), { 'unread count 200': (r) => r.status === 200 }) || errorRate.add(1);
      check(http.post(`${BASE_URL}/api/v1/chat/rooms/${vuState.ownRoomUuid}/read`, null, h), { 'mark as read 200/204': (r) => r.status === 200 || r.status === 204 }) || errorRate.add(1);
    }
  });

  sleep(Math.random() * 1);

  timedGroup('notification center', () => {
    check(http.get(`${BASE_URL}/api/v1/notification-center/notices`, h), { 'nc notices 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.post(`${BASE_URL}/api/v1/notification-center/notices/read`, null, h), { 'nc notices read 200/204': (r) => r.status === 200 || r.status === 204 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/notification-center/received-applications`, h), { 'nc received 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.post(`${BASE_URL}/api/v1/notification-center/received-applications/read`, null, h), { 'nc received read 200/204': (r) => r.status === 200 || r.status === 204 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/notification-center/sent-applications`, h), { 'nc sent 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.post(`${BASE_URL}/api/v1/notification-center/sent-applications/read`, null, h), { 'nc sent read 200/204': (r) => r.status === 200 || r.status === 204 }) || errorRate.add(1);
  });

  sleep(Math.random() * 1.5);
}

// Exercises the token lifecycle in isolation: register -> refresh -> logout, then a fresh
// registration -> withdraw. Kept separate from `journey` so it doesn't invalidate a session
// mid-scenario for the main VUs.
export function lifecycle() {
  const session = registerVU('life');
  if (!session) {
    sleep(1);
    return;
  }

  const refreshRes = http.post(`${BASE_URL}/api/v1/auth/refresh`, null, {
    headers: { Authorization: `Bearer ${session.refreshToken}` },
  });
  check(refreshRes, { 'refresh 200': (r) => r.status === 200 }) || errorRate.add(1);
  const refreshed = refreshRes.status === 200 ? refreshRes.json() : session;

  const logoutRes = http.post(`${BASE_URL}/api/v1/auth/logout`, null, {
    headers: { Authorization: `Bearer ${refreshed.refreshToken}` },
  });
  check(logoutRes, { 'logout 204': (r) => r.status === 204 }) || errorRate.add(1);

  sleep(0.5);

  // Separate throwaway session to test withdrawal (soft delete) without affecting the one above.
  const churnSession = registerVU('churn');
  if (churnSession) {
    const withdrawRes = http.del(`${BASE_URL}/api/v1/users/me`, null, authHeaders(churnSession));
    check(withdrawRes, { 'withdraw 200/204': (r) => r.status === 200 || r.status === 204 }) || errorRate.add(1);
  }

  sleep(1);
}
