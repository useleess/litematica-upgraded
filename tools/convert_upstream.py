"""Copy masa's Litematica + malilib (Ornithe 1.12.2 branches, MCP names already) into this Forge project.

    python -I tools/convert_upstream.py [<reference folder>]      (default: ../reference)

Re-runnable: pulling a newer upstream = update the reference clones, run this, review the git diff. Java packages
move from litematica.* / malilib.* to com.useleess.litematicaupgraded.* so a LiteLoader Litematica in the same game
can't clash; assets are copied unchanged. Files listed in tools/upstream-exceptions.txt (loader-only code) are skipped,
and files under tools/forge-overrides/<upstream path> replace the upstream copy (our Forge versions of loader glue).
"""
import json
import re
import shutil
import sys
from pathlib import Path

MOD = Path(__file__).resolve().parents[1]
BASE_PKG = "com.useleess.litematicaupgraded"
LIBS = ("litematica", "malilib")
PKG_LINE = re.compile(r"^(\s*(?:package|import(?:\s+static)?)\s+)(litematica|malilib)([.;])", re.M)


# fully-qualified uses in code, e.g. "implements malilib.event.X" (string literals are left alone: they are keys)
FQN = re.compile(r"(?<![\w.\"'/$])(litematica|malilib)\.(?=[a-z])")
STRING_OR_CODE = re.compile(r'("(?:\\.|[^"\\])*")')


def relocate(text):
    text = PKG_LINE.sub(lambda m: f"{m.group(1)}{BASE_PKG}.{m.group(2)}{m.group(3)}", text)
    parts = STRING_OR_CODE.split(text)
    for i in range(0, len(parts), 2):
        parts[i] = FQN.sub(lambda m: f"{BASE_PKG}.{m.group(1)}.", parts[i])
    return "".join(parts)


def exceptions():
    path = MOD / "tools" / "upstream-exceptions.txt"
    if not path.exists():
        return set()
    return {line.partition("|")[0].strip() for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.startswith("#")}


def merge_lang(extra, target):
    """Appends the keys of tools/forge-overrides/lang/<domain>/en_us.lang that the upstream file lacks (our additions)."""
    if not extra.exists() or not target.exists():
        return
    text = target.read_text(encoding="utf-8")
    have = {line.partition("=")[0] for line in text.splitlines() if "=" in line}
    added = [line for line in extra.read_text(encoding="utf-8").splitlines()
             if "=" in line and line.partition("=")[0] not in have]
    if added:
        target.write_text(text.rstrip("\n") + "\n" + "\n".join(added) + "\n", encoding="utf-8", newline="\n")


def main(ref):
    skip = exceptions()
    overrides = MOD / "tools" / "forge-overrides"
    java_root = MOD / "src/main/java" / BASE_PKG.replace(".", "/")
    res_root = MOD / "src/main/resources"
    copied = 0
    for lib in LIBS:
        src_main = ref / f"{lib}-ornithe-1.12.2" / "src" / "main"
        shutil.rmtree(java_root / lib, ignore_errors=True)
        for src in sorted((src_main / "java" / lib).rglob("*")):
            if not src.is_file():
                continue
            rel = src.relative_to(src_main / "java").as_posix()
            if rel in skip:
                continue
            override = overrides / rel
            text = (override if override.exists() else src).read_text(encoding="utf-8")
            target = java_root / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(relocate(text) if src.suffix == ".java" else text, encoding="utf-8", newline="\n")
            copied += 1
        assets = src_main / "resources" / "assets"
        for sub in assets.iterdir():
            shutil.rmtree(res_root / "assets" / sub.name, ignore_errors=True)
            shutil.copytree(sub, res_root / "assets" / sub.name)
            merge_lang(overrides / "lang" / sub.name / "en_us.lang", res_root / "assets" / sub.name / "lang" / "en_us.lang")
        for cfg in (src_main / "resources").glob("mixins.*.json"):
            data = json.loads(cfg.read_text(encoding="utf-8"))
            data["package"] = f"{BASE_PKG}.{data['package']}"
            data.pop("refmap", None)
            data["refmap"] = f"mixins.{lib}.refmap.json"
            (res_root / cfg.name).write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"copied {copied} files from {ref}")


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else MOD.parent / "reference")
