#!/bin/bash
# The check for in-world work: tools/check.sh (coverage + build + unit tests), then the in-game tests on the test box
# (tools/ingame.ps1: server + real Forge game + selftest jar), judged by tools/ingame_verdict.py against
# test-plan.json (never weaker than master's). Exit 0 only when all pass. Optional: FILTER=<test id prefix>.
# Game log, server log and screenshots come back in build-ingame/.
HERE=$(cd "$(dirname "$0")/.." && pwd)
MAKER=$(cd "$HERE/../.." && pwd)  # the workspace: the mod sits two levels below it (also in ladder copies)
bash "$HERE/tools/check.sh" || exit 1
# OptiFine may not be redistributed: its jar is copied in for the run only (git ignores it) and removed afterwards
mkdir -p "$HERE/_optifine" && cp "$MAKER/redstone-scope/reference/modtest-downloads/OptiFine_1.12.2_HD_U_G5.jar" "$HERE/_optifine/"
trap 'rm -rf "$HERE/_optifine"' EXIT
LOG="$HERE/.check-ingame.log"
rm -rf "$HERE/build-ingame"
LU_REPO=$(cygpath -w "$HERE") LU_BOT=$(cygpath -w "$MAKER/discord-bot") \
"$MAKER/discord-bot/.venv/Scripts/python.exe" -c '
import os, sys
sys.path.insert(0, os.environ["LU_BOT"])
import testbox
from pathlib import Path
repo = Path(os.environ["LU_REPO"])
for line in testbox.run(repo, ["tools/box-ingame.sh"], "lu-ingame", fetch=("_out", repo / "build-ingame"), group="litematica-upgraded"):
    print(line, end="", flush=True)
' > "$LOG" 2>&1
grep -vE '^\S*\[2m' "$LOG" | tail -30
[ -f "$HERE/build-ingame/latest.log" ] || { echo "no game log came back"; exit 1; }
BASE="$HERE/.base-plan.json"
git -C "$HERE" show master:test-plan.json > "$BASE" 2>/dev/null || cp "$HERE/test-plan.json" "$BASE"
[ -n "$FILTER" ] && { python -I "$HERE/tools/ingame_verdict.py" "$HERE/build-ingame/latest.log" /dev/null 2>/dev/null; grep -E "SCOPE-TEST (fail|pass)" "$HERE/build-ingame/latest.log" | tail -40; grep -q "SCOPE-TEST done" "$HERE/build-ingame/latest.log" && ! grep -q "SCOPE-TEST fail" "$HERE/build-ingame/latest.log"; rc=$?; [ $rc -eq 0 ] && rm -rf "$HERE/build-ingame" "$BASE"; exit $rc; }
python -I "$HERE/tools/ingame_verdict.py" "$HERE/build-ingame/latest.log" "$HERE/test-plan.json" "$BASE"
rc=$?
# a passed run leaves nothing behind; a failed one keeps its logs + screenshots in build-ingame/ to find the cause
[ $rc -eq 0 ] && rm -rf "$HERE/build-ingame" "$BASE"
exit $rc
