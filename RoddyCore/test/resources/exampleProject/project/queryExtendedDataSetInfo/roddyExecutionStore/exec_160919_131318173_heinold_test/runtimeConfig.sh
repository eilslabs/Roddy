#!/bin/bash


if [ -z "${PS1-}" ]; then
	 echo non interactive process!
else
	 echo interactive process


fi

declare -x    analysisMethodNameOnInput=testAnalysis
declare -x    analysisMethodNameOnOutput=testAnalysis
declare -x    testAOutputDirectory=testfiles
declare -x    testOutputDirectory=/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/testfiles
declare -x    testInnerOutputDirectory=/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/testfiles/testfilesw2
declare -x    jobnamePrefix=defaultRoddyJobname
declare -x    inputBaseDirectory=/data/michael/temp/exampleProject/project
declare -x    outputBaseDirectory=/data/michael/temp/exampleProject/project
declare -x    DEBUG=TRUE
declare -x    UNZIPTOOL=gunzip
declare -x    UNZIPTOOL_OPTIONS="-c"
declare -x    ZIPTOOL=gzip
declare -x    ZIPTOOL_OPTIONS="-c"
declare -x    useAcceleratedHardware=true
declare -x    outputUMask=007
declare -x    outputFileGroup=UNDEFINED
declare -x    outputAccessRights=u+rw,g+rw,o-rwx
declare -x    outputAllowAccessRightsModification=true
declare -x    outputAnalysisBaseDirectory=/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo
declare -x    processOptionsSetUserGroup=false
declare -x    processOptionsSetUserMask=false
declare -x    processOptionsQueryID=false
declare -x    XMLValidation=true
declare -x    ForbidSubmissionOnRunning=true
declare -x    BreakSubmissionOnError=true
declare -x    RollbackOnSubmissionOnError=false
declare -x    ModifiedVariablePassing=true
declare -x    UseOldDataSetIDExtraction=true
declare -x    AutoFilenames=false
declare -x    UnzipZippedPlugins=false
declare -x    DIR_LOCKFILES=/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/roddyExecutionStore/exec_160919_131318173_heinold_test/temp/lockfiles
declare -x    DIR_TEMP=/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/roddyExecutionStore/exec_160919_131318173_heinold_test/temp
declare -x    DIR_EXECUTION=/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/roddyExecutionStore/exec_160919_131318173_heinold_test
declare -x    DIR_EXECUTION_COMMON=/data/michael/temp/exampleProject/project/.roddyExecutionDirectory
declare -x    DIR_RODDY=/data/michael/Projekte/Roddy
declare -x    DIR_BUNDLED_FILES=/data/michael/Projekte/Roddy/bundledFiles
declare -x    DIR_ANALYSIS_TOOLS=/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/roddyExecutionStore/exec_160919_131318173_heinold_test/analysisTools
declare -x    jobStateLogFile=/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/roddyExecutionStore/exec_160919_131318173_heinold_test/jobStateLogfile.txt
TOOL_CLEANUP_SCRIPT="/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/roddyExecutionStore/exec_160919_131318173_heinold_test/analysisTools/roddyTests/cleanupScript.sh"
TOOL_TEST_SCRIPT="/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/roddyExecutionStore/exec_160919_131318173_heinold_test/analysisTools/roddyTests/testScriptSleep.sh"
TOOL_TEST_SCRIPT_EXIT_BAD="/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/roddyExecutionStore/exec_160919_131318173_heinold_test/analysisTools/roddyTests/testScriptSleepExitBad.sh"
TOOL_TEST_FILE_WITH_CHILDREN="/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/roddyExecutionStore/exec_160919_131318173_heinold_test/analysisTools/roddyTests/testScriptSleep.sh"
TOOL_COMPRESSION_DETECTION="/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/roddyExecutionStore/exec_160919_131318173_heinold_test/analysisTools/roddyTools/determineFileCompressor.sh"
TOOL_CREATE_LOCK_FILES="/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/roddyExecutionStore/exec_160919_131318173_heinold_test/analysisTools/roddyTools/createLockFiles.sh"
TOOL_STREAM_BUFFER="/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/roddyExecutionStore/exec_160919_131318173_heinold_test/analysisTools/roddyTools/streamBuffer.sh"
TOOL_WRAPIN_SCRIPT="/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/roddyExecutionStore/exec_160919_131318173_heinold_test/analysisTools/roddyTools/wrapInScript.sh"
TOOL_NATIVE_WORKFLOW_SCRIPT_WRAPPER="/data/michael/temp/exampleProject/project/queryExtendedDataSetInfo/roddyExecutionStore/exec_160919_131318173_heinold_test/analysisTools/roddyTools/nativeWorkflowScriptWrapper.sh"



set -o pipefail
set -v
set -x
[[ ! ${SET_PATH-} == "" ]] && export PATH=${SET_PATH}
