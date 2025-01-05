#!/bin/bash

set -ex
topdir=$(realpath $(dirname $0)/..)

pushd $topdir

gradleArgs="--write-verification-metadata sha256"
gradleArgs="${gradleArgs} --refresh-dependencies"

./gradlew ${gradleArgs} \
          --dry-run \
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
          help
mv gradle/verification-metadata.dryrun.xml gradle/verification-metadata.xml

./gradlew ${gradleArgs} \
          build \
          mergeDebugResources
