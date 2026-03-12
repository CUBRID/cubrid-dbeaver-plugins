#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

ENABLER_VERSION="v1.0.0-$(date +%Y%m%d%H%M)"
BUILD_FOLDER="$SCRIPT_DIR/build"

# CloudBeaver paths
DRIVER_INPUT_PATH="$BUILD_FOLDER/cloudbeaver/server/drivers"
BUNDLE_INPUT_PATH="$BUILD_FOLDER/cloudbeaver/server/bundles/io.cloudbeaver.resources.drivers.base"
ARTIFACT_PATH="$BUILD_FOLDER/cloudbeaver/deploy/cloudbeaver"
BUILD_SCRIPT="$BUILD_FOLDER/cloudbeaver/deploy/build.sh"
SEARCH_PATH="$BUILD_FOLDER/cloudbeaver/deploy/cloudbeaver/server/plugins"
PLUGIN_PATTERN="io.cloudbeaver.resources.drivers.base_*.jar"
CLOUDBEAVER_REPO="https://github.com/dbeaver/cloudbeaver.git"

DIST_DIR="$SCRIPT_DIR/dist"
CUBRID_PACKAGE_LINUX="$DIST_DIR/cubrid-enabler.sh"
CUBRID_PACKAGE_WINDOW="$DIST_DIR/cubrid-enabler.bat"

set -e

# ============================================================
# Functions
# ============================================================

MISSING=()

check_command() {
  command -v "$1" >/dev/null 2>&1 || MISSING+=("$1")
}

check_java() {
  if command -v java >/dev/null 2>&1; then
    java -version >/dev/null 2>&1 || MISSING+=("java")
  else
    MISSING+=("java")
  fi
}

# ============================================================
# Dependency checks
# ============================================================

check_java
check_command git
check_command mvn
check_command node
check_command npm
check_command yarn

