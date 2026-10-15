import json
from pathlib import Path
import fitz

base = Path(__file__).resolve().parent
summary = json.loads((base / 'simulator-summary.json').read_text(encoding='utf-8'))
run = next(Path(row['directory']) for row in summary['runs'] if row['name'] == 'run-NEGATIV')
pdf = fitz.open(run / 'testdokumentation.pdf')
for index, page in enumerate(pdf):
    if 'DE00370400440532013000' in page.get_text():
        samples = []
        for sample in range(index, min(index + 2, len(pdf))):
            output = base / f'sepa-negativ-seite-{sample+1}.png'
            pdf[sample].get_pixmap(matrix=fitz.Matrix(1.3, 1.3)).save(output)
            samples.append({'page': sample+1, 'image': str(output), 'text': pdf[sample].get_text()})
        (base / 'sepa-pdf-inspection.json').write_text(json.dumps({
            'pdf': str(run / 'testdokumentation.pdf'), 'pages': len(pdf), 'samples': samples
        }, ensure_ascii=False, indent=2), encoding='utf-8')
        print(json.dumps({'pdf': str(run / 'testdokumentation.pdf'), 'samplePages': [p['page'] for p in samples]}))
        break
else:
    raise AssertionError('Synthetischer IBAN-Rohwert fehlt im PDF')
