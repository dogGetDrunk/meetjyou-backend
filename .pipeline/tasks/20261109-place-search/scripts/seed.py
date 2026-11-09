import json,sys
d=json.load(open(sys.argv[1])); sgg=d['sgg']
sido={}
for code,(sn,_) in sgg.items(): sido[code[:2]]=sn
rows=[("00","미지정","미지정","SIDO",None,0)]
for c in sorted(sido): rows.append((c,sido[c],sido[c],"SIDO",None,1))
parents={}
for code,(sn,name) in sgg.items():
    if ' ' in name:
        city=name.split(' ')[0]; pc=code[:4]+'0'
        assert parents.get(pc,city)==city; parents[pc]=city
        assert pc not in sgg
for pc,city in sorted(parents.items()):
    rows.append((pc,city,f"{sido[pc[:2]]} {city}","SIGUNGU",pc[:2],1))
for code in sorted(sgg):
    sn,name=sgg[code]
    parent=code[:4]+'0' if ' ' in name else code[:2]
    rows.append((code,name,f"{sn} {name}","SIGUNGU",parent,1))
# parents must precede children for FK: sido first, then cities, then the rest (cities sorted before)
out=["-- Regions: 시·도 > 시·군·구, general-gu (일반구) under their city. Built from the 2026-09 SBIZ store file",
     "-- (시도코드/시군구코드 are 법정동 codes); city codes of general-gu = first 4 digits + '0'.",
     "-- '00' (미지정) holds rows written before travel destinations had a region; never searchable.",
     "INSERT INTO region (code, name, full_name, level, parent_code, searchable) VALUES"]
vals=[]
for c,n,f,l,p,s in rows:
    pv="NULL" if p is None else f"'{p}'"
    vals.append(f"    ('{c}', '{n}', '{f}', '{l}', {pv}, {s})")
out.append(",\n".join(vals)+";")
open(sys.argv[2],'w').write("\n".join(out)+"\n")
print(len(rows),'rows', len(sido),'sido',len(parents),'parents',len(sgg),'sgg')
