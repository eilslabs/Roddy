#!/bin/bash

set -e

# Return 1, if the plugin name is allowed. Otherwise 0.
validateVersionedPluginName() {
    echo "${1:?No plugin name given}" | perl -ne 'print scalar(/^\w+(?:[_:]\d+\.\d+\.\d+(?:-\d+)?)?$/) ? "valid\n" : "invalid\n"'
}

# Take string, check that it follows the pattern allowed for versioned plugins (with '_' or ':' between plugin name and version and
# return the plugin name.
getPluginName() {
    local pluginName="${1:?No plugin name given}"
    if [[ $(validateVersionedPluginName "$pluginName") == "invalid" ]]; then
        echo "'$pluginName' is not a valid plugin name" >> /dev/stderr
        exit 1
    else
        echo "$pluginName" | sed -r 's/:/_/' | sed -r 's/_.+//'
    fi
}

libName=$(getPluginName "${2:?No plugin name given}")


increasebuildonly=${increasebuildonly-false}

# Check for configured plugin directories. Set empty if no dirs are configured.
pluginDirectories=`grepFromConfigFile pluginDirectories $customconfigfile`
pluginDirectories="pluginDirectories=$pluginDirectories"

#TODO Find other plugin directories as well
pluginID=$2
srcDirectory=`groovy ${SCRIPTS_DIR}/findPluginFolders.groovy ${pluginDirectories} ${RODDY_DIRECTORY} ${pluginID}`
[[ "$srcDirectory" == null ]] && echo "Compilation aborted: source directory for plugin not found. Is your app ini correct? pluginDirectories=$pluginDirectories" && exit 1
echo $srcDirectory
cd $srcDirectory
requestedAPIVersion=`grep RoddyAPIVersion buildinfo.txt | cut -d "=" -f 2`
[[ ! $RODDY_API == $requestedAPIVersion ]] && echo "Mismatch between used Roddy version ${RODDY_API} and requested version ${requestedAPIVersion}. Will not compile plugin." && exit 1
#cat buildinfo.txt

echo "Increasing build number and date"
pluginClass=`find $srcDirectory/ -name "*Plugin.java" | head -n 1`
groovy ${SCRIPTS_DIR}/IncreaseAndSetBuildVersion.groovy $srcDirectory/buildversion.txt $pluginClass ${INCREASE_BUILD_VERSION:-true}
echo "  Increased to" `head -n 1 $srcDirectory/buildversion.txt`.`tail -n 1 $srcDirectory/buildversion.txt`

if [[ $increasebuildonly == false ]]
then
    echo "Switching to plugin directory for plugin '$versionedPluginName'"

    echo "Creating necessary paths"
    rm -rf build
    mkdir -p build/classes

    echo "Searching source files"

    test=`find src/ -type f \( -name "*.groovy" -or -name "*.java"  \)`

    echo "Searching libraries"

    roddyLibrary=${RODDY_BINARY}
    jfxLibrary=`find $JDK_HOME/ -name "jfxrt.jar"`
    libraries=`ls -d1 ${RODDY_BINARY_DIR}/lib/** | tr "\\n" ":"`; libraries=${libraries:0:`expr ${#libraries} - 1`}
    libraries=$roddyLibrary:$libraries:$jfxLibrary

    # Check if there is a buildinfo.txt and resolve additional library dependencies.
    if [[ -f buildinfo.txt ]]
    then
        [[ $? == 0 ]] && pluginLibs=`groovy ${SCRIPTS_DIR}/findPluginLibraries.groovy ${pluginDirectories} ${RODDY_DIRECTORY} ${PWD}`
        [[ $? == 0 && $pluginLibs != "null" ]] && libraries=$libraries:$pluginLibs
        echo "Used libraries: "
        ofs=$IFS
        IFS=":"
        for lib in $pluginLibs; do
          echo "  ${lib}"
        done
        IFS=$ofs
    fi

    echo "Working in:" $PWD
    echo "Using jfx lib:" $jfxLibrary
    librariesForManifest=`ls -d1 ${RODDY_BINARY_DIR}/lib/*groo*`
    librariesForManifest="$librariesForManifest $jfxLibrary ${RODDY_BINARY}"

    echo "Compiling library / plugin in $1"
    javac -version
    groovyc -version
    uncheckedStr=""

    groovyc $uncheckedStr -d build/classes -cp $libraries -j $test
    [ "$?" -ne 0 ] && "Error during compilation" && exit $?

    echo "Creating manifest file"
    echo Manifest-Version: 1.0 > manifest.tmp
    echo Created-By: 1.8 >> manifest.tmp
    echo "" >> manifest.tmp

    echo "Searching generated files"
    cd build/classes
    classFiles=`find . -type f \( -name "*.class" \)`

    outputDirectory="../.."

    echo "Compressing files to "`readlink -f $outputDirectory/$libName.jar`
    jar cf $outputDirectory/$libName.jar $classFiles
    cd $outputDirectory
    [[ -d ".svn" ]] && svn add $libName.jar buildversion.txt #2> /dev/null
    [[ -d ".git" ]] && git add $libName.jar buildversion.txt `git stage | grep Plugin.java | cut -d ":" -f 2` #2> /dev/null
    rm manifest.tmp
fi
