#!/usr/bin/env bash
# Compila y ejecuta las pruebas. Requiere JDK 17+.
set -e
cd "$(dirname "$0")"
rm -rf out-test && mkdir out-test
javac --release 17 -d out-test $(find src/main/java src/test/java -name '*.java')
cp -r src/main/resources/. out-test/
java -cp out-test com.hfw.builds.SelfTest
rm -rf out-test
