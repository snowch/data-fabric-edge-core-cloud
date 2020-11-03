#!/bin/bash
# Copies complete Microservices Dashboard app tar file to server.
# See usage for details.

AWS_SERVER=aws1
AWS_KEY=/Users/jjanos/Keys/JJHome.pem
AWS_USER=ec2-user
AZURE_SERVER=azure1
AZURE_KEY=/Users/jjanos/Keys/azure_key
AZURE_USER=azure-user

SERVER=$1

if [ "$SERVER" == "" ]; then
  echo "usage push_app_complete.sh <target>"
  echo "where target ="
  echo "  maprdemo (for sandbox)"
  echo "  edge1 (or any hostname) for standard ssh"
  echo "  aws (Server=$AWS_SERVER, Key=$AWS_KEY, User=$AWS_USER)"
  echo "  azure (Server=$AZURE_SERVER, Key=$AZURE_KEY, User=$AZURE_USER)"
  exit 1;
fi

SOURCE_DIR=/Users/jjanos/MyDemos/edge
TAR_FILE=microservices-dashboard-demo.tar
DEST_DIR=.

echo Pushing complete MICROSERVICES DASHBOARD app from local machine to $SERVER.

if [ "$SERVER" = "maprdemo" ]; then
  PORT="-P 2222"
else
  PORT=""
fi

USER=mapr
if [ "$SERVER" = "aws" ]; then
  USER=$AWS_USER
  SERVER=$AWS_SERVER
  KEY="-i $AWS_KEY"
fi
if [ "$SERVER" = "azure" ]; then
  USER=$AZURE_USER
  SERVER=$AZURE_SERVER
  KEY="-i $AZURE_KEY"
fi

set -x
scp $PORT $KEY "$SOURCE_DIR"/$TAR_FILE $USER@$SERVER:$DEST_DIR

