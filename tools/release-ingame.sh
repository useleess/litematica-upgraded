#!/bin/bash
# The release tester's in-game run (discord-bot/tester.py, mods.json "ingame", on the test box): the same run as
# tools/check-ingame.sh (tools/box-ingame.sh = every phase), then the result lines printed in the tester's protocol:
# every SCOPE-TEST line of the game logs, and "SCOPE TESTS ALL: N passed, M failed" only when the selftest finished.
bash tools/box-ingame.sh
LOG=_out/latest.log
[ -f "$LOG" ] || { echo "NO RESULT: no game log"; exit 1; }
grep -o "SCOPE-TEST \(pass\|fail\|skip\) .*" "$LOG" | tr -d '\r'
grep -q "SCOPE-TEST done" "$LOG" || { echo "NO RESULT: the selftest did not finish"; exit 1; }
pass=$(grep -c "SCOPE-TEST pass " "$LOG"); fail=$(grep -c "SCOPE-TEST fail " "$LOG")
echo "SCOPE TESTS ALL: $pass passed, $fail failed"
[ "$fail" = 0 ]
