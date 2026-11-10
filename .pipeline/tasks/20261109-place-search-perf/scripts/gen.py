"""Synthetic place rows shaped like the 2026-09 SBIZ load (1,023,599 rows).

Target shape: common 2-char tokens with large posting lists ('카페' ~25k like prod),
brand names (스타벅스 ~2k), and a long tail of rare names.
"""
import random
import re
import sys

random.seed(42)
N = int(sys.argv[2]) if len(sys.argv) > 2 else 1_023_599
seed_sql = open(sys.argv[1], encoding="utf-8").read()
rows = re.findall(r"\('(\d+)', '([^']*)', '([^']*)', '(\w+)', (?:'(\d+)'|NULL), ([01])\)", seed_sql)
parents = {r[4] for r in rows if r[4]}
districts = [(r[0], r[2]) for r in rows if r[3] == "SIGUNGU" and r[5] == "1" and r[0] not in parents]
# Seoul/Gyeonggi heavier, like the real file.
weights = [3.0 if c.startswith(("11", "41")) else 1.0 for c, _ in districts]

SYL = list("가나다라마바사아자차카타파하고노도로모보소오조초코토포호구누두루무부수우주추쿠투푸후"
           "강남동서북산성신영월일진청한해행화경광대명미백상선수안연용원유은정중천평학현홍")
COMMON = {  # category -> (word, probability the name contains it)
    "음식점": [("식당", .06), ("국밥", .02), ("치킨", .05), ("분식", .03), ("반점", .02), ("횟집", .02),
            ("막창", .004), ("갈비", .03), ("김밥", .03), ("피자", .02), ("족발", .01), ("카페", .003)],
    "카페": [("카페", .20), ("커피", .15), ("베이커리", .05), ("디저트", .03)],
    "술집": [("호프", .15), ("포차", .10), ("주점", .10), ("이자카야", .03)],
    "숙박": [("모텔", .30), ("펜션", .25), ("호텔", .10), ("게스트하우스", .05)],
    "쇼핑": [("편의점", .02), ("약국", .25), ("화장품", .10), ("서점", .05), ("기념품", .03)],
}
BRANDS = {"카페": [("스타벅스", .017), ("이디야커피", .025), ("메가커피", .03), ("투썸플레이스", .012)],
          "쇼핑": [("CU", .12), ("GS25", .12), ("세븐일레븐", .08), ("올리브영", .01)],
          "음식점": [("BBQ", .003), ("교촌치킨", .002), ("맘스터치", .002)]}
CATS = [("음식점", .59), ("카페", .116), ("술집", .089), ("숙박", .081), ("쇼핑", .124)]
ROADS = ["중앙로", "대학로", "시장길", "역전로", "해안로", "공원로", "문화로", "신촌로", "번영로", "평화로"]


def rand_word(lo=2, hi=4):
    return "".join(random.choice(SYL) for _ in range(random.randint(lo, hi)))


def make(cat):
    for brand, p in BRANDS.get(cat, []):
        if random.random() < p:
            return brand, rand_word(2, 3) + "점"
    name = rand_word()
    for word, p in COMMON[cat]:
        if random.random() < p:
            name = random.choice([name + word, word + name, name + " " + word])
            break
    return name, ""


out = sys.stdout
cat_names = [c for c, _ in CATS]
cat_weights = [w for _, w in CATS]
for i in range(N):
    cat = random.choices(cat_names, cat_weights)[0]
    name, branch = make(cat)
    code, full = random.choices(districts, weights)[0]
    search = (name + branch).replace(" ", "")
    addr = f"{full} {random.choice(ROADS)}{random.randint(1, 99)}번길 {random.randint(1, 300)}"
    lat, lng = 33 + random.random() * 5, 126 + random.random() * 3
    out.write("\t".join([f"K{i:09d}", name, branch or "\\N", search, f"C{cat_names.index(cat)}",
                         cat, code, addr, f"{lat:.6f}", f"{lng:.6f}"]) + "\n")
