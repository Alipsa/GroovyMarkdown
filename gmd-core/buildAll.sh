#!/usr/bin/env bash
set -e

if [[ -f ~/.sdkman/bin/sdkman-init.sh ]]; then
  source ~/.sdkman/bin/sdkman-init.sh
fi
if command --v jdk21 > /dev/null 2>&1; then
  source jdk21
fi
echo "******************************************************"
echo "* Building gmd-core and publishing it to maven local *"
echo "******************************************************"
#./gradlew clean build publishToMavenLocal
mvn clean install
#echo "***********************"
#echo "* creating the fatJar *"
#echo "***********************"
#./gradlew fatJar
echo "************************************"
echo "* Running the command line example *"
echo "************************************"
./cmdLineExample.sh