# Scripts to configure mirroring between the "HQ" cluster and the Edge.
# Show this through the MCS.
# To avoid "fat finger" risks, walk through MCS but run this script instead.
# Explain that MCS is built upon CLI and REST equivalents.

echo Creating the mirror volume ...
(set -x; maprcli volume create -name files-missionX -type mirror -source files-missionX@dc1.enterprise.org -mount 1 -path /apps/pipeline/data/files-missionX -topology /data -auditenabled true)

echo
echo Start mirroring ...
(set -x; maprcli volume mirror start -name files-missionX)

echo
