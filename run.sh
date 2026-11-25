#!/usr/bin/env bash
# Compila y arranca la web. Requiere JDK 17+.   Uso: ./run.sh   (puerto: PORT=8080 ./run.sh)
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir out
javac --release 17 -d out $(find src/main/java -name '*.java')
cp -r src/main/resources/. out/
java -cp out com.hfw.builds.Main
