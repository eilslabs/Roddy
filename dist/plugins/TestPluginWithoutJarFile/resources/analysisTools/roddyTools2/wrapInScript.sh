#!/bin/bash

# This script wraps in another script.
# The configuration file is sourced and has to be sourced again in the wrapped script.
# A job error entry is created in the results list along with a timestamp
#   i.e. 1237474.tbi-pbs1,START,928130918393
# This status is ignored if the script is currently planned or running
# When the job finished an entry with the job scripts exit code is created with a timestamp
#
# Cluster options (like i.e. PBS ) have to be parsed and set before job submission!
# They will be ignored after the script is wrapped.

# Set the "RODDY_SCRATCH" variable and directory from the predefined "RODDY_SCRATCH" variable or the "defaultScratchDir" variable.
# Die if the resulting directory is not accessible (executable).
setupRoddyScratch() {
  if [[ "${RODDY_SCRATCH:-}" == "" ]]; then
    throw 200 "Undefined RODDY_SCRATCH variable."
  elif [[ ! -x "$RODDY_SCRATCH" ]]; then
    throw 200 "Cannot access RODDY_SCRATCH=$RODDY_SCRATCH"
  else
    if [[ ! -d ${RODDY_SCRATCH} ]]; then
        mkdir -p ${RODDY_SCRATCH}
    fi
    echo "RODDY_SCRATCH is set to ${RODDY_SCRATCH}"
  fi
}

source ${PARAMETER_FILE}

isOutputFileGroup=${outputFileGroup-false}

if [[ $isOutputFileGroup != false && ${newGrpIsCalled-false} == false ]]; then
  export newGrpIsCalled=true
  newgrp -c $0 $outputFileGroup
  exit $?

else

  [[ ${debugWrapInScript-false} == true ]] && set -xv
  [[ ${debugWrapInScript-false} == false ]] && set +xv

  #set +xuv # Disable output again
  export RODDY_JOBID=${RODDY_JOBID-$$}
  export RODDY_PARENT_JOBS=${RODDY_PARENT_JOBS-false}
  echo "RODDY_JOBID is set to ${RODDY_JOBID}"

  # Replace #{RODDY_JOBID} in passed variables.
  while read line; do
    echo $line
    _temp=$RODDY_JOBID
    export RODDY_JOBID=`echo $RODDY_JOBID | cut -d "." -f 1`
    line=${line//-x/};
    eval ${line//#/\$};
    export RODDY_JOBID=$_temp
  done <<< `export | grep "#{"`

  setupRoddyScratch

  # Check
  _lock="$jobStateLogFile~"

  # Select the proper lock command. lockfile-create is not tested though.
  lockCommand="lockfile -s 1 -r 50"
  unlockCommand="rm -f"

  useLockfile=true
  [[ -z `which lockfile` ]] && useLockfile=false
  [[ ${useLockfile} == false ]] && lockCommand=lockfile-create && unlockCommand=lockfile-remove && echo "Set lockfile commands to lockfile-create and lockfile-remove"

  startCode=STARTED

  # Check if the jobs parent jobs are stored and passed as a parameter. If so Roddy checks the job state logfile
  # if at least one of the parent jobs exited with a value different to 0.
  if [[ ! ${RODDY_PARENT_JOBS} = false ]]
  then
    # Now check all lines in the file
    strlen=`expr ${#RODDY_PARENT_JOBS} - 2`
    RODDY_PARENT_JOBS=${RODDY_PARENT_JOBS:1:strlen}
    for parentJob in ${RODDY_PARENT_JOBS[@]}; do
      [[ ${exitCode-} == 250 ]] && continue;
      result=`cat ${jobStateLogFile} | grep -a "^${parentJob}:" | tail -n 1 | cut -d ":" -f 2`
      [[ ! $result -eq 0 ]] && echo "At least one of this parents jobs exited with an error code. This job will not run." && startCode="ABORTED"
    done
  fi

  # Check the wrapped script for existence
  [[ ${WRAPPED_SCRIPT-false} == false || ! -f ${WRAPPED_SCRIPT} ]] && startCode=ABORTED && echo "The wrapped script is not defined or not existing."

  # Put in start in Leetcode
  ${lockCommand} $_lock;
  echo "${RODDY_JOBID}:${startCode}:"`date +"%s"`":${TOOL_ID}" >> ${jobStateLogFile};
  ${unlockCommand} $_lock
  [[ ${startCode} == 60000 || ${startCode} == "ABORTED" ]] && echo "Exitting because a former job died." && exit 250
  # Sleep a second before and after executing the wrapped script. Allow the system to get different timestamps.
  sleep 2

  export WRAPPED_SCRIPT=${WRAPPED_SCRIPT} # Export script so it can identify itself
  # TODO Integrate automated checkpoint file creation
  #[[ -f ${FILENAME_CHECKPOINT} ]] && ${FILENAME_CHECKPOINT}

  # Create directories
  mkdir -p ${DIR_TEMP} 2 > /dev/null

  echo "Calling script ${WRAPPED_SCRIPT}"
  jobProfilerBinary=${JOB_PROFILER_BINARY-}
  [[ ${enableJobProfiling-false} == false ]] && jobProfilerBinary=""

  myGroup=`groups  | cut -d " " -f 1`
  outputFileGroup=${outputFileGroup-$myGroup}

  $jobProfilerBinary bash ${WRAPPED_SCRIPT}
  exitCode=$?
  echo "Exited script ${WRAPPED_SCRIPT} with value ${exitCode}"

  [[ ${debugWrapInScript-false} == true ]] && set -xuv
  [[ ${debugWrapInScript-false} == false ]] && set +xuv

  sleep 2

  ${lockCommand} $_lock;
  echo "${RODDY_JOBID}:${exitCode}:"`date +"%s"`":${TOOL_ID}" >> ${jobStateLogFile};
  ${unlockCommand} $_lock

  # Set this in your command factory class, when roddy should clean up the dir for you.
  [[ ${RODDY_AUTOCLEANUP_SCRATCH-false} == "true" ]] && rm -rf ${RODDY_SCRATCH} && echo "Auto cleaned up RODDY_SCRATCH"

  [[ ${exitCode} -eq 0 ]] && exit 0

  [[ ${exitCode} -eq 100 ]] && Finished script with 99 for compatibility reasons with Sun Grid Engine. 100 is reserved for SGE usage. && exit 99
  exit $exitCode

fi