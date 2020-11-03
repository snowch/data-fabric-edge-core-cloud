#!/bin/bash
# Creates a tar file of the complete Microservices Dashboard Demo.

BASE_DIR=/Users/jjanos/MyDemos/edge
PROJECT=microservices-dashboard

cd $BASE_DIR
tar -cvf "$PROJECT-demo.tar" ./"$PROJECT"
cd -
