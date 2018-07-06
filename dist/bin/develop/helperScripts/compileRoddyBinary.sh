#!/bin/bash

set -e

echo "Increasing build number and date to"
$GROOVY_BINARY ${SCRIPTS_DIR}/IncreaseAndSetBuildVersion.groovy RoddyCore/buildversion.txt RoddyCore/src/de/dkfz/roddy/Constants.java ${INCREASE_BUILD_VERSION:-true}
echo "  "$(head -n 1 RoddyCore/buildversion.txt).$(tail -n 1 RoddyCore/buildversion.txt)

echo "Storing buildinfo for the new Roddy jar file"
echo JDKVersion=$JDK_VERSION > dist/bin/develop/buildinfo.txt
echo GroovyVersion=$GROOVY_VERSION >> dist/bin/develop/buildinfo.txt
echo RoddyAPIVersion=`head RoddyCore/buildversion.txt -n 1` >> dist/bin/develop/buildinfo.txt

declare -a gradleParameters=$*
./gradlew build "${fullParameterList[@]:1:99}"
