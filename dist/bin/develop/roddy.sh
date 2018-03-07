#!/bin/bash

set -o pipefail

cd `dirname $0`
parm1=${1-}

RODDY_JAVA_OPTS=${RODDY_JAVA_OPTS:-${JAVA_OPTS:--Xms64m -Xmx1g}}

# Call some scripts before other steps start.
if [[ "$parm1" == "prepareprojectconfig" ]]; then
    source ${SCRIPTS_DIR}/prepareProjectConfiguration.sh
    exit 0
fi

PATH="${JDK_HOME:?Please set JDK_HOME}/bin:${JAVA_HOME:?Please set JAVA_HOME}/bin:${GROOVY_HOME:?Please set GROOVY_HOME}/bin:$PATH"

if [[ -x "$RODDY_BINARY_DIR/lib" ]]; then
    libraries=`ls -d1 ${RODDY_BINARY_DIR}/lib/** 2> /dev/null | tr "\\n" ":"`; libraries=${libraries:0:`expr ${#libraries} - 1`}
else
    libraries=""
fi

#Is the roddy binary or anything set via command line?
for i in $*
do
    if [[ $i == --usePluginVersion* ]]; then
        overridePluginParameters=${i:19:140}
    fi
done

if [[ "$parm1" == "compile" ]]; then
    [[ ! -d $JDK_HOME ]] && echo "There was no JDK home found. Roddy cannot compile workflows." && exit 1
    source ${SCRIPTS_DIR}/compileRoddyBinary.sh
    exit 0
elif [[ "$parm1" == "install" ]]; then
    export tagID=$2
    git tag | grep $tagID
    [[ $? -ne 0 ]] && echo "Tag id ${tagID} could not be found. Please make sure, that the Roddy version is available in your local repository." && exit 1

    export tempDir=`mktemp -d -t RODDY_INSTALL.XXXXXXXXX`
    export myDir=`readlink -f $RODDY_BINARY_DIR/../`
    [[ -d $myDir/$tagID ]] && echo "The target release directory already exists." && exit 1

    # Check the Tag and abort if necessary
    ( git clone -l -b $tagID . $tempDir; \
      cd $tempDir; \
      ./roddy.sh compile; \
      cp -r dist/bin/develop $myDir/$tagID )
    rm -rf $tempDir
    exit 0
elif [[ "$parm1" == "compileplugin" ]]; then
    echo "Using Roddy binary "`basename ${RODDY_BINARY}`
    echo "  Roddy version: "$(basename $(dirname ${RODDY_BINARY}))
    [[ ! -f ${RODDY_BINARY} ]] && echo "The requested Roddy version does not exist or was not build. Please check Roddy version or run './roddy.sh compile'." && exit 1
    [[ ! -d $JDK_HOME ]] && echo "There was no JDK home found. Roddy cannot compile workflows." && exit 1
    source ${SCRIPTS_DIR}/compileRoddyPlugin.sh
    exit 0
elif [[ "$parm1" == "packplugin" || "$parm1" == "testpackplugin" ]]; then
    [[ "$parm1" == "testpackplugin" ]] && set -xuv
    increasebuildonly=true
    source ${SCRIPTS_DIR}/compileRoddyPlugin.sh

    # Test pack does not put things to svn so it is safe to use. Test will not change the zip file but will increase the buildnumber.
    pluginID=$2
    pluginDirectories=`grepFromConfigFile pluginDirectories $customconfigfile`
    pluginDirectory=`$GROOVY_BINARY ${SCRIPTS_DIR}/findPluginFolders.groovy ${pluginDirectories} ${RODDY_DIRECTORY} ${pluginID}`
    for i in `ls ${pluginDirectory}/README*.txt 2> /dev/null`; do
        $GROOVY_BINARY ${SCRIPTS_DIR}/addChangelistVersionTag.groovy $i ${pluginDirectory}/buildversion.txt
    done

    major=`head ${pluginDirectory}/buildversion.txt -n 1`
    minor=`tail ${pluginDirectory}/buildversion.txt -n 1`
    filename=${pluginID}_${major}.${minor}
    cd ${pluginDirectory}/..
    echo "Copying 'develop' to ${filename} ..."
    [[ ! -d ${filename} ]] && mkdir ${filename}
    cp -r $pluginID/* ${filename}
    cd $filename
#    set -xuv
    set +e
    echo "Removing obsolete files"
    # Find .svn folders?
    find -type d -name "*.svn" | xargs rm -rf
    rm -rf build
    rm -rf out
    rm README*.txt~ 2> /dev/null

    echo "Creating list of included files ..."
    (find -maxdepth 1 -type f | sort | xargs md5sum) > FileList.nfo
    (for i in `find -mindepth 1 -maxdepth 1 -type d | sort | grep -v "build"`; do find $i -type f | sort | xargs md5sum ; done) >> FileList.nfo

    # Step back to main dir
    cd ..

    echo "Compressing to ${filename}.zip ..."
    [[ -f {filename}.zip ]] && rm ${filename}.zip
    infoFile=${filename}/${pluginID}.nfo
    svn info > ${infoFile}
    svn status >> ${infoFile}
    find ${RODDY_DIRECTORY}/dist >> ${infoFile}
    ls -l ${RODDY_DIRECTORY}/dist >> ${infoFile}

    if [[ "$parm1" == "packplugin" ]]; then
        zip -x "*/build/*" -r9 ${filename}.zip ${filename} > /dev/null
        echo "Adding to SVN"
#        svn add ${filename}.zip ${filename}.nfo 2> /dev/null
    fi

    cd ..; echo "Done";
    exit 0
    # Only unzip if necessary!
elif [[ "$parm1" == "createworkflow" ]]; then
    pluginID=$2
    workflowID=$3
    source ${SCRIPTS_DIR}/createNewWorkflow.sh ${customconfigfile} ${pluginID} ${3-}
    exit 0
fi

export RODDY_HELPERSCRIPTS_FOLDER=`readlink -f dist/bin/develop/helperScripts`

source ${RODDY_HELPERSCRIPTS_FOLDER}/networkFunctions.sh

debuggerSettings=""
[[ -n ${DEBUG_RODDY} ]] && debuggerSettings=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005

caller=$(checkAndDownloadGroovyServ "${RODDY_DIRECTORY}")

if [[ ${caller} == java ]]; then

  echo "Using Java to start Roddy" >> /dev/stderr
  ${caller} ${debuggerSettings} $RODDY_JAVA_OPTS -enableassertions -cp .:$libraries:${RODDY_BINARY} de.dkfz.roddy.Roddy "$@"

elif [[ $(basename ${caller}) == groovyclient && -f ${caller} && -x ${caller} ]]; then

  echo "Using GroovyServ to start Roddy"
  # Get the port of an existing instance of GroovyServ or start a new instance with a free port.
  portForGroovyServ=$(cd ${RODDY_HELPERSCRIPTS_FOLDER}; getExistingOrNewGroovyServPort)

  [[ -z ${portForGroovyServ-} ]] && echo "Could not get a free port for GroovyServ. GroovyServ will be disabled. Delete the file dist/runtime/gservforbidden to reenable it. Please restart Roddy." && exit 5

  # JAVA_OPTS are automatically used by groovyserver (see the .go files in the sources)
  ${caller} -Cenv-all ${debuggerSettings} -cp .:$libraries:${RODDY_BINARY} GServCaller.groovy "$@"

else
  echo "Cannot start Roddy, neither Java nor GroovyServ was recognized" && exit 5
fi

IFS=$OFS
