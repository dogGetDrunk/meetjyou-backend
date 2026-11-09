"""Write before.sql (current single query) and after.sql (exact + rest, same paths as PlaceSearchService).

usage: python3 -I queries.py <out_dir> <all_codes_csv> <seoul_csv> <busan_csv> <incheon_csv>
Region path (threshold 30k active rows): Seoul 191k, Busan 41k -> full-text; Incheon 28k, Ulleung 2.5k -> region index.
"""
import sys

out, all_codes, seoul, busan, incheon = sys.argv[1:6]
COLS = "id, name, branch_name, category_name, road_address, lat, lng, region_code"
STOP = ["a", "about", "an", "are", "as", "at", "be", "by", "com", "de", "en", "for", "from", "how", "i", "in",
        "is", "it", "la", "of", "on", "or", "that", "the", "this", "to", "und", "was", "what", "when", "where",
        "who", "will", "with", "www"]
CASES = [("카페 전국", "카페", None, "fts"), ("카페 서울", "카페", seoul, "fts"),
         ("카페 울릉군", "카페", "'47940'", "index"), ("스타벅스 전국", "스타벅스", None, "fts"),
         ("이디야커피 전국", "이디야커피", None, "fts"), ("막창 전국", "막창", None, "fts"),
         ("희귀어 전국", "강남북산", None, "fts"), ("카페 부산", "카페", busan, "fts"),
         ("희귀어 부산", "강남북산", busan, "fts"), ("카페 인천", "카페", incheon, "index"),
         ("희귀어 인천", "강남북산", incheon, "index")]


def terms(q):
    toks = []
    for i in range(len(q) - 1):
        t = q[i:i + 2]
        if not any(w in t.lower() for w in STOP) and t not in toks:
            toks.append(t)
    return " ".join(f'+"{t}"' for t in toks)


def region(codes):
    return f" AND region_code IN ({codes})" if codes else ""


before, after = [], []
for label, q, codes, path in CASES:
    before.append(f"-- name: before {label}\nSELECT {COLS} FROM place WHERE MATCH(search_name) AGAINST "
                  f"('\"{q}\"' IN BOOLEAN MODE) AND active = 1{region(codes)} "
                  f"ORDER BY (search_name = '{q}') DESC, id LIMIT 20")
    exact = (f"SELECT /*+ NO_MRR(place) */ {COLS} FROM place WHERE region_code IN ({codes or all_codes}) AND active = 1 "
             f"AND search_name = '{q}' LIMIT 20")
    like = f"search_name LIKE '%{q}%' ESCAPE '!' AND search_name <> '{q}'"
    if path == "fts":
        rest = (f"SELECT {COLS} FROM place WHERE MATCH(search_name) AGAINST ('{terms(q)}' IN BOOLEAN MODE) "
                f"AND {like} AND active = 1{region(codes)} LIMIT 20")
    else:
        rest = f"SELECT /*+ NO_MRR(place) */ {COLS} FROM place WHERE region_code IN ({codes}) AND active = 1 AND {like} LIMIT 20"
    after.append(f"-- name: after {label}\n{exact}\n;;\n{rest}")
open(f"{out}/before.sql", "w", encoding="utf-8").write("\n".join(before) + "\n")
open(f"{out}/after.sql", "w", encoding="utf-8").write("\n".join(after) + "\n")
