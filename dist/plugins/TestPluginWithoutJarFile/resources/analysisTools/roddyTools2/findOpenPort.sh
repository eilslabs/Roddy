#!/bin/bash

startPort=49152
[[ $# == 1 ]] && let startPort=$1+1

# Find a free port in the user space
for (( i = $startPort ; i <= 65535 ; i++ ))
do n=`lsof -iTCP:$i`
    [[ "$n" == "" ]] && port=$i && break
done
echo $port