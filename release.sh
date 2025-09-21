#!/usr/bin/env bash

echo "publishing all from parent + all sub projects"
mvn -Prelease deploy
echo "To finish publishing visit https://central.sonatype.com/publishing/deployments"