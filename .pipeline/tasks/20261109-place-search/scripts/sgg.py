import json,sys,collections
d=json.load(open(sys.argv[1]))
sgg=d['sgg']; print('sgg count',len(sgg))
gu=[(k,v) for k,v in sorted(sgg.items()) if ' ' in v[1]]
print('names with space',len(gu))
for k,v in gu: print(k,v)
odd=[(k,v) for k,v in sorted(sgg.items()) if k[-1]!='0' and ' ' not in v[1]]
print('non-0 last digit without space',odd)
keep=set(sys.argv[2].split(','))
print('target count', sum(c[6] for c in d['cats'] if (c[0] in('I1','I2') and c[4] not in('I20701','I21102','I10201')) or c[4] in keep))
