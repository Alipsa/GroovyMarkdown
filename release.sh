#!/usr/bin/env bash

echo "publishing all from parent + all sub projects"
mvn -Prelease deploy
echo "To check central publishing visit https://central.sonatype.com/publishing/deployments"
echo "To check gradle plugin portal publishing visit https://plugins.gradle.org/plugin/se.alipsa.gmd.gmd-gradle-plugin"