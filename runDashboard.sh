#!/bin/bash

EDITION=$1

if [ "$EDITION" != 'hq' ] && [ "$EDITION" != 'edge' ] && [ "$EDITION" != 'cloud' ] ; then
  echo "usage: runDashboard.sh [hq|edge|cloud]"
  exit 1;
fi


# Kills the background Jetty process on termination.
function gracefulExit {
  echo
  echo Terminating Jetty process...
  kill $JETTY_PID
  wait $JETTY_PID
  echo Removing log4j files...
  rm log4.*
  echo finished.
}

if [ "$EDITION" = 'cloud' ]; then

  java -cp `mapr classpath`:./extracted/demoapp/microservices-dashboard/target/microservices-dashboard-1.0.jar pipeline.microservices.cloud.DsRecordingService

else

  trap gracefulExit EXIT

  echo Launching Jetty in the background...
  JETTY_HOME=/home/mapr/microservices-dashboard/extracted/jetty/jetty-distribution-9.4.5.v20170502
  #cd $JETTY_HOME/demo-base/
  #java -jar $JETTY_HOME/start.jar jetty.http.port=9080
  cd $JETTY_HOME
  java -jar $JETTY_HOME/start.jar &
  JETTY_PID=$!

  # sleep to avoid mixing up all the startup messages.
  sleep 3s
  echo "JETTY Started (PID $JETTY_PID) ...."

  # Run the demo app...
  cd -

  # Connecting to MCS (REST API) requires our own truststore.
  # But connecting to AWS requires a normal one!
  if [ "$EDITION" = 'hq' ]; then
    TRUSTSTORE=' '
  else
    TRUSTSTORE='-Djavax.net.ssl.trustStore=/opt/mapr/conf/ssl_truststore'
  fi

  java $TRUSTSTORE -cp ./aws/lib/*:./aws/third-party/lib/*:./extracted/vertx/lib/*:`mapr classpath`:./extracted/demoapp/microservices-dashboard/target/microservices-dashboard-1.0.jar MicroservicesDashboard $EDITION

fi


