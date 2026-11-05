#!/bin/bash
# usage: measure.sh <before|after> <port>  — runs every scenario against one running app
set -u
V=$1; PORT=$2; J=$(cd "$(dirname "$0")" && pwd); BASE=http://127.0.0.1:$PORT
OUT=$J/results/$V; mkdir -p $OUT; cd $J
sql() { docker exec meetjyou-lt mysql -uroot -plt lt_$V -N -e "$1" 2>/dev/null; }
reset_digest() { sql "TRUNCATE performance_schema.events_statements_summary_by_digest"; }
count_stmts() { sql "SELECT COALESCE(SUM(COUNT_STAR),0) FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME='lt_$V' AND DIGEST_TEXT NOT LIKE 'SELECT @@%' AND DIGEST_TEXT NOT LIKE '%performance_schema%'"; }
H=$(jq -r '.hubs[0]' tokens.json); A=$(jq -r '.admin' tokens.json)

echo "== warmup"; k6 run -q -e BASE=$BASE -e VUS=10 -e DUR=20s chat-rooms.js > /dev/null 2>&1

echo "== chat rooms: statements per request (20 sequential)"
reset_digest; for i in $(seq 20); do curl -s -o /dev/null -H "Authorization: Bearer $H" $BASE/api/v1/chat/rooms; done
echo "stmts_per_rooms_request=$(( $(count_stmts) / 20 ))" | tee $OUT/rooms-stmts.txt
echo "== chat rooms load (20 VUs, 60s)"
k6 run -e BASE=$BASE -e VUS=20 -e DUR=60s --summary-export $OUT/rooms.json chat-rooms.js > $OUT/rooms.txt 2>&1

echo "== post detail load (100 VUs, 60s)"
before_views=$(sql "SELECT SUM(views) FROM post_view_counts")
k6 run -e BASE=$BASE -e VUS=100 -e DUR=60s --summary-export $OUT/post.json post-detail.js > $OUT/post.txt 2>&1
sleep 2; after_views=$(sql "SELECT SUM(views) FROM post_view_counts")
echo "views_added=$(( after_views - before_views ))" | tee $OUT/post-views.txt

echo "== notice broadcast (3 runs)"
for r in 1 2 3; do
  reset_digest; before_rows=$(sql "SELECT COUNT(*) FROM notification_outbox")
  t=$(curl -s -o /dev/null -w "%{http_code} %{time_total}" -X POST -H "Authorization: Bearer $A" -H 'Content-Type: application/json' \
      -d "{\"title\":\"lt notice $r\",\"body\":\"body\",\"notify\":true,\"critical\":false}" $BASE/api/v1/notices)
  sleep 1; rows=$(( $(sql "SELECT COUNT(*) FROM notification_outbox") - before_rows ))
  echo "run=$r http_time=$t outbox_rows=$rows stmts=$(count_stmts)" | tee -a $OUT/broadcast.txt
done
