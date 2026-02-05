#!/usr/bin/env bash

if command -v jdk21; then
  . jdk21
fi

echo "******************************"
echo "** Maven dependency updates **"
echo "******************************"
mvn versions:display-plugin-updates versions:display-dependency-updates

pushd gmd-gradle-plugin
echo "*******************************"
echo "** Gradle dependency updates **"
echo "*******************************"
./gradlew dependencyUpdates -Drevision=release
popd

echo ""
echo "******************************************"
echo "** Plugin Default Dependency Versions  **"
echo "******************************************"
./checkDefaultDependencyVersions