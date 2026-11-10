"""Run each named query REPS times in one session; report server-side duration and InnoDB page reads.

usage: python3 -I bench.py queries.sql [reps]
queries.sql: blocks separated by lines starting with '-- name: <label>'.
"""
import subprocess
import sys

M = ["docker", "exec", "-i", "mjy-perf", "mysql", "-uroot", "-ppw", "--default-character-set=utf8mb4", "-N", "-B", "mjy"]
STATUS = ("SELECT variable_value FROM performance_schema.global_status WHERE variable_name IN "
          "('Innodb_buffer_pool_read_requests','Innodb_buffer_pool_reads') ORDER BY variable_name;")


def blocks(path):
    name, buf = None, []
    for line in open(path, encoding="utf-8"):
        if line.startswith("-- name:"):
            if name:
                yield name, "".join(buf).strip().rstrip(";")
            name, buf = line.split(":", 1)[1].strip(), []
        else:
            buf.append(line)
    if name:
        yield name, "".join(buf).strip().rstrip(";")


def run(label, sql, reps):
    # A block may hold several statements separated by a ';;' line: one search = their sum.
    stmts = [x.strip().rstrip(";") for x in sql.split("\n;;\n") if x.strip()]
    script = ["SET profiling = 1;", "SET profiling_history_size = 100;"]
    for _ in range(reps):
        script.append(STATUS)
        script += [f"SELECT COUNT(*) FROM ({q}) t;" for q in stmts]
        script.append(STATUS)
    script.append("SHOW PROFILES;")
    lines = subprocess.run(M, input="\n".join(script), capture_output=True, text=True).stdout.splitlines()
    per = 4 + len(stmts)
    vals = lines[: reps * per]
    profs = [float(l.split("\t")[1]) * 1000 for l in lines[reps * per:] if "SELECT COUNT(*) FROM (" in l]
    durs = sorted(sum(profs[i * len(stmts):(i + 1) * len(stmts)]) for i in range(reps))
    logical, physical, rows = [], [], 0
    for i in range(reps):
        v = vals[i * per:(i + 1) * per]
        logical.append(int(v[-2]) - int(v[0]))
        physical.append(int(v[-1]) - int(v[1]))
        rows = sum(int(x) for x in v[2:-2])
    p95 = durs[min(len(durs) - 1, int(len(durs) * 0.95))]
    print(f"{label:<34} rows={rows:<3} p50={durs[len(durs) // 2]:8.1f}ms p95={p95:8.1f}ms "
          f"logical={sorted(logical)[len(logical) // 2]:>8} physical={sorted(physical)[len(physical) // 2]:>7}")


reps = int(sys.argv[2]) if len(sys.argv) > 2 else 5
for label, sql in blocks(sys.argv[1]):
    run(label, sql, reps)
