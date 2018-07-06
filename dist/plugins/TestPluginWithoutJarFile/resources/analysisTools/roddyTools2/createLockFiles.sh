#!/bin/bash

set -xuv

# The script parses the env output and greps for LOCKFILE_...
# It then stores the filtered output in an array and creates those files with touch
# Those files can then be locked with lockfile. Lockfile will block if a file is already existing.

lockfiles=`env | grep "LOCKFILE_"`

for lf in $lockfiles
do
    touch `echo $lf | cut -d "=" -f 2`
done

#sleep 60