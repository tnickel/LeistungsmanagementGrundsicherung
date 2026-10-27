"""HTML-Tokenizer-Gegenprobe: Entities in href erzeugen keine neuen Attribute."""
import json
from html.parser import HTMLParser
from pathlib import Path

class Links(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.tags = []
    def handle_starttag(self, tag, attrs):
        self.tags.append((tag, dict(attrs)))

data = json.loads(Path(__file__).with_name('gui-probes.json').read_text(encoding='utf-8-sig'))
for case in data['linkification']:
    parser = Links()
    parser.feed(case['html'])
    assert all(tag in ('a', 'br') for tag, _ in parser.tags), parser.tags
    for tag, attrs in parser.tags:
        if tag == 'a':
            assert set(attrs) == {'href', 'target', 'rel'}, attrs
            assert attrs['href'].startswith('https://'), attrs
            assert attrs['target'] == '_blank' and attrs['rel'] == 'noopener', attrs
print('PASS: 6 Linktexte; nur a/br; keine eingeschleusten Tags oder Ereignisattribute.')
