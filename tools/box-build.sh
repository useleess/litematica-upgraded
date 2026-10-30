#!/bin/bash
# Runs on the test box (sent there by tools/check.sh): compile + unit tests with the box's shared toolchain.
TC=$(cd ../../toolchain && pwd)
export JAVA_HOME=$(ls -d "$TC"/jdk8/*/ | head -1)
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="$TC/gradle-home"
./gradlew --no-daemon --console=plain build 2>&1 | grep -E "error:|FAILED|BUILD |tests completed|What went wrong|Could not" | head -300
[ "${PIPESTATUS[0]}" = 0 ] && echo CHECK-PASS || echo CHECK-FAIL
