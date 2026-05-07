#!/bin/bash
SHELL_DIR="$( cd "$( dirname "$0" )" && pwd -P )"
set -euo pipefail

# Set the version of the plugins
#   mvn tycho-versions:set-version -DnewVersion=X.Y.Z
REPO_SOURCE=${SHELL_DIR}/org.cubrid.dbeaver.repository/target/repository
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
OUTPUT_DIR=${SHELL_DIR}/p2-site-plugin
PACKAGE_NAME=CUBRID-DBeaver-Plugins-${VERSION}.zip

echo "Building CUBRID DBeaver plugins version ${VERSION}..."
cd ${SHELL_DIR}

# Build with Maven
mvn clean package -DskipTests \
  -Djdk.xml.maxGeneralEntitySizeLimit=0 \
  -Djdk.xml.totalEntitySizeLimit=0 \
  -Djdk.xml.maxOccurLimit=0 \
  -Djdk.xml.maxParameterEntitySizeLimit=0 \
  -Djdk.xml.maxElementContentWhitespaceLimit=0 || { echo "ERROR: Maven build failed."; exit 1; }

# Prepare output directory
echo "Cleaning old site and preparing ${OUTPUT_DIR}..."
if [ -d "$OUTPUT_DIR" ]; then
    rm -rf "$OUTPUT_DIR"
fi
mkdir -p "$OUTPUT_DIR"

# Copy the generated repository from the repository module
if [ -d "$REPO_SOURCE" ]; then
    cp -R "$REPO_SOURCE/"* "$OUTPUT_DIR/"
    echo "P2 Repository generated."
    if [ -f "${SHELL_DIR}/${PACKAGE_NAME}" ]; then
        rm -f "${SHELL_DIR}/${PACKAGE_NAME}"
    fi
    cd ${OUTPUT_DIR} && zip -r ${SHELL_DIR}/${PACKAGE_NAME} . -q
    echo "P2 Repository zipped to ${SHELL_DIR}/${PACKAGE_NAME}."
else
    echo "ERROR: Repository source not found at ${REPO_SOURCE}"
    exit 1
fi
