import json
from pathlib import Path

base = Path(__file__).resolve().parent
status = json.loads((base / 'llm-review-status.json').read_text(encoding='utf-8'))
assert status['state'] == 'PASS', status['state']
assert status['completedCases'] == status['totalCases'] == 18
assert status['completedBatches'] == status['totalBatches'] == 6
assert status['transportVerified'] and status['calculationVerified'] and status['reviewCoverageVerified']
assert not status['hasFailures'] and status['caseVerdict'] == 'PASS'
assert len(status['cases']) == 18 and all(case['verdict'] == 'PASS' for case in status['cases'])
directory = Path(status['artifactDirectory'])
manifest = json.loads((directory / 'manifest.json').read_text(encoding='utf-8'))
summary = {key: status[key] for key in (
    'state', 'completedCases', 'totalCases', 'completedBatches', 'totalBatches',
    'transportVerified', 'calculationVerified', 'reviewCoverageVerified',
    'hasFailures', 'caseVerdict', 'retryStatistik', 'artifactDirectory')}
summary['model'] = json.loads((base / 'llm-public-configuration.json').read_text(encoding='utf-8'))['model']
summary['selectedCaseIds'] = manifest['selectedCaseIds']
summary['sources'] = manifest['sources']
(base / 'llm-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({key: summary[key] for key in ('state', 'completedCases', 'completedBatches', 'model', 'retryStatistik')}))
