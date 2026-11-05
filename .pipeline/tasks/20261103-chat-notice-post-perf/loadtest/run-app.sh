#!/bin/bash
# usage: run-app.sh <before|after> <port> <bootJar path> <firebase json path>
# Runs one app version against the throwaway MySQL on 127.0.0.1:3307 (database lt_<version>).
# Needs JAVA_HOME and a jwt.key file (random base64, 48 bytes) next to this script.
DIR=$(cd "$(dirname "$0")" && pwd)
V=$1; PORT=$2; JAR=$3; FIREBASE=$4
export SPRING_PROFILES_ACTIVE=db,secrets,release
export SPRING_DATASOURCE_URL="jdbc:mysql://127.0.0.1:3307/lt_${V}?characterEncoding=UTF-8&serverTimezone=Asia/Seoul&createDatabaseIfNotExist=true"
export SPRING_DATASOURCE_USERNAME=root SPRING_DATASOURCE_PASSWORD=lt
export GOOGLE_APPLICATION_CREDENTIALS="$FIREBASE"
export WEBSOCKET_ALLOWED_ORIGIN_PATTERN="http://localhost"
unset DISCORD_WEBHOOK_URL
# SerialGC and a small heap mirror the production container (mem_limit 1536m).
exec "$JAVA_HOME/bin/java" -Xmx1g -XX:+UseSerialGC -jar "$JAR" \
  --server.port="$PORT" --jwt.secret-key="$(cat "$DIR/jwt.key")"
