import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate } from 'k6/metrics';
import encoding from 'k6/encoding';

// Full READ-ONLY sweep of the real production instance (meetjyou.duckdns.org).
// Covers every GET endpoint reachable by a normal authenticated user, plus the permitAll
// public GETs — replacing prod-posts-read.js (which only covered GET /posts) as the "full API"
// read-only baseline for production.
//
// Auth: uses the synthetic load-test account via POST /internal/load-test-token
// (LoadTestTokenController — gated by X-Load-Test-Secret, must be enabled + configured in
// production secrets). This avoids needing a real Kakao/Google OAuth login in the release
// profile. The synthetic account has no pre-existing posts/plans/parties/chat rooms unless a
// prior run created them, so UUID-scoped endpoints are exercised opportunistically (UUIDs
// discovered from list responses) and checks accept 403/404 as legitimate non-error outcomes,
// not just 200.
//
// Deliberately EXCLUDED (this is a read-only pass by explicit decision, not full coverage):
// - Every write endpoint (POST/PUT/PATCH/DELETE) — avoids polluting production data or
//   triggering real push notifications / OCI writes.
// - All OracleObjectStorageController PAR endpoints and GET /terms/{uuid}/content-url — these
//   generate real OCI PAR URLs even on a GET, so out of scope (same exclusion as the local
//   full-api-scenario.js).
// - Every ADMIN-only GET: GET /users, GET /parties, GET /parties/user/{uuid}, GET
//   /parties/plan/{uuid}, GET /parties/{uuid} (admin single lookup), GET /plans/user/{uuid},
//   GET /{platform}/versions (list-all). Reaching these would require promoting the synthetic
//   account to ADMIN in production — a real, hard-to-reverse privilege change on a live
//   environment — so it is not done here.
// - Chat message send (STOMP, not REST — see .claude/rules/chat.md).
//
// Run: k6 run -e SECRET=<LOAD_TEST_TOKEN_SECRET> loadtest/prod-full-read-scenario.js
// Override target: k6 run -e BASE_URL=... -e SECRET=... loadtest/prod-full-read-scenario.js

const BASE_URL = __ENV.BASE_URL || 'https://meetjyou.duckdns.org';
const SECRET = __ENV.SECRET;

const errorRate = new Rate('errors');

export const options = {
  scenarios: {
    read_sweep: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '20s', target: 10 },
        { duration: '40s', target: 10 },
        { duration: '20s', target: 30 },
        { duration: '40s', target: 30 },
        { duration: '20s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<800'],
    errors: ['rate<0.05'],
  },
};

// LoadTestTokenResponse only carries {accessToken, expiresAt} — no uuid field. The synthetic
// account's uuid lives in the JWT's own "userUuid" claim (JwtProvider.generateAccessToken),
// not the subject (which is the email), so it has to be decoded out of the token itself.
function decodeJwtUuid(token) {
  const payload = token.split('.')[1];
  const claims = JSON.parse(encoding.b64decode(payload, 'rawurl', 's'));
  return claims.userUuid;
}

// Fetch the synthetic-account token once in setup() and share it across all VUs — the token
// endpoint issues a fixed-identity JWT and there is no reason for every VU to mint its own.
export function setup() {
  const res = http.post(`${BASE_URL}/api/v1/internal/load-test-token`, null, {
    headers: { 'X-Load-Test-Secret': SECRET },
  });
  if (res.status !== 200) {
    throw new Error(`Failed to obtain load-test token: ${res.status} ${res.body}`);
  }
  const body = res.json();
  const uuid = decodeJwtUuid(body.accessToken);
  if (!uuid) {
    throw new Error('Failed to decode userUuid claim from load-test token — aborting to avoid null-path requests.');
  }
  return { token: body.accessToken, uuid };
}

