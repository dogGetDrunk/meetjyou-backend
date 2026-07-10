import http from 'k6/http';
import { check, sleep } from 'k6';

// Focused abuse test for AuthRateLimitFilter (per-IP rate limit on auth endpoints).
// Goal: confirm the 5-6th request in a 1-minute window gets HTTP 429 as expected,
// then confirm whether spoofing X-Forwarded-For resets/bypasses the limit
// (server.forward-headers-strategy: native trusts XFF for remoteAddr).
//
// Run baseline (no spoofing):
//   k6 run loadtest/auth-abuse.js
// Run with spoofing enabled to test bypass:
//   k6 run -e SPOOF_XFF=true loadtest/auth-abuse.js

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SPOOF_XFF = __ENV.SPOOF_XFF === 'true';

export const options = {
  scenarios: {
    single_client_burst: {
      executor: 'per-vu-iterations',
      vus: 1,
      iterations: 20,
      maxDuration: '1m',
    },
  },
};

export default function () {
  const headers = { 'Content-Type': 'application/json' };
  if (SPOOF_XFF) {
    // A different fake client IP on every request.
    headers['X-Forwarded-For'] = `10.0.${Math.floor(Math.random() * 255)}.${Math.floor(Math.random() * 255)}`;
  }

  const res = http.post(`${BASE_URL}/api/v1/auth/nonce`, null, { headers });

  console.log(`iter=${__ITER} status=${res.status} xff=${headers['X-Forwarded-For'] || 'n/a'}`);

  check(res, {
    'not a server error': (r) => r.status < 500,
  });

  sleep(0.2);
}
