#!/usr/bin/env bash
set -e
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
# echo "Version: $VERSION"
if [[ ! -f target/gmd-$VERSION.jar ]]; then
  mvn package
fi
java -jar target/gmd-$VERSION.jar toPdf src/test/resources/test.gmd target/cmdLineExample.pdf