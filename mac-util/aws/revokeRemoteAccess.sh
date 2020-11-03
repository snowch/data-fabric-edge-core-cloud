# Used to quickly modify AWS Security Groups at customer sites.

# The security group to be modified
SG_MAPR_NODES=sg-01def6a935f8bff16

PATH=/Users/jjanos/Library/Python/2.7/bin:$PATH
aws --version

IPADDR=$1

if [ "$IPADDR" == '' ] ; then
  echo "usage: revokeRemoteAccess.sh [your_current_ip_address]"
  exit 1;
fi

echo Revoking demo access from IP address $IPADDR...

echo Revoking port 22...
aws ec2 revoke-security-group-ingress --group-id $SG_MAPR_NODES --protocol tcp --port 22 --cidr $IPADDR/32

echo Adding port 8443...
aws ec2 revoke-security-group-ingress --group-id $SG_MAPR_NODES --protocol tcp --port 8443 --cidr $IPADDR/32

echo Adding port 8080...
aws ec2 revoke-security-group-ingress --group-id $SG_MAPR_NODES --protocol tcp --port 8080 --cidr $IPADDR/32

echo Adding port 8081...
aws ec2 revoke-security-group-ingress --group-id $SG_MAPR_NODES --protocol tcp --port 8081 --cidr $IPADDR/32

echo Finished.

