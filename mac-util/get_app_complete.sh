#!/bin/bash
# Retrieves complete Microservices Dashboard app tar file from server

SERVER=$1

if [ "$SERVER" == "" ]; then
  echo "usage <server-name>"
  exit 1;
fi

SOURCE_DIR=/home/mapr
TAR_FILE=microservices-dashboard-demo.tar
DEST_DIR=/Users/jjanos/MyDemos/edge/

echo Retrieving complete MICROSERVICES DASHBOARD app from $SERVER.

if [ "$SERVER" = "maprdemo" ]; then
  PORT=2222
else
  PORT=22
fi

scp -P $PORT mapr@$SERVER:$SOURCE_DIR/$TAR_FILE $DEST_DIR

