#!/bin/bash
# Creates a tar file of the complete Eclipse workspace
# for the Microservices Dashboard App.

WKSPC_BASE=/Users/jjanos/eclipse/eclipse-workspace
PROJECT=microservices-dashboard
OUTPUT_DIR=/Users/jjanos/MyDemos/edge/$PROJECT/eclipse

cd $WKSPC_BASE
tar -cvf "$OUTPUT_DIR"/"$PROJECT-app.tar" ./"$PROJECT"
cd -
