# Moves OpenVPN keys to a temporary location so
# they can be easily downloaded (to Sandbox, or to Azure)

# Run this script AS ROOT on the OpenVPN server in AWS.

TMP_LOCATION=~ec2-user/openvpnKeys-ForDownload

mkdir $TMP_LOCATION
cp /etc/openvpn/easy-rsa/keys/ca.crt $TMP_LOCATION
cp /etc/openvpn/easy-rsa/keys/sandbox.crt $TMP_LOCATION
cp /etc/openvpn/easy-rsa/keys/sandbox.key $TMP_LOCATION
cp /etc/openvpn/easy-rsa/keys/azureGateway.crt $TMP_LOCATION
cp /etc/openvpn/easy-rsa/keys/azureGateway.key $TMP_LOCATION
cp /etc/openvpn/myvpn.tlsauth $TMP_LOCATION

chown ec2-user:ec2-user $TMP_LOCATION/*

echo Keys copied to $TMP_LOCATION
