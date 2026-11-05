// Chat room list: each VU is a member of 30 rooms with 20 unread messages per room.
import http from 'k6/http';
import { check } from 'k6';
const data = JSON.parse(open('./tokens.json'));
const BASE = __ENV.BASE;
export const options = {
  scenarios: { rooms: { executor: 'constant-vus', vus: Number(__ENV.VUS || 20), duration: __ENV.DUR || '60s' } },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};
export default function () {
  const token = data.hubs[(__VU - 1) % data.hubs.length];
  const res = http.get(`${BASE}/api/v1/chat/rooms`, { headers: { Authorization: `Bearer ${token}` } });
  check(res, { '200': (r) => r.status === 200 });
}
