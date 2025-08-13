#!/bin/bash
set -a
# Solo righe non commentate e non vuote
grep -v '^#' ../.env | grep -v '^$' > /tmp/.env_no_comments
export $(cat /tmp/.env_no_comments | xargs)
flyway \
  -url=jdbc:postgresql://localhost:6481/ordinainchat \
  -user=$POSTGRES_USER \
  -password=$POSTGRES_PASSWORD \
  repair

