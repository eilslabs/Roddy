#!/bin/bash

# This script wraps in a complete native workflow script and executes it.

[[ ${debugWrapInScript-false} == true ]]  && set -xv
[[ ${debugWrapInScript-false} == false ]] && set +xv

export WRAPPED_SCRIPT=${WRAPPED_SCRIPT} # Export script so it can identify itself
set -xv
[[ ! -d $DIR_TEMP ]] && mkdir $DIR_TEMP

export tmpFileCnt=$DIR_TEMP/counter
export tmpFileCall=$DIR_TEMP/calls

echo 0 > $tmpFileCnt

function qsub() {
    cur=`head -n 1 $tmpFileCnt`
    cur=`expr $cur + 1`
    echo "fakeID_$cur, qsub $*" >> $tmpFileCall
    echo $cur > $tmpFileCnt
    echo "fakeID_$cur"
}

echo "Calling script ${WRAPPED_SCRIPT}"
source ${WRAPPED_SCRIPT}
exitCode=$?
echo "Exited script ${WRAPPED_SCRIPT} with value ${exitCode}"

[[ ${debugWrapInScript-false} == true ]]  && set -xuv
[[ ${debugWrapInScript-false} == false ]] && set +xuv

sleep 2

[[ ${exitCode} -eq 0 ]] && exit 0

[[ ${exitCode} -eq 100 ]] && Finished script with 99 for compatibility reasons with Sun Grid Engine. 100 is reserved for SGE usage. && exit 99
exit $exitCode
