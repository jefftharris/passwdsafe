#!/bin/bash

set -ex
topdir=$(realpath $(dirname $0)/..)

pushd $topdir
./gradlew --write-verification-metadata sha256 \
          assembleDebug \
          assembleRelease \
          build \
          connectedDebugAndroidTest \
          check \
          mergeDebugResources \
          test \
          help \
          --dry-run
mv gradle/verification-metadata.dryrun.xml gradle/verification-metadata.xml

./gradlew --write-verification-metadata sha256 \
          mergeDebugResources \
          :lib:extractDebugAnnotations \
          :lib:compileDebugLibraryResources
