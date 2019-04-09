#!/usr/bin/env bash

set -e

export TERM=${TERM:-dumb}
cd source
./gradlew --no-daemon integrationTest