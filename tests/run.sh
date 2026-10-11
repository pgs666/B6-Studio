#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p build/protocol-tests
javac -d build/protocol-tests app/src/main/java/dev/b6/studio/Protocol.java app/src/main/java/dev/b6/studio/LogStore.java tests/ProtocolTest.java tests/DiagnosticsTest.java
java -cp build/protocol-tests ProtocolTest
java -cp build/protocol-tests DiagnosticsTest
