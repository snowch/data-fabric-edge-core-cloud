#!/bin/bash

# CHANGE THESE!
AWS_USER_TAG=jjanos
AWS_REGION=us-east-1
AWS_AVAILABILITY_ZONE=us-east-1a
VOL_SIZE_GB=2500

# Pre-Requisites:
# AWS CLI must be 1) installed and 2) configured (aws configure)

# Identify the instance IDs for your MapR nodes here.
# A little clunky, but list each instance x times for x drives.
# For example, if using 4 drives, list each instance 4 times in a row, 
# then move on to the next instance.
instanceIDs=(
	'i-0baada8781ea371cb'
	'i-0baada8781ea371cb'
	'i-0baada8781ea371cb'
	'i-0baada8781ea371cb'
	'i-05bfef6b74560a1f6'
	'i-05bfef6b74560a1f6'
	'i-05bfef6b74560a1f6'
	'i-05bfef6b74560a1f6'
	'i-02c0e96858c8d8b06'
	'i-02c0e96858c8d8b06'
	'i-02c0e96858c8d8b06'
	'i-02c0e96858c8d8b06'
)

# Also clunky, but list each drive for instance 1,
# then each drive for instance 2, etc.
devices=(
	'/dev/xvdf'
	'/dev/xvdg'
	'/dev/xvdh'
	'/dev/xvdi'
        '/dev/xvdf'
        '/dev/xvdg'
        '/dev/xvdh'
        '/dev/xvdi'
        '/dev/xvdf'
        '/dev/xvdg'
        '/dev/xvdh'
        '/dev/xvdi'
)

echo Removing existing output files from previous runs...
if [ -e createdEBSvols.out ]; then
  rm createdEBSvols.out
fi
if [ -e deleteEBSvolumes.out.sh ]; then
  rm deleteEBSvolumes.out.sh
fi
if [ -e detachEBSvolumes.out.sh ]; then
  rm detachEBSvolumes.out.sh
fi

echo Creating 12 volumes, 4 for each instance...
for i in {1..12}; do 
	aws ec2 create-volume --size $VOL_SIZE_GB --region $AWS_REGION --availability-zone $AWS_AVAILABILITY_ZONE --volume-type st1 --tag-specifications "ResourceType=volume,Tags=[{Key=purpose,Value=CDC Performance Testing},{Key=user,Value=$AWS_USER_TAG}]" >> createdEBSvols.out

done

echo Extracting the Volume IDs...
awk -F'"' '/VolumeId/ { print $4 }' createdEBSvols.out > ebsVolumeIDs.out
echo Volume IDs written to ebsVolumeIDs.out.

echo Pausing 20 seconds to wait for volume creation...
sleep 20

echo Attaching new volumes to instances...
recordCt=0

while IFS='' read -r line || [[ -n "$line" ]]; do
	printf "Attaching Volume $line to ${instanceIDs[recordCt]}\n"
	aws ec2 attach-volume --volume-id $line --instance-id ${instanceIDs[recordCt]} --device ${devices[recordCt]}
        recordCt=$(($recordCt+1))
done < ebsVolumeIDs.out

echo Generate detach and delete script....
awk -F'"' '/VolumeId/ { print "aws ec2 detach-volume --volume-id " $4 }' createdEBSvols.out > detachEBSvolumes.out.sh
awk -F'"' '/VolumeId/ { print "aws ec2 delete-volume --volume-id " $4 }' createdEBSvols.out >> deleteEBSvolumes.out.sh
chmod +x detachEBSvolumes.out.sh
chmod +x deleteEBSvolumes.out.sh
echo Detach and delte scripts created.
echo Remember that these volumes are *NOT* set to delete on instance termination.
echo

echo Cleaning up...
rm ebsVolumeIDs.out
rm createdEBSvols.out

exit 0

