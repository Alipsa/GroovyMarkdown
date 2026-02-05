#!/usr/bin/env bash
set -e
if command -v jdk21; then
  . jdk21
fi
mvn clean install

echo "Done"