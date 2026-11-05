// Post detail with logged-in viewers. VUs exceed the Hikari pool (50) on purpose.
import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
const data = JSON.parse(open('./tokens.json'));
const BASE = __ENV.BASE;
const firstViews = new Counter('first_views');   // (viewer, post) pairs requested for the first time
const slow = new Counter('slow_over_4s');
export const options = {
  scenarios: { detail: { executor: 'constant-vus', vus: Number(__ENV.VUS || 100), duration: __ENV.DUR || '60s' } },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};
export default function () {
  const token = data.viewers[(__VU - 1) % data.viewers.length];
  const post = data.posts[__ITER % data.posts.length];
  const res = http.get(`${BASE}/api/v1/posts/${post}`, { headers: { Authorization: `Bearer ${token}` } });
  check(res, { '200': (r) => r.status === 200 });
  if (res.status === 200 && __ITER < data.posts.length) firstViews.add(1);
  if (res.timings.duration > 4000) slow.add(1);
}
