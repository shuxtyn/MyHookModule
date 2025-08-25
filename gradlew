#!/usr/bin/env sh

# Simple Gradle Wrapper launcher for CI
# Looks for a usable Java and runs the Gradle Wrapper JAR.

set -e

# Resolve script dir
PRG="$0"
while [ -h "$PRG" ] ; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/"

APP_HOME="`pwd -P`"
cd "$SAVED"

WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Find Java
if [ -n "$JAVA_HOME" ] ; then
  JAVA_CMD="$JAVA_HOME/bin/java"
else
  JAVA_CMD="java"
fi

# Run
exec "$JAVA_CMD" -jar "$WRAPPER_JAR" "$@"
