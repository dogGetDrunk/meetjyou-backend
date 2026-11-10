"""Reorder generated rows like the production load: 시·도 in Korean name order (the SBIZ zip's file order)."""
import re
import sys

seed = open(sys.argv[1], encoding="utf-8").read()
rows = re.findall(r"\('(\d+)', '([^']*)', '([^']*)', '(\w+)', (?:'(\d+)'|NULL), ([01])\)", seed)
parent = {r[0]: r[4] for r in rows}
name = {r[0]: r[1] for r in rows}


def sido(code):
    while parent.get(code):
        code = parent[code]
    return name[code]


lines = open(sys.argv[2], encoding="utf-8").read().splitlines()
lines.sort(key=lambda l: sido(l.split("\t")[6]))
sys.stdout.write("\n".join(lines) + "\n")
