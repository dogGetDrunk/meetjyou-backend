import zipfile, csv, io, sys, collections
z = zipfile.ZipFile(sys.argv[1])
names = [i for i in z.infolist()]
for i in names:
    try: i.filename = i.filename.encode('cp437').decode('utf-8')
    except Exception: pass
print([i.filename for i in names])
txt=[i for i in names if i.filename.endswith('.txt')][0]
print(z.read(txt).decode('utf-8','replace'))
cats=collections.Counter(); sido=collections.Counter(); sgg={}; total=0; blank=collections.Counter()
for i in names:
    if not i.filename.endswith('.csv'): continue
    with z.open(i) as f:
        r=csv.reader(io.TextIOWrapper(f,encoding='utf-8-sig'))
        h=next(r)
        if total==0: print(list(enumerate(h)))
        for row in r:
            total+=1
            d=dict(zip(h,row))
            cats[(d['상권업종대분류코드'],d['상권업종대분류명'],d['상권업종중분류코드'],d['상권업종중분류명'],d['상권업종소분류코드'],d['상권업종소분류명'])]+=1
            sido[(d['시도코드'],d['시도명'])]+=1
            sgg[d['시군구코드']]=(d['시도명'],d['시군구명'])
            for k in ('경도','위도','도로명주소','상호명'):
                if not d.get(k): blank[k]+=1
print('total',total,'blank',blank)
print(sorted(sido.items()))
import json
json.dump({'cats':[list(k)+[v] for k,v in cats.items()],'sgg':sgg},open(sys.argv[2],'w'),ensure_ascii=False)
