#!/bin/bash

set -ex
topdir=$(realpath $(dirname $0)/..)

pushd $topdir
./gradlew --write-verification-metadata sha256 --refresh-dependencies \
          :lib:extractDebugAnnotations \
          :lib:compileDebugLibraryResources \
          assembleDebug \
          assembleRelease \
          build \
          connectedDebugAndroidTest \
          check \
          mergeDebugResources \
          :passwdsafe:processDebugResources \
          :sync:processDebugResources \
          test \
          help \
          --dry-run
mv gradle/verification-metadata.dryrun.xml gradle/verification-metadata.xml

./gradlew --write-verification-metadata sha256 --refresh-dependencies \
          build \
          mergeDebugResources \
