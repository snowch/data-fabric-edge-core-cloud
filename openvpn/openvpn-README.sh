# OpenVPN is used to establish a connection to an OpenVPN server
# operating in the cloud.  It provides sandbox-to-cloud connectivity,
# and can also be configured to allow connectivity between
# the Edge cluster and the cloud directly.
# See the demo documentation for full details.

# OpenVPN configuration files are located in /etc/openvpn

# OpenVPN has been configured to start automatically:
# systemctl enable openvpn@sandbox

# To determine if OpenVPN is running:
# sudo systemctl status openvpn@sandbox

# To determine if an internal IP address has been obtained,
# run ifconfig and look for tun0 interface. 
# Expected address is 10.8.0.2

# If necessary, restart OpenVPN:
# systemctl start openvpn@sandbox

# logs can be found in /etc/openvpn/logs

# Note the obvious: 
# the OpenVPN server must be running for the client to connect.
