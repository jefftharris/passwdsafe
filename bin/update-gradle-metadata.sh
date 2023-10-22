#!/bin/bash

set -ex
topdir=$(realpath $(dirname $0)/..)

pushd $topdir
./gradlew --write-verification-metadata sha256 \
          assembleDebug \
          connectedDebugAndroidTest \
          --dry-run

mv gradle/verification-metadata.dryrun.xml gradle/verification-metadata.xml
