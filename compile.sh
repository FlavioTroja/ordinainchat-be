#!/bin/bash
set -a
# Solo righe non commentate e non vuote
grep -v '^#' ../.env | grep -v '^$' > /tmp/.env_no_comments
export $(cat /tmp/.env_no_comments | xargs)
./gradlew clean build --refresh-dependencies --stacktrace
