set -ex

# project.version to be replaced with Maven project version during Maven build (package goal)
BUILD_TAR_GZ=$(ls *.tar.gz)
BUILD_VERSION=${BUILD_TAR_GZ:21:-7}
[ -z "$BUILD_VERSION" ] && { echo "Invalid tar.gz filename, version not found"; exit 1; }
docker build --tag=authzforce/server:${BUILD_VERSION} .
docker login
docker push authzforce/server:${BUILD_VERSION}