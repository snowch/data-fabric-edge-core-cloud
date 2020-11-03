#!/bin/bash

EDITION=$1
APP_NAME=microservices-dashboard
APP_HOME=/home/mapr/$APP_NAME

if [ "$EDITION" != 'hq' ] && [ "$EDITION" != 'edge' ] && [ "$EDITION" != 'cloud' ]; then
  echo "usage: installDemo.sh [hq|edge|cloud]"
  exit 1;
fi

if [ ! "$USER" = "mapr" ]; then
  echo "Run this script as mapr"
  exit 1
fi

echo "Microservices Dashboard - Demo Installation Script"
echo "This script will install various components of the Microservices Dashboard Demo."
echo
echo "Application tar file should be extracted to $APP_HOME"
echo "MapR has to be up and running."

if [ "$EDITION" = 'hq' ]; then
  echo "Installing HQ demo edition."
  CLUSTER_NAME=dc1.enterprise.org
elif [ "$EDITION" = 'edge' ]; then
  echo "Installing EDGE demo edition."
  CLUSTER_NAME=edge1.enterprise.org
else
  echo "Installing CLOUD demo edition."
  CLUSTER_NAME=aws1.enterprise.org
fi

echo
echo "***  CLUSTER_NAME is set to $CLUSTER_NAME  ***"
echo "Part of this installation will write to MapR-FS over NFS."
echo "If the wrong cluster name is specified, and multiple clusters"
echo "are connected, data could be written to the wrong cluster."
echo
read -p "Press ENTER to acknowledge."

echo

WARNINGS=NONE
if [ -d "$APP_HOME/extracted" ]; then
  echo "$APP_HOME/extracted will be deleted and rebuilt."
  WARNINGS=YES
fi
if [ -d "/mapr/$CLUSTER_NAME/apps/pipeline" ]; then
  echo "/mapr/$CLUSTER_NAME/apps/pipeline will be deleted and rebuilt."
  WARNINGS=YES
fi

if [ "$WARNINGS" = "YES" ]; then
  echo
  read -p "Press ENTER to acknowledge these warnings and proceed."

  if [ -d "$APP_HOME/extracted" ]; then
    rm -rf $APP_HOME/extracted
    echo "$APP_HOME/extracted deleted."
  fi

  if [ -d "/mapr/$CLUSTER_NAME/apps/pipeline" ]; then
    if [ -d "/mapr/$CLUSTER_NAME/apps/pipeline/data/files-missionX" ]; then
      echo "Removing files-missionX volume (this will be recreated)..."
      maprcli volume remove -name files-missionX -force true
      echo "  volume removed."
    fi
    rm -rf /mapr/$CLUSTER_NAME/apps/pipeline
    echo "/mapr/$CLUSTER_NAME/apps/pipeline deleted."
  fi

  echo "Cleanup complete."
fi

echo "Beginning Installation..."

mkdir $APP_HOME/extracted

if [ "$EDITION" = 'hq' ] || [ "$EDITION" = 'edge' ] ; then

  echo
  JETTY_DIR=$APP_HOME/extracted/jetty
  echo Installing Jetty, extracting to $JETTY_DIR...
  mkdir $JETTY_DIR
  tar -xvzf $APP_HOME/downloads/jetty-distribution-9.4.5.v20170502.tar.gz --directory=$JETTY_DIR

  echo 
  echo Copying Jetty config files to serve static content...
  cp $APP_HOME/config/jetty-dash-conf.xml $APP_HOME/extracted/jetty/jetty-distribution-9.4.5.v20170502/webapps
  cp $APP_HOME/config/jetty-maprfs-images-HQ.xml $APP_HOME/extracted/jetty/jetty-distribution-9.4.5.v20170502/webapps

  echo
  VERTX_DIR=$APP_HOME/extracted/vertx
  echo Installing Vert.x, extracting to $VERTX_DIR...
  mkdir $VERTX_DIR
  tar -xvzf $APP_HOME/downloads/vert.x-3.3.3-full.tar.gz --directory=$APP_HOME/extracted

  # The AWS SDK should also be here, but I only have the extracted files at this point,
  # not the original download.
  echo
  echo Skipping AWS SDK - should already be in $APP_HOME/aws.

fi

echo
WEBAPP_DIR=$APP_HOME/extracted/demoapp
echo Extracting the web app to $WEBAPP_DIR...
mkdir $WEBAPP_DIR
tar xvf eclipse/$APP_NAME-app.tar --directory=$WEBAPP_DIR
mv $WEBAPP_DIR/$APP_NAME/src/main/resources/config.properties config/config.properties

echo
echo Creating folder structure in MapR...
mkdir /mapr/$CLUSTER_NAME/apps/pipeline
mkdir /mapr/$CLUSTER_NAME/apps/pipeline/data


if [ "$EDITION" = "hq" ]; then

  mkdir /mapr/$CLUSTER_NAME/apps/pipeline/data/nasa
  mkdir /mapr/$CLUSTER_NAME/apps/pipeline/data/nasa/image_files
  mkdir /mapr/$CLUSTER_NAME/apps/pipeline/data/nasa/meta

  echo Creating files-missionX volume....
  maprcli volume create -name files-missionX -path /apps/pipeline/data/files-missionX -replication 1 -minreplication 1

  echo Copying starter data to MapR...
  cp $APP_HOME/data-HQ/search_hits/*.json /mapr/$CLUSTER_NAME/apps/pipeline/data/nasa/meta
  tar -xvf $APP_HOME/data-HQ/downloaded_images/downloaded_images.tar -C /mapr/$CLUSTER_NAME/apps/pipeline/data/nasa/image_files

fi

if [ "$EDITION" = "edge" ]; then

  # Enable stream auditing
  # Auditing is required to detect the establishment of replication.
  # Streams replication is configured via the MCS of the HQ Cluster.
  # That creates a stream replica object here on the Edge cluster.
  # By listening to the audit stream, we are able to detect the
  # creation of this object, and which point other services
  # can subscribe to that stream.  Subscribers cannot subscribe to
  # that stream before the stream replica is created.

  # Enable stream auditing
  maprcli config save -values '{"mfs.enable.audit.as.stream":"1"}'

  # Enable auditing of filesystem and table operations
  maprcli audit data -enabled true -retention 1

  # Enable auditing on the mapr.apps volume
  maprcli volume audit -name mapr.apps -enabled true -dataauditops +create,+delete,+tablecreate,-setattr,-chown,-chperm,-chgrp,-getxattr,-listxattr,-setxattr,-removexattr,-read,-write,-mkdir,-readdir,-rmdir,-createsym,-lookup,-rename,-createdev,-truncate,-tablecfcreate,-tablecfdelete,-tablecfmodify,-tablecfScan,-tableget,-tableput,-tablescan,-tableinfo,-tablemodify,-getperm,-getpathforfid,-hardlink

  # Enable auditing on the directory where the streams replica
  # is to be created.
  hadoop mfs -setaudit on /apps/pipeline/data

  # To verify:
  # maprcli volume info -name mapr.apps -json

  # Here, an "A" in the second column indicates it’s being audited.
  # hadoop mfs -ls /apps/pipeline/data

  echo Stream Auditing enabled.

fi

echo 
echo Done with install!
echo Now use runDashboard.sh.
echo HQ and EDGE editions are accessed from a web browser.
echo The CLOUD edition generates a simple text file, viewed using ‘tail -f’.
echo Have a nice demo! 
echo
