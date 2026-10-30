#!/bin/bash
# The check for the Forge port: every upstream file is converted (none dropped to make it compile), then it
# compiles and its unit tests pass on the test box. Exit code 0 only when both pass.
HERE=$(cd "$(dirname "$0")/.." && pwd)
MAKER=$(cd "$HERE/../.." && pwd)  # the workspace: the mod sits two levels below it (also in ladder copies)
python -I "$HERE/tools/upstream_coverage.py" "$MAKER/litematica-upgraded/reference" "$HERE" || exit 1
LOG="$HERE/.check-box.log"
"$MAKER/discord-bot/.venv/Scripts/python.exe" "$MAKER/discord-bot/testbox.py" run "$HERE" tools/box-build.sh > "$LOG" 2>&1
cat "$LOG"
grep -q '^CHECK-PASS' "$LOG"
