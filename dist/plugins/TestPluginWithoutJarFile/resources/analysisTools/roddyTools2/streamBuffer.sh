#!/bin/bash

#PBS -q highmem
#PBS -l mem=120g

set -xuv
set -o pipefail

# This script creates n input buffers and n output buffers

# Ports are shared with n input and n output files.

# Open input buffers, create named pipes first and buffer to those
npipeStr="${DIR_TEMP}/${PBS_JOBID}_np_"
portTemp=$((0x`echo ${PBS_JOBID} | md5sum | cut -b 1-8` % 16000 + 49152))
processNumber[0]=-1

# Find n open ports
for ((i = 0; i < $BUFFER_COUNT; i++))
do
    lockfile ${DIR_TEMP}/~streamingBufferPortFinder.lock
    npipe=$npipeStr$i
    mkfifo $npipe
    portIn=`${TOOLSDIR}/../roddy/findOpenPort.sh ${portTemp}`
    portOut=`${TOOLSDIR}/../roddy/findOpenPort.sh ${portIn}`
    portTemp=$portOut

    portFileIn="PORTEXCHANGE_INFILE_$i"
    portFileOut="PORTEXCHANGE_OUTFILE_$i"

#    ip=`ip addr | grep "10.0.0." | cut -d " " -f 6 | cut -d "/" -f 1`
#    [[ -z "$ip" ]] &&
    ip=`ip addr | grep "inet " | grep "19"  | grep -v "0.0." | cut -d " " -f 6 | cut -d "/" -f 1`

    echo $ip $portIn > ${!portFileIn}
    echo $ip $portOut > ${!portFileOut}

#    echo `hostname` $port > ${!portFile}
    ${JAVA_BINARY:-java} -Xmx12G -jar ${TOOL_MEMORY_STREAMER} serve $portIn $portOut 10G 2 $DIR_TEMP/${PBS_JOBID}_memStreamer_server_${i} &
    processNumber=("$processNumber[@]" $!)
#    netcat -vv -l -p $port | mbuffer -m 10G > $npipe & #$BUFFER_SIZE$BUFFER_UNIT
#    mbuffer -I $port -m 10G | tee ${DIR_TEMP}/${PBS_JOBID}_saifile_$i > $npipe & #$BUFFER_SIZE$BUFFER_UNIT
    rm -rf ${DIR_TEMP}/~streamingBufferPortFinder.lock
done

# Remove locks, so the input can start
for in_lock in `env | grep "IN_LOCKFILE" | cut -d "=" -f 2`
do
    rm -rf $in_lock
done

# Wait for the output buffers
for out_lock in `env | grep "OUT_LOCKFILE" | cut -d "=" -f 2`
do
    rm -rf $out_lock
done

# Read out output buffer options
# Read from named pipe to output buffers

#sleep 4 # Sleep a bit so the receiving mbuffer can start up
#
#for ((i = 0; i < $BUFFER_COUNT; i++))
#do
#    portFile="PORTEXCHANGE_OUTFILE_$i"
#    hostAndPort=`cat ${!portFile}`
#    host=`echo $hostAndPort | cut -d " " -f 1`
#    port=`echo $hostAndPort | cut -d " " -f 2`
#
##    hostIP=`ping $host | head -n 1 | cut -d "(" -f 2 | cut -d ")" -f 1`
#
#
#
#    npipe=$npipeStr$i
#    nice cat $npipe | netcat -vv ${host} ${port} &
##    nice cat $npipe | mbuffer -O ${host}:${port} &
#    processNumber=("$processNumber[@]" $!)
#done

# Wait for the output processes!
#for ((i = 1; i <= $BUFFER_COUNT; i++))
#do
#    wait ${processNumber[$i]}
#done

wait

# Remove named pipes
for ((i = 0; i < $BUFFER_COUNT; i++))
do
    rm -rf np_0
done

# Finished
sleep 60