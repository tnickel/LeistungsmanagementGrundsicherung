import json,re
from pathlib import Path
from pypdf import PdfReader

base=Path(__file__).resolve().parent
results=[]
for name in ['run-POSITIV','run-NEGATIV','run-LAST','run-STRESS','web-POSITIV','web-NEGATIV','web-LAST']:
    log=(base/(name+'.log')).read_text(encoding='utf-8-sig',errors='replace')
    match=re.search(r'(?:Lauf beendet\. PDF:|Web-Lauf beendet\. PDF:) (.+)',log)
    if not match:raise RuntimeError('PDF path missing: '+name)
    pdf=Path(match.group(1).strip());run=pdf.parent
    state=json.loads((run/'run-state.json').read_text(encoding='utf-8'))
    reader=PdfReader(pdf)
    picks=sorted(set([0,min(1,len(reader.pages)-1),min(4,len(reader.pages)-1),len(reader.pages)-1]))
    texts=[{'page':p+1,'text':reader.pages[p].extract_text()[:3500]} for p in picks]
    results.append({'name':name,'runId':state['runId'],'state':state['state'],'totalCases':state['totalCases'],'verifiedCases':state['verifiedCases'],'failedCases':state['failedCases'],'verificationErrors':state['verificationErrors'],'pdf':str(pdf),'pages':len(reader.pages),'md':str(pdf.with_suffix('.md')),'samplePages':texts})
(base/'report-inspection.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps([{k:v for k,v in row.items() if k!='samplePages'} for row in results],ensure_ascii=False))
