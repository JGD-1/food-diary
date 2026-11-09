import csv, sys
names = {r[0]: r[1] for r in csv.reader(open(sys.argv[1], encoding='utf8'))}
want = set(sys.argv[2].split(';'))
ids = {k for k, v in names.items() if v in want}
out = open(sys.argv[3], 'w', encoding='utf8', newline='')
for line in sys.stdin.buffer:
    parts = line.decode('utf8', 'replace').rstrip('\r\n').split(',')
    # ImageID,Source,LabelName,Confidence (labels) or ImageID,Source,LabelName,Confidence,XMin,... (boxes)
    if len(parts) > 3 and parts[2] in ids and parts[3] == '1':
        out.write(','.join(parts) + '\n')
