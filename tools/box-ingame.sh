#!/bin/bash
# Runs on the test box (sent there by tools/check-ingame.sh): build the mod + the selftest jar, then tools/ingame.ps1.
TC=$(cd ../../toolchain && pwd)
export JAVA_HOME=$(ls -d "$TC"/jdk8/*/ | head -1)
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME="$TC/gradle-home"
./gradlew --no-daemon --console=plain build selftestJar 2>&1 | grep -E "error:|FAILED|BUILD |tests completed|What went wrong|Could not" | head -200
[ "${PIPESTATUS[0]}" = 0 ] || { echo INGAME-FAIL: build; exit 1; }
powershell -NoProfile -ExecutionPolicy Bypass -File "$(cygpath -w "$PWD/tools/ingame.ps1")"
