"""Every file of masa's Litematica + malilib must be in the Forge port - none dropped or gutted to make it compile.

    python -I tools/upstream_coverage.py <reference folder> <mod folder>

Java files move from <lib>/ (litematica/, malilib/) to com/useleess/litematicaupgraded/<lib>/ (relocated so a LiteLoader
Litematica in the same game can't clash) and must keep at least 70% of their non-blank lines. Asset files must all
be there. A loader-only file that really has no Forge counterpart goes in tools/upstream-exceptions.txt as
"<upstream path> | <reason>" (at most 10, each one read by the reviewer).
"""
import sys
from pathlib import Path

LIBS = ("litematica", "malilib")
MAX_EXCEPTIONS = 10
MIN_SHARE = 0.7


def lines(path):
    return sum(1 for line in path.read_text(encoding="utf-8", errors="replace").splitlines() if line.strip())


def main(ref, mod):
    exceptions = {}
    ex_file = mod / "tools" / "upstream-exceptions.txt"
    if ex_file.exists():
        for line in ex_file.read_text(encoding="utf-8").splitlines():
            if line.strip() and not line.startswith("#"):
                path, _, reason = line.partition("|")
                if not reason.strip():
                    return [f"exception without a reason: {line}"]
                exceptions[path.strip()] = reason.strip()
    problems = []
    if len(exceptions) > MAX_EXCEPTIONS:
        problems.append(f"{len(exceptions)} exceptions, at most {MAX_EXCEPTIONS}")
    total = 0
    for lib in LIBS:
        base = ref / f"{lib}-ornithe-1.12.2" / "src" / "main"
        for src in sorted((base / "java").rglob("*.java")):
            rel = src.relative_to(base / "java").as_posix()
            total += 1
            if rel in exceptions:
                continue
            target = mod / "src/main/java/com/useleess/litematicaupgraded" / rel
            if not target.exists():
                problems.append(f"missing: {rel} -> {target.relative_to(mod).as_posix()}")
            elif lines(target) < MIN_SHARE * lines(src):
                problems.append(f"gutted: {rel} ({lines(target)} of {lines(src)} lines)")
        for src in sorted((base / "resources" / "assets").rglob("*")):
            if src.is_file():
                rel = src.relative_to(base / "resources").as_posix()
                if not (mod / "src/main/resources" / rel).exists():
                    problems.append(f"missing asset: {rel}")
    for path in exceptions:
        if not any((ref / f"{lib}-ornithe-1.12.2/src/main/java" / path).exists() for lib in LIBS):
            problems.append(f"exception for a file upstream doesn't have: {path}")
    return problems, total


if __name__ == "__main__":
    result = main(Path(sys.argv[1]), Path(sys.argv[2]))
    if isinstance(result, list):
        print(result[0]); sys.exit(1)
    problems, total = result
    for p in problems[:60]:
        print(p)
    if len(problems) > 60:
        print(f"... and {len(problems) - 60} more")
    print(f"upstream coverage: {total} java files, {len(problems)} problems")
    sys.exit(1 if problems else 0)
