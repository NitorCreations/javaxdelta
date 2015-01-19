#!/bin/bash

MD5=%%MD5%%

if [ "$1" = "-d" ]; then
  shift
  if [[ "$1" =~ ^[0-9]*$ ]]; then
    DEBUG_PORT=$1
    shift
  else
    DEBUG_PORT=4444
  fi
  DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=$DEBUG_PORT"
fi
if [ -z "$JXDELTA_HOME" ]; then
  JXDELTA_HOME=$(cd $(dirname "${BASH_SOURCE[0]}") && pwd -P)
fi

mkdir -p $JXDELTA_HOME/lib
if [ ! -d $JXDELTA_HOME/lib ]; then
  echo "Failed to create javaxdelta lib directory"
  exit 1
fi

JXDELTA_JAR=$JXDELTA_HOME/lib/$MD5.jar
tail -n+%%ARCHIVE_START%% $0 > $JXDELTA_JAR

if [ -z "$JAVA_HOME" ]; then
  if ! which java > /dev/null; then
    echo "No java or java on PATH"
    exit 1
  else
    JAVA=$(which java)
  fi
else
  JAVA=$JAVA_HOME/bin/java
fi

export JXDELTA_HOME
case $1 in
  delta)
  shift
  exec $JAVA $DEBUG -cp $JXDELTA_JAR at.spardat.xma.xdelta.JarDelta "$@"
  ;;
  patch)
  shift
  EXTRA_ARGS=""
  while [[ "$1" =~ ^-p ]]; do
    if [[ "$1" =~ ^-ps ]]; then
      EXTRA_ARGS="$EXTRA_ARGS -Dpatcher.ignoreSourcePathElements=$2"
      shift
      shift
    elif [[ "$1" =~ ^-po ]]; then
      EXTRA_ARGS="$EXTRA_ARGS -Dpatcher.ignoreOutputPathElements=$2"
      shift
      shift
    fi
  done
  exec $JAVA $EXTRA_ARGS $DEBUG -cp $JXDELTA_JAR at.spardat.xma.xdelta.JarPatcher "$@"
  ;;
  *)
  echo "usage:"
  echo "  $0 [-d [port]] delta source.zip target.zip patch.zip"
  echo "    or"
  echo "  $0 [-d [port]] patch [-ps num] [-po num] patch.zip [target.zip [source.zip]]"
  echo "    -d         start debugger and wait on defined port (4444 by default)"
  echo "    -ps num    ingore num path elements on the source entry inside the patch"
  echo "    -po num    ingore num path elements on the output entry inside the patch"
  exit 1
  ;;
esac
