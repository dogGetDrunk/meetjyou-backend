import json,sys
d=json.load(open(sys.argv[1]))
want=('I1','I2','G2')
for c in sorted(d['cats']):
    if c[0] in ('I1','I2') or c[0]=='G2':
        print(c)
print('---large', sorted({(c[0],c[1]) for c in d['cats']}))
