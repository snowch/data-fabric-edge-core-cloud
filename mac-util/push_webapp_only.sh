#!/bin/bash
# Copies Microservices Dashboard Java app to server

SERVER=$1

if [ "$SERVER" == "" ]; then
  echo "usage <server-name> [keyfile]"
  exit 1;
fi

if [ "$2" != "" ]; then
  KEYOPTION="-i $2"
fi

SOURCE_DIR=/Users/jjanos/MyDemos/edge/microservices-dashboard/eclipse
TAR_FILE=microservices-dashboard-app.tar
DEST_DIR=./microservices-dashboard/eclipse

echo Pushing Eclipse project tar file to $SERVER

if [ "$SERVER" = "maprdemo" ]; then
  PORT=2222
else
  PORT=22
fi

set -x
scp -P $PORT $KEYOPTION "$SOURCE_DIR"/$TAR_FILE mapr@$SERVER:$DEST_DIR
