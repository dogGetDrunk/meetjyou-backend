import http from 'k6/http';
import { check, sleep } from 'k6';

// Read-focused benchmark for the real OCI production instance.
// Purpose: measure GET /api/v1/posts latency before/after the N+1 fix, using the
// dedicated synthetic load-test-token account. Kept read-only and modest VU count
// out of respect for OCI free-tier resources (HikariCP pool size 5, shared compute).
//
// Run: k6 run -e SECRET=<LOAD_TEST_TOKEN_SECRET> loadtest/prod-posts-read.js

const BASE_URL = __ENV.BASE_URL || 'https://meetjyou.duckdns.org';
const SECRET = __ENV.SECRET;

export const options = {
  scenarios: {
    ramping_read: {
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
    http_req_failed: ['rate<0.05'],
  },
};

// Fetch the token once in setup() and share it across all VUs — the token endpoint
// is intentionally rate-limited (5/min), so per-VU fetching would exhaust it immediately.
export function setup() {
  const res = http.post(`${BASE_URL}/api/v1/internal/load-test-token`, null, {
    headers: { 'X-Load-Test-Secret': SECRET },
  });
  if (res.status !== 200) {
    throw new Error(`Failed to obtain load-test token: ${res.status} ${res.body}`);
  }
  return { token: res.json('accessToken') };
}

export default function (data) {
  const authHeaders = { headers: { Authorization: `Bearer ${data.token}` } };

  const res = http.get(`${BASE_URL}/api/v1/posts?page=0&size=10`, authHeaders);
  check(res, { 'list posts 200': (r) => r.status === 200 });

  sleep(1 + Math.random());
}
