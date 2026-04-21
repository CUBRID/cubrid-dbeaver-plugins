#!/bin/bash
set -euo pipefail

# Configuration
REPO_SOURCE="org.cubrid.dbeaver.repository/target/repository"
VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
OUTPUT_DIR="p2-site-plugin"

echo "Building CUBRID DBeaver plugins version ${VERSION}..."

# Build with Maven
mvn clean package -DskipTests \
  -Djdk.xml.maxGeneralEntitySizeLimit=0 \
  -Djdk.xml.totalEntitySizeLimit=0 \
  -Djdk.xml.maxOccurLimit=0 \
  -Djdk.xml.maxParameterEntitySizeLimit=0 \
  -Djdk.xml.maxElementContentWhitespaceLimit=0 || { echo "ERROR: Maven build failed."; exit 1; }

# Prepare output directory
echo "Cleaning old site and preparing ${OUTPUT_DIR}..."
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# Copy the generated repository from the repository module
if [ -d "$REPO_SOURCE" ]; then
    cp -R "$REPO_SOURCE/"* "$OUTPUT_DIR/"
    echo "P2 Repository generated."
else
    echo "ERROR: Repository source not found at ${REPO_SOURCE}"
    exit 1
fi
