echo Running this script in the deployment environment
echo will create a tar file of the complete demo
echo so as to retrieve it for redeployment elsewhere.
echo
echo It turns out to be easier just to make the changes in the 
echo dev environment and move those changes to the deployment 
echo environment each time, rather than pull the changes back,
echo so this script is not used often.
echo You have been so advised. 

BASE_DIR=/home/mapr
APP_DIR=microservices-dashboard

echo
echo "Delete files in $BASE_DIR/$APP_DIR/extracted ?"
echo "You will have to run the install.sh script"
echo "to extract these files again before running this demo."
read -p "Delete files? (y|n) " -n 1 -r

case "$REPLY" in
  y|Y ) echo; echo "Removing files..."; rm -rf $BASE_DIR/$APP_DIR/extracted; rm log4*;;
  * ) echo; echo "Leaving files in place.";
esac

cd $BASE_DIR
tar cvf microservices-dashboard-demo.tar $APP_DIR/

echo
echo microservices-dashboard-demo.tar created in $BASE_DIR.

