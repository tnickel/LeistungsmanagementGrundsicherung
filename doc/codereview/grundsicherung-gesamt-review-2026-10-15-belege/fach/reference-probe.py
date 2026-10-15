import importlib.util,json,pathlib,sys
spec=importlib.util.spec_from_file_location('review_ref',pathlib.Path('doc/testzertifikat-werkzeug/nachrechnen_100.py'))
ref=importlib.util.module_from_spec(spec)
spec.loader.exec_module(ref)
out=[]
for file in sys.argv[1:]:
    text=pathlib.Path(file).read_text(encoding='utf-8')
    text=text[text.index('block: '):]
    calc=ref.rechne_block(text)
    doc=ref.dokumentwerte(text)
    diffs=[]
    for i,person in enumerate(calc['personen'],1):
        for key,value in person.items():
            if doc['personen'][i].get(key)!=value: diffs.append(f'p{i}.{key}')
    for field,key in [('gesamt','bg_gesamtbedarf'),('anr','bg_anrechenbar'),('verw','bg_verwertbares_vermoegen'),('zahl','bg_zahlbetrag'),('rest','budget_rest_hypothetisch')]:
        if calc[field]!=doc.get(key):diffs.append(key)
    out.append(dict(datei=pathlib.Path(file).name,arithmetischeBeanstandungen=diffs,zahlbetrag=str(calc['zahl'])))
print(json.dumps(dict(referenz='Python nachrechnen_100',synthetisch=True,proben=out)))