export default function (data) {
  const h = { headers: { Authorization: `Bearer ${data.token}` } };

  group('public (no auth)', () => {
    const notices = http.get(`${BASE_URL}/api/v1/notices?page=0&size=20`);
    check(notices, { 'list notices 200': (r) => r.status === 200 }) || errorRate.add(1);
    const noticeList = notices.status === 200 ? notices.json('content') || [] : [];
    if (noticeList.length > 0) {
      check(http.get(`${BASE_URL}/api/v1/notices/${noticeList[0].uuid}`), { 'notice detail 200': (r) => r.status === 200 }) ||
        errorRate.add(1);
    }

    check(http.get(`${BASE_URL}/api/v1/terms/active`), { 'active terms 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/android/versions/check?clientVersion=1.0.0`), {
      'version check 200/400': (r) => r.status === 200 || r.status === 400,
    }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/android/versions/latest`), { 'latest version 200/404': (r) => r.status === 200 || r.status === 404 }) ||
      errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/users/is-duplicate-nickname?nickname=k6probe${__VU}${__ITER}`), {
      'nickname dup check 200': (r) => r.status === 200,
    }) || errorRate.add(1);
  });

  sleep(Math.random() * 0.5);

  group('user profile (auth)', () => {
    check(http.get(`${BASE_URL}/api/v1/users/me/profile`, h), { 'my profile 200/404': (r) => r.status === 200 || r.status === 404 }) ||
      errorRate.add(1);
    // 404 PREFERENCE_NOT_FOUND is expected here: the synthetic account never goes through
    // PUT /users/me, so it has no gender/age/etc. preference rows.
    check(http.get(`${BASE_URL}/api/v1/users/${data.uuid}/basic-info`, h), { 'basic-info 200/404': (r) => r.status === 200 || r.status === 404 }) ||
      errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/users/${data.uuid}/advanced-info`, h), { 'advanced-info 200/404': (r) => r.status === 200 || r.status === 404 }) ||
      errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/users/me/plans`, h), { 'my plans 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/users/me/parties`, h), { 'my parties 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/users/me/applications`, h), { 'my applications 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/users/me/posts`, h), { 'my posts 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/users/me/notification-settings`, h), { 'notification settings 200': (r) => r.status === 200 }) ||
      errorRate.add(1);
  });

  sleep(Math.random() * 0.5);

  let samplePost = null;
  group('posts (auth)', () => {
    const list = http.get(`${BASE_URL}/api/v1/posts?page=0&size=20`, h);
    check(list, { 'list posts 200': (r) => r.status === 200 }) || errorRate.add(1);
    const content = list.status === 200 ? list.json('content') || [] : [];
    if (content.length > 0) samplePost = content[0];

    check(http.get(`${BASE_URL}/api/v1/posts/author/${data.uuid}`, h), { 'posts by author 200': (r) => r.status === 200 }) ||
      errorRate.add(1);

    if (samplePost) {
      check(http.get(`${BASE_URL}/api/v1/posts/${samplePost.uuid}`, h), { 'post detail 200': (r) => r.status === 200 }) || errorRate.add(1);
    }
  });

  sleep(Math.random() * 0.5);

  group('plan (auth, best-effort)', () => {
    // Only exercised when a listed post happens to carry a public planUuid — the synthetic
    // account owns no plans of its own, and there is no plan-listing endpoint to discover one.
    if (samplePost && samplePost.planUuid) {
      check(http.get(`${BASE_URL}/api/v1/plans/${samplePost.planUuid}`, h), { 'plan detail 200/403/404': (r) => [200, 403, 404].includes(r.status) }) ||
        errorRate.add(1);
      check(http.get(`${BASE_URL}/api/v1/plans/${samplePost.planUuid}/markers`, h), {
        'plan markers 200/403/404': (r) => [200, 403, 404].includes(r.status),
      }) || errorRate.add(1);
    }
  });

  sleep(Math.random() * 0.5);

  group('party (auth, best-effort)', () => {
    if (samplePost && samplePost.partyUuid) {
      check(http.get(`${BASE_URL}/api/v1/parties/${samplePost.partyUuid}/members`, h), {
        'party members 200/403/404': (r) => [200, 403, 404].includes(r.status),
      }) || errorRate.add(1);
      check(http.get(`${BASE_URL}/api/v1/parties/${samplePost.partyUuid}/join-requests`, h), {
        'pending join-requests 200/403/404': (r) => [200, 403, 404].includes(r.status),
      }) || errorRate.add(1);
    }
  });

  sleep(Math.random() * 0.5);

  group('chat (auth)', () => {
    const rooms = http.get(`${BASE_URL}/api/v1/chat/rooms`, h);
    check(rooms, { 'list chat rooms 200': (r) => r.status === 200 }) || errorRate.add(1);
    const roomList = rooms.status === 200 ? rooms.json('rooms') || [] : [];
    if (Array.isArray(roomList) && roomList.length > 0 && roomList[0].roomUuid) {
      const roomUuid = roomList[0].roomUuid;
      check(http.get(`${BASE_URL}/api/v1/chat/rooms/${roomUuid}/messages`, h), { 'chat messages 200': (r) => r.status === 200 }) ||
        errorRate.add(1);
      check(http.get(`${BASE_URL}/api/v1/chat/rooms/${roomUuid}/unread-count`, h), { 'unread count 200': (r) => r.status === 200 }) ||
        errorRate.add(1);
    }
  });

  sleep(Math.random() * 0.5);

  group('notification center (auth)', () => {
    check(http.get(`${BASE_URL}/api/v1/notification-center/notices`, h), { 'nc notices 200': (r) => r.status === 200 }) || errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/notification-center/received-applications`, h), { 'nc received 200': (r) => r.status === 200 }) ||
      errorRate.add(1);
    check(http.get(`${BASE_URL}/api/v1/notification-center/sent-applications`, h), { 'nc sent 200': (r) => r.status === 200 }) ||
      errorRate.add(1);
  });

  sleep(1 + Math.random());
}
