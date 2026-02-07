#!/usr/bin/env bash
set -e
pushd ..
  version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
popd

./gradlew clean build publishToMavenLocal -Ppublish.version=$version

pushd src/test/manual-test
  ./gradlew clean build
popd

./gradlew clean build publishPlugins --validate-only -Ppublish.version=$version
./gradlew publishPlugins -Ppublish.version=$version