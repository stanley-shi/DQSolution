#!/bin/bash

ROOT_DIR=$(cd $(dirname $0); pwd)

SLEEP_INTERVAL=${SLEEP_INTERVAL:-60}

if [ ! -f $ROOT_DIR/bark_jobs.sh ]; then
  echo "bark_jobs.sh not found!"
  exit 1
fi

set +e
while true
do
  $ROOT_DIR/bark_jobs.sh 2>&1
  sleep $SLEEP_INTERVAL
done
set -e
