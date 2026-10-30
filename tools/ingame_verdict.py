"""Verdict of an in-game run: reads the SCOPE-TEST lines of the game log against test-plan.json.

    python -I tools/ingame_verdict.py <game log> <test-plan.json> [<plan of the base commit>]

Passes only when the selftest finished ("SCOPE-TEST done"), nothing failed, and every plan feature has at least
`min` different passing test ids with its prefix. With a base plan given, a feature line removed or a min lowered
compared to it also fails (a plan is never weakened to get a pass).
"""
import json
import re
import sys

LINE = re.compile(r"SCOPE-TEST (pass|fail|skip) (\S+)(?: :: (.*))?")


def main(log_path, plan_path, base_path=None):
    text = open(log_path, encoding="utf-8", errors="replace").read()
    results = {}
    for kind, test_id, reason in LINE.findall(text):
        results[test_id] = (kind, reason)
    plan = json.load(open(plan_path, encoding="utf-8"))["features"]
    problems = []
    if "SCOPE-TEST done" not in text:
        problems.append("the selftest did not finish (no 'SCOPE-TEST done')")
    for test_id, (kind, reason) in sorted(results.items()):
        if kind == "fail":
            problems.append(f"FAIL {test_id} :: {reason}")
    for f in plan:
        passed = sorted(t for t, (k, _) in results.items() if k == "pass" and t.startswith(f["prefix"]))
        if len(passed) < f["min"]:
            problems.append(f"plan: {f['prefix']} needs {f['min']} passing tests, has {len(passed)}")
    if base_path:
        base = {f["prefix"]: f["min"] for f in json.load(open(base_path, encoding="utf-8"))["features"]}
        now = {f["prefix"]: f["min"] for f in plan}
        for prefix, minimum in base.items():
            if prefix not in now:
                problems.append(f"plan weakened: {prefix} removed")
            elif now[prefix] < minimum:
                problems.append(f"plan weakened: {prefix} min {minimum} -> {now[prefix]}")
    counts = {k: sum(1 for v in results.values() if v[0] == k) for k in ("pass", "fail", "skip")}
    for p in problems[:40]:
        print(p)
    print(f"in-game: {counts['pass']} pass, {counts['fail']} fail, {counts['skip']} skip, {len(problems)} problems")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:4]))
