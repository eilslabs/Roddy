#!/bin/sh

# File compression detection equal to the one in Common.groovy but for server side execution.
# TODO Maybe create a custom jar / java program which can do this for both.
# detect compression method of file: can be gzip, bzip2, ASCII
COMPRESSION=`file -bL ${TEST_FILE} | cut -d ' ' -f 1`
if [[ $COMPRESSION = "setgid" ]]  # "setgid gzip compressed data": sticky bit is set for the file
then
    COMPRESSION=`file -bL $1 | cut -d ' ' -f 2`
fi
if [[ $COMPRESSION = "gzip" ]]
then
  UNZIPTOOL="gunzip"
  UNZIPTOOL_OPTIONS=" -c"
  ZIPTOOL="gzip"
  ZIPTOOL_OPTIONS=" -c"
elif [[ $COMPRESSION = "bzip2" ]]
then
  UNZIPTOOL="bunzip2"
  UNZIPTOOL_OPTIONS=" -c -k"
  ZIPTOOL="bzip2"
  ZIPTOOL_OPTIONS=" -c -k"
elif [[ $COMPRESSION = "ASCII" ]]
then
  UNZIPTOOL="cat"
  UNZIPTOOL_OPTIONS=""
  ZIPTOOL="head"
  ZIPTOOL_OPTIONS=" -n E"
else
  echo "Unknown compression $COMPRESSION; skipping $1"
  continue
fi