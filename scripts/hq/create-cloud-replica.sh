echo Setting up replication ...
(set -x; maprcli stream replica autosetup -path /mapr/dc1.enterprise.org/apps/pipeline/data/cloudStream -replica /mapr/aws1.enterprise.org/apps/pipeline/data/cloudStream -useexistingreplica true)