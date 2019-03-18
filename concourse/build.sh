#!/usr/bin/env bash

set -e

export TERM=${TERM:-dumb}
cd source
./gradlew --no-daemon build

cp build/build.properties ../gradle-props
