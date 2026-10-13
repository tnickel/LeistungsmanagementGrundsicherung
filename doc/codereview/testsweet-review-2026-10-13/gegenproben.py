import re, sys, subprocess, tempfile, json
from pathlib import Path
root=Path(sys.argv[1]); checker=root/'doc/testzertifikat-werkzeug/nachrechnen_100.py'
source=(root/'doc/testzertifikat-werkzeug/pruefmenge-positiv-100faelle.md').read_text(encoding='utf-8')
case=next(b for b in re.split(r'(?=^## TF )',source,flags=re.M) if re.match(r'## TF \d+ KOMBINATION ',b))
variants={
 'mehrbedarf_null':re.sub(r'^(p\d+_mehrbedarf_\w+)=.*$',r'\g<1>=0.00',case,flags=re.M),
 'personenzahlbetrag_falsch':re.sub(r'^(p\d+_zahlbetrag)=.*$',r'\g<1>=999999.99',case,flags=re.M),
 'rechenblock_fehlend':case.replace('block: ','block_corrupt: '),
 'zwei_bg_fehler':re.sub(r'^bg_gesamtbedarf=.*$','bg_gesamtbedarf=999999.99',case,flags=re.M)
}
results={}
with tempfile.TemporaryDirectory(prefix='testsweet-review-input-') as td:
 for name, content in variants.items():
  md=Path(td)/(name+'.md'); protocol=Path(td)/(name+'-result.md'); md.write_text(content,encoding='utf-8')
  p=subprocess.run([sys.executable,str(checker),str(md),'--max','1','--protokoll',str(protocol)],capture_output=True,text=True,encoding='utf-8',errors='replace')
  protocol_lines=protocol.read_text(encoding='utf-8').splitlines() if protocol.exists() else []
  results[name]={'exit':p.returncode,'summary':[line for line in p.stdout.splitlines() if line.startswith(('Gepruefte','Abweichungen:'))],'protocol_result':[line for line in protocol_lines if line.startswith('ergebnis = ')]}
print(json.dumps(results,ensure_ascii=False,indent=2))