if [ ${#MISSING[@]} -ne 0 ]; then
  echo "❌ Missing required dependencies:"
  for dep in "${MISSING[@]}"; do
    if [ "$dep" == "yarn" ]; then
      echo "   - yarn is not installed. Please install yarn before running this script."
      echo "     e.g.) npm install -g yarn"
    else
      echo "   - $dep is not installed. Please install $dep before running this script."
    fi
  done
  echo
  echo "Please install them before running this script."
  read -p "Press [Enter] to exit..."
  exit 1
else
  echo "✅ All required dependencies are installed."
fi

# ============================================================
# Cloudbeaver Build
# ============================================================

#Clone CloudBeaver
if [ -d "$BUILD_FOLDER" ]; then
  echo "CloudBeaver repository already exists."
  rm -rf "$BUILD_FOLDER"
fi
mkdir -p "$BUILD_FOLDER"
git -C "$BUILD_FOLDER" clone "$CLOUDBEAVER_REPO"
echo "CloudBeaver repository cloned."

CLOUDBEAVER_VERSION=$(git -C "$BUILD_FOLDER/cloudbeaver" describe --tags --abbrev=0)

# Download CUBRID JDBC driver via Maven (latest)
DOWNLOAD_LIB_DIR="$BUILD_FOLDER/cubrid-jdbc-driver"
rm -rf "$DOWNLOAD_LIB_DIR"
mkdir -p "$DOWNLOAD_LIB_DIR"
echo "Downloading CUBRID JDBC driver (latest)..."
(
  cd "$DOWNLOAD_LIB_DIR"
  if ! mvn dependency:copy -Dartifact=org.cubrid:cubrid-jdbc:LATEST -DoutputDirectory=. -q; then
    echo "ERROR: Failed to download CUBRID JDBC driver via Maven."
    exit 1
  fi
)
JDBC_JAR=$(find "$DOWNLOAD_LIB_DIR" -name "cubrid-jdbc-*.jar" | head -n 1)
if [[ -z "$JDBC_JAR" ]]; then
  echo "ERROR: mvn did not download cubrid-jdbc jar to $DOWNLOAD_LIB_DIR"
  exit 1
fi
echo "✅ CUBRID JDBC driver downloaded: $JDBC_JAR"

# Extract version from filename (assuming format cubrid-jdbc-X.Y.Z.jar)
CUBRID_VERSION=$(basename "$JDBC_JAR" | sed -E 's/cubrid-jdbc-(.*)\.jar/\1/')
echo "Detected CUBRID driver version: $CUBRID_VERSION"

#Copy Pom.xml file for Driver
mkdir -p "$DRIVER_INPUT_PATH/cubrid"
cp "$SCRIPT_DIR/pom.xml" "$DRIVER_INPUT_PATH/cubrid/pom.xml"

echo "Update pom.xml with actual CUBRID version"
sed -i "s|<version>latest</version>|<version>${CUBRID_VERSION}</version>|" "$DRIVER_INPUT_PATH/cubrid/pom.xml"

echo "Pom.xml file for Driver installed to: $DRIVER_INPUT_PATH/cubrid/pom.xml"

echo "modify global pom.xml for driver"
sed -i '/<modules>/a \        <module>cubrid</module>' "$DRIVER_INPUT_PATH/pom.xml"

echo "modify global bundle plugin.xml"
PLUGIN_XML="$BUNDLE_INPUT_PATH/plugin.xml"
sed -i '/<resource name="drivers\/databend"\/>/a \        <resource name="drivers/cubrid"/>' "$PLUGIN_XML"
if ! grep -q 'name="drivers/cubrid"' "$PLUGIN_XML"; then
  echo "ERROR: CUBRID resource entry was not inserted into plugin.xml. Check the anchor pattern."
  exit 1
fi

sed -i '/<bundle id="drivers.databend" label="Databend drivers"\/>/a \        <bundle id="drivers.cubrid" label="CUBRID drivers"/>' "$PLUGIN_XML"
if ! grep -q 'id="drivers.cubrid"' "$PLUGIN_XML"; then
  echo "ERROR: CUBRID bundle entry was not inserted into plugin.xml. Check the anchor pattern."
  exit 1
fi

sed -i '/<driver id="databend:databend"\/>/a \        <driver id="cubrid:cubrid_jdbc"/>' "$PLUGIN_XML"
if ! grep -q 'id="cubrid:cubrid_jdbc"' "$PLUGIN_XML"; then
  echo "ERROR: CUBRID driver entry was not inserted into plugin.xml. Check the anchor pattern."
  exit 1
fi

#Build CloudBeaver
chmod +x "$BUILD_SCRIPT"
(
  cd "$(dirname "$BUILD_SCRIPT")"
  ./build.sh
)
echo "CloudBeaver built successfully."

# Locate built CloudBeaver drivers.base bundle (no replacement; use build output)
FOUND_FILE=$(find "$SEARCH_PATH" -type f -name "$PLUGIN_PATTERN" | head -n 1)
if [[ -z "$FOUND_FILE" ]]; then
  echo "ERROR: No file matching pattern '$PLUGIN_PATTERN' was found"
  exit 1
fi
echo "Using built bundle: $FOUND_FILE"

# ============================================================
# Generate Linux Distribution (Patch File)
# ============================================================
mkdir -p "$DIST_DIR"
# Create Cubrid Patch file for Linux
echo "Generating $CUBRID_PACKAGE_LINUX..."

cat > "$CUBRID_PACKAGE_LINUX" << 'EOF_LIN'
#!/bin/bash
# Build Version of Cloudbeaver: __CLOUDBEAVER_VERSION__
# Enabler Version: __ENABLER_VERSION__
set -e

# Variable
SHELL_DIR="$(cd "$(dirname "$0")" && pwd -P)"
if [ -d "${SHELL_DIR}/server/plugins" ]; then
  ROOT_PATH="${SHELL_DIR}"
else
  echo "It looks like it's not the root directory of CloudBeaver."
  echo "Please run this script from the root directory of CloudBeaver."
  exit 1
fi

BASE_PATH="${ROOT_PATH}/drivers/cubrid"
SEARCH_PATH="${ROOT_PATH}/server/plugins"
JDBC_FILE_NAME="cubrid-jdbc-__CUBRID_VERSION__.jar"
FILE_PATTERN="io.cloudbeaver.resources.drivers.base_*.jar"

# Function
extract_block() {
  local name="$1"
  local out="$2"

  awk "
    /^__${name}__$/ {flag=1; next}
    /^__END_${name}__$/ {flag=0}
    flag {print}
  " "$0" | base64 -d > "$out"
}

# Create driver directory
mkdir -p "$BASE_PATH"
echo "Created folder: $BASE_PATH"

# Extract CUBRID JDBC driver
extract_block "CUBRID_JDBC_JAR" "$BASE_PATH/$JDBC_FILE_NAME"
echo "CUBRID JDBC driver extracted"

# Find and replace CloudBeaver bundle
FOUND_FILE=$(find "$SEARCH_PATH" -type f -name "$FILE_PATTERN" | head -n 1)

# Backup original file
echo "Backup original file: $FOUND_FILE"
if [ -f "$FOUND_FILE" ]; then
  TIMESTAMP=$(date +%s)
  cp "$FOUND_FILE" "$FOUND_FILE.$TIMESTAMP.bak"
  echo "Backup successful: $FOUND_FILE.$TIMESTAMP.bak"
else
  echo "ERROR: No file matching pattern '$FILE_PATTERN' was found"
  exit 1
fi

# Extract driver bundle
extract_block "DRIVERS_BASE_JAR" "$FOUND_FILE"
echo "Bundle replaced successfully"
echo "(Required) Please restart the CloudBeaver container to enable CUBRID."
exit 0
EOF_LIN

# Replace version placeholder
sed -i "s/__CUBRID_VERSION__/${CUBRID_VERSION}/g" "$CUBRID_PACKAGE_LINUX"
sed -i "s/__CLOUDBEAVER_VERSION__/${CLOUDBEAVER_VERSION}/g" "$CUBRID_PACKAGE_LINUX"
sed -i "s/__ENABLER_VERSION__/${ENABLER_VERSION}/g" "$CUBRID_PACKAGE_LINUX"

# Append embedded JDBC jar
echo "__CUBRID_JDBC_JAR__" >> "$CUBRID_PACKAGE_LINUX"
base64 "$JDBC_JAR" >> "$CUBRID_PACKAGE_LINUX"
echo "__END_CUBRID_JDBC_JAR__" >> "$CUBRID_PACKAGE_LINUX"

# Append embedded drivers.base jar
echo "__DRIVERS_BASE_JAR__" >> "$CUBRID_PACKAGE_LINUX"
base64 "$FOUND_FILE" >> "$CUBRID_PACKAGE_LINUX"
echo "__END_DRIVERS_BASE_JAR__" >> "$CUBRID_PACKAGE_LINUX"

chmod +x "$CUBRID_PACKAGE_LINUX"
echo "✅ Patch cubrid-package.sh generated with embedded jars"
exit 0

# ============================================================
# Generate patch file for Window (Patch File) -- Not Release
# ============================================================
echo "Generating $CUBRID_PACKAGE_WINDOW..."
# Write the batch script logic
cat > "$CUBRID_PACKAGE_WINDOW" << 'EOF_WIN'
@echo off
setlocal enabledelayedexpansion
REM Enabler Version: __ENABLER_VERSION__
REM Cloudbeaver Version: __CLOUDBEAVER_VERSION__

set TIMESTAMP=%DATE%-%TIME%
set TIMESTAMP=%TIMESTAMP::=%
set TIMESTAMP=%TIMESTAMP:.=%
set TIMESTAMP=%TIMESTAMP: =%

REM Variable
set "SCRIPT_DIR=%~dp0"
set "BASE_PATH=%SCRIPT_DIR%drivers\cubrid"
set "SEARCH_PATH=%SCRIPT_DIR%server\plugins"
set "JDBC_FILE_NAME=cubrid-jdbc-__CUBRID_VERSION__.jar"
set "FILE_PATTERN=io.cloudbeaver.resources.drivers.base_*.jar"

REM Create driver directory
mkdir "%BASE_PATH%" 2>nul
echo Created folder: "%BASE_PATH%"

REM Extract Cubrid JDBC driver
powershell -NoProfile -ExecutionPolicy Bypass -Command "$i=$false;$o=@();gc -LiteralPath '%~f0'|ForEach-Object{if($_.Trim() -eq '__CUBRID_JDBC_JAR__'){$i=$true;return};if($_.Trim() -eq '__END_CUBRID_JDBC_JAR__'){$i=$false;return};if($i){$o+=$_}};if($o.Count -eq 0){exit 2};[IO.File]::WriteAllLines('%TEMP%\jdbc.b64',$o,[Text.Encoding]::ASCII)"
if errorlevel 1 exit /b 1
certutil -f -decode "%TEMP%\jdbc.b64" "%BASE_PATH%\%JDBC_FILE_NAME%" >nul || exit /b 1
del "%TEMP%\jdbc.b64" 2>nul
echo CUBRID JDBC driver extracted

REM Find and replace CloudBeaver bundle
set "FOUND_FILE="

for /r "%SEARCH_PATH%" %%F in (%FILE_PATTERN%) do (
    set "FOUND_FILE=%%F"
)

REM Backup original file
echo Backup original file: %FOUND_FILE%
if exist "%FOUND_FILE%" (
  copy "%FOUND_FILE%" "%FOUND_FILE%.%TIMESTAMP%.bak"
  echo "Backup successful: %FOUND_FILE%.%TIMESTAMP%.bak"
) else (
  echo "ERROR: No file matching pattern '%FILE_PATTERN%' was found"
  exit /b 1
)

REM Extract driver bundle
powershell -NoProfile -ExecutionPolicy Bypass -Command "$i=$false;$o=@();gc -LiteralPath '%~f0'|ForEach-Object{if($_.Trim() -eq '__DRIVERS_BASE_JAR__'){$i=$true;return};if($_.Trim() -eq '__END_DRIVERS_BASE_JAR__'){$i=$false;return};if($i){$o+=$_}};if($o.Count -eq 0){exit 2};[IO.File]::WriteAllLines('%TEMP%\base.b64',$o,[Text.Encoding]::ASCII)"
if errorlevel 1 exit /b 1
certutil -f -decode "%TEMP%\base.b64" "%FOUND_FILE%" >nul || exit /b 1
del "%TEMP%\base.b64" 2>nul
echo Bundle replaced successfully
echo "(Required) Please restart the CloudBeaver application to enable CUBRID."

endlocal
exit /b 0

REM Embedded files (base64)
EOF_WIN

# Replace version placeholder
sed -i "s/__CUBRID_VERSION__/${CUBRID_VERSION}/g" "$CUBRID_PACKAGE_WINDOW"
sed -i "s/__CLOUDBEAVER_VERSION__/${CLOUDBEAVER_VERSION}/g" "$CUBRID_PACKAGE_WINDOW"
sed -i "s/__ENABLER_VERSION__/${ENABLER_VERSION}/g" "$CUBRID_PACKAGE_WINDOW"

# Append embedded JDBC jar
echo "__CUBRID_JDBC_JAR__" >> "$CUBRID_PACKAGE_WINDOW"
base64 "$JDBC_JAR" >> "$CUBRID_PACKAGE_WINDOW"
echo "__END_CUBRID_JDBC_JAR__" >> "$CUBRID_PACKAGE_WINDOW"

# Append embedded drivers.base jar
echo "__DRIVERS_BASE_JAR__" >> "$CUBRID_PACKAGE_WINDOW"
base64 "$FOUND_FILE" >> "$CUBRID_PACKAGE_WINDOW"
echo "__END_DRIVERS_BASE_JAR__" >> "$CUBRID_PACKAGE_WINDOW"

echo "✅ Patch cubrid-package.bat generated with embedded jars"
exit 0
