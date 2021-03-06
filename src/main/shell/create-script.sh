#!/bin/bash

set -x

MD5=$(md5sum target/javaxdelta-*-jar-with-dependencies.jar | cut -d " " -f 1)
ARCHIVE_START=$(($(wc -l  src/main/shell/javaxdelta.sh | cut -d" " -f1) + 1))
sed -e "s/%%MD5%%/$MD5/" -e "s/%%ARCHIVE_START%%/$ARCHIVE_START/" src/main/shell/javaxdelta.sh > target/javaxdelta.sh
cat target/javaxdelta-*-jar-with-dependencies.jar >> target/javaxdelta.sh
ln target/javaxdelta-*-jar-with-dependencies.jar target/javaxdelta-uber-$MD5.jar
chmod 755 target/javaxdelta.sh
