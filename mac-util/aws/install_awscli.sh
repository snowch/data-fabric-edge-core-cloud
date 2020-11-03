# Installs the AWS CLI on macOS
# Used to quickly modify AWS Security Groups at customer sites.
# Instructions found on Amazon: 
# https://docs.aws.amazon.com/cli/latest/userguide/cli-install-macos.html

curl -O https://bootstrap.pypa.io/get-pip.py
python get-pip.py --user
PATH=/Users/jjanos/Library/Python/2.7/bin:$PATH
pip install awscli --upgrade --user
aws --version
pip install awscli --upgrade --user

