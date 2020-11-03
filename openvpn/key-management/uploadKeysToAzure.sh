# Convenience script to upload OpenVPN keys to Azure.

# Run this script from your MacBook.

# These are the required files:
# /etc/openvpn/easy-rsa/keys/ca.crt
# /etc/openvpn/easy-rsa/keys/azureGateway.crt
# /etc/openvpn/easy-rsa/keys/azureGateway.key
# /etc/openvpn/myvpn.tlsauth

# It’s assumed those keys have already been downloaded from AWS
# and placed in $AWS_KEYS_LOCATION.
# The script to do that is downloadKeysToSandbox.sh

# These will be uploaded to /etc/openvpn/keys,
# which has to be created and writeable by azure-user.

# AZURE_NODE has to have a valid record in local /etc/hosts file.
AZURE_USER=azure-user
AZURE_SSH_KEY=/Users/jjanos/Keys/azure_key
AZURE_NODE=openvpn.azure
AZURE_KEYS_LOCATION=/etc/openvpn/keys

LOCAL_TMP_DIR=/tmp/sandboxKeys
if [ ! -d "$LOCAL_TMP_DIR" ]; then
    echo “Cannot find keys locally in $LOCAL_TMP_DIR. Exiting.”
    exit 1;
fi

echo Uploading keys to azure...
scp -i $AZURE_SSH_KEY $LOCAL_TMP_DIR/* $AZURE_USER@$AZURE_NODE:/etc/openvpn/keys
