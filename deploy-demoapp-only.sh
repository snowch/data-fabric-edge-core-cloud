# This script just extracts the actual java web application.
# Useful for when the rest of the setup hasn't changed.

WEBAPP_DIR=extracted/demoapp
APP_NAME=microservices-dashboard

rm -rf $WEBAPP_DIR
mkdir $WEBAPP_DIR
tar xvf eclipse/$APP_NAME-app.tar --directory=$WEBAPP_DIR

mv $WEBAPP_DIR/$APP_NAME/src/main/resources/config.properties config/config.properties