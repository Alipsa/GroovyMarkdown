#!/usr/bin/env bash

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