import hashlib
import json
import re
from pathlib import Path

base = Path(__file__).resolve().parent
rows = []
for job in json.loads((base / 'simulator-matrix-results.json').read_text(encoding='utf-8-sig')):
    name = job['mode'] + '-' + job['category']
    assert job['exitCode'] == 0, name
    log = (base / (name + '.log')).read_text(encoding='utf-8-sig', errors='replace')
    match = re.search(r'^Markdown: (.+)$', log, re.MULTILINE)
    assert match, name
    md = Path(match.group(1).strip())
    run = md.parent
    state = json.loads((run / 'run-state.json').read_text(encoding='utf-8'))
    assert state['state'] == 'PASS' and state['success'] and state['reportsComplete'], name
    assert state['totalCases'] == state['verifiedCases'], name
    assert state['failedCases'] == state['verificationErrors'] == 0, name
    assert state['buildEvidence']['sourceArtifactsMatch'], name
    for report in state['reports']:
        content = (run / report['path']).read_bytes()
        assert hashlib.sha256(content).hexdigest() == report['sha256'], (name, report['path'])
    rows.append({
        'name': name, 'runId': state['runId'], 'state': state['state'],
        'totalCases': state['totalCases'], 'verifiedCases': state['verifiedCases'],
        'failedCases': state['failedCases'], 'verificationErrors': state['verificationErrors'],
        'sourceSha256': state['buildEvidence']['sourceSha256'],
        'directory': str(run), 'reports': state['reports'],
    })
assert len(rows) == 7
assert len({row['sourceSha256'] for row in rows}) == 1
summary = {'totalCases': sum(row['totalCases'] for row in rows), 'runs': rows}
(base / 'simulator-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'totalCases': summary['totalCases'], 'runs': [
    {key: row[key] for key in ('name', 'state', 'totalCases', 'verifiedCases')} for row in rows
]}, ensure_ascii=False))

positive = next(Path(row['directory']) / 'testdokumentation.md' for row in rows if row['name'] == 'run-POSITIV')
text = positive.read_text(encoding='utf-8')
checks = {}
for label, amount in [('Alleinerziehend', '2822.24'), ('Alleinerziehend', '3312.80'),
                      ('Alleinerziehend', '3750.80'), ('Warmwasser', '1063.00')]:
    # Der vollständige Bericht enthält sowohl die Eingaben als auch SQL-Soll-/Istwerte.
    checks[amount] = amount in text
assert all(checks.values()), checks
assert 'SELECT VERMOEGEN FROM BEDARFSGEMEINSCHAFT WHERE ID = ?' in text
assert 'SELECT BUDGET_REST FROM BEDARFSGEMEINSCHAFT WHERE ID = ?' in text
(base / 'boundary-report-checks.json').write_text(json.dumps(checks, indent=2), encoding='utf-8')
