#!/bin/sh
BASEDIR=$(cd "$(dirname "$0")"; pwd -P)
WRAPPER_DIR="$BASEDIR/.mvn/wrapper"
PROPS_FILE="$WRAPPER_DIR/maven-wrapper.properties"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"

if [ ! -f "$PROPS_FILE" ]; then
  echo "Missing $PROPS_FILE"
  exit 1
fi

WRAPPER_URL=$(grep -E '^wrapperUrl=' "$PROPS_FILE" | sed -e 's/wrapperUrl=//')

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "Downloading Maven Wrapper jar..."
  mkdir -p "$WRAPPER_DIR"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR"
  else
    echo "Neither curl nor wget found to download Maven Wrapper jar."
    exit 1
  fi
fi

exec java -Dmaven.multiModuleProjectDirectory="$BASEDIR" -classpath "$WRAPPER_JAR" org.apache.maven.wrapper.MavenWrapperMain "$@"
