#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mvn -q -DskipTests package
exec java -jar target/matchmaking-1.0.jar "$@"
