#!/bin/bash
# The check for the runtime step: tools/check.sh (coverage + build + unit tests) and then a real Forge 1.12.2 game
# with MixinBooter on the test box must reach the main menu with all mixins working. Exit 0 only when all pass.
# The game's log and a main-menu screenshot come back in build-smoke/.
HERE=$(cd "$(dirname "$0")/.." && pwd)
MAKER=$(cd "$HERE/../.." && pwd)  # the workspace: the mod sits two levels below it (also in ladder copies)
bash "$HERE/tools/check.sh" || exit 1
LOG="$HERE/.check-smoke.log"
rm -rf "$HERE/build-smoke"
LU_REPO=$(cygpath -w "$HERE") LU_BOT=$(cygpath -w "$MAKER/discord-bot") \
"$MAKER/discord-bot/.venv/Scripts/python.exe" -c '
import os, sys
sys.path.insert(0, os.environ["LU_BOT"])
import testbox
from pathlib import Path
repo = Path(os.environ["LU_REPO"])
for line in testbox.run(repo, ["tools/box-smoke.sh"], "lu-smoke", fetch=("_out", repo / "build-smoke"), group="litematica-upgraded"):
    print(line, end="", flush=True)
' > "$LOG" 2>&1
grep -vE '^\S*\[2m' "$LOG"
grep -q '^SMOKE-PASS' "$LOG"
