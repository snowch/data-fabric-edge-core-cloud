# Convenience script to download OpenVPN keys from server
# and copy them to the Sandbox.

# Run this script from your MacBook.

# These are the required files:
# /etc/openvpn/easy-rsa/keys/ca.crt
# /etc/openvpn/easy-rsa/keys/sandbox.crt
# /etc/openvpn/easy-rsa/keys/sandbox.key
# /etc/openvpn/easy-rsa/keys/azureGateway.crt
# /etc/openvpn/easy-rsa/keys/azureGateway.key
# /etc/openvpn/myvpn.tlsauth

# Those keys were probably owned by root and can’t be downloaded directly.
# You can run a script on the AWS server to consolidate them all 
# into a temporary directory accessible by ec2-user.
# Specify that temporary location using AWS_KEYS_LOCATION.

AWS_SSH_KEY=/Users/jjanos/Keys/JJHome.pem
AWS_NODE=100.24.27.228
AWS_KEYS_LOCATION=/home/ec2-user/openvpnKeys-ForDownload

TMP_LOCAL_DIR=/tmp/sandboxKeys
if [ -d "$TMP_LOCAL_DIR" ]; then
    rm -rf $TMP_LOCAL_DIR
    echo "Local temp directory $TMP_LOCAL_DIR already existed. Deleted."
fi

mkdir $TMP_LOCAL_DIR

echo Retrieving keys...
scp -i $AWS_SSH_KEY ec2-user@$AWS_NODE:$AWS_KEYS_LOCATION/* /tmp/sandboxKeys

echo Moving to sandbox...
scp -P 2222 -i $TMP_LOCAL_DIR/* root@localhost:/etc/openvpn/keys
