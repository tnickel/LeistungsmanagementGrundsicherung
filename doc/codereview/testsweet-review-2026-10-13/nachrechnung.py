import re, sys, json
from decimal import Decimal as D, ROUND_HALF_UP
from pathlib import Path
rbs={i:D(v) for i,v in enumerate(('563','506','451','471','390','357'),1)}
ww={i:D(v) for i,v in enumerate(('.023','.023','0','.014','.012','.008'),1)}
q=lambda v:v.quantize(D('.01'),rounding=ROUND_HALF_UP)
text=Path(sys.argv[1]).read_text(encoding='utf-8')
errors=[]; blocks=people=checks=0; with_blocks=set(); branches=set()
def check(tf,key,expected,values):
    global checks
    checks+=1
    if key not in values: errors.append(f'TF {tf}: missing {key}')
    elif D(values[key]) != expected: errors.append(f'TF {tf}: {key}: expected {expected}, got {values[key]}')
for tftext in re.split(r'(?=^## TF )',text,flags=re.M):
    m=re.match(r'## TF (\d+) (\S+)',tftext)
    if not m: continue
    tf,typ=m.groups()
    for block in re.split(r'(?=^block: )',tftext,flags=re.M):
        if not block.startswith('block: '): continue
        blocks+=1; with_blocks.add(tf)
        vals=dict(re.findall(r'^(\w+)\s*=\s*(-?\d+(?:\.\d+)?)\s*$',block,re.M))
        monthmatch=re.search(r'^zahlungsmonat = (\S+)',block,re.M)
        month=monthmatch.group(1) if monthmatch else None
        total=income=D(0); adults=children=0
        inputs=re.findall(r'^p(\d+)_eingaben: (.+)$',block,re.M)
        if not inputs: errors.append(f'TF {tf}: no persons')
        for pn,line in inputs:
            people+=1; f=dict(item.split('=',1) for item in line.split(', ')); stage=int(f['rbs']); rb=rbs[stage]
            branches.add(f'RBS{stage}')
            adults+=stage<=3; children+=stage>3
            g=q(rb*D('.17')) if f['merkzeichen_g']=='true' else D(0)
            pregnant=f['schwanger']=='true'
            if month and f['entbindungsmonat']!='-' and month>f['entbindungsmonat']: pregnant=False
            preg=q(rb*D('.17')) if pregnant else D(0)
            u7,u16,minor=(int(f[k]) for k in ('kinder_u7','kinder_u16','kinder_minderjaehrig'))
            minor=max(u7,u16,minor)
            rate=D('.36') if u7 or 2<=max(u7,u16)<=3 else min(D('.12')*minor,D('.60'))
            single=q(rbs[1]*rate) if minor else D(0)
            warm=q(rb*ww[stage]) if f['warmwasser']=='true' else D(0)
            if g: branches.add('G')
            if preg: branches.add('Schwangerschaft')
            if f['entbindungsmonat']!='-': branches.add('Entbindungsmonat')
            if single: branches.add('Alleinerziehend')
            if warm: branches.add('Warmwasser')
            if g+preg+single>rb: branches.add('Abs6-Kappung')
            rent=sum(D(f[k]) for k in ('kdu_miete','kdu_betrieb','kdu_heizung'))
            gross=D(f['erwerb']); allowance=min(q(gross*D('.30')),q(rbs[1]/2))
            if gross: branches.add('Erwerb')
            deductions=sum(D(f[k]) for k in ('abz_steuern','abz_sv','abz_vers','abz_erwerb'))
            inc=max(D(0),D(f['rente'])+gross-deductions-allowance)
            demand=rb+min(rb,g+preg+single)+warm+rent
            expected={'regelbedarf':rb,'mehrbedarf_g':g,'mehrbedarf_schwanger':preg,'mehrbedarf_alleinerziehend':single,'mehrbedarf_warmwasser':warm,'kdu':rent,'gesamtbedarf':demand,'freibetrag_erwerb':allowance,'absetzbetaetrag':deductions,'anrechenbar':inc,'zahlbetrag':demand-inc}
            for key,value in expected.items(): check(tf,f'p{pn}_{key}',value,vals)
            total+=demand; income+=inc
        protected=D(10000)*adults+D(500)*children if adults else D(10000)
        asset=max(D(0),D(vals['vermoegen'])-protected)
        if asset: branches.add('Vermoegenseinsatz')
        amount=total-income-asset
        for key,value in {'schonvermoegen':protected,'bg_gesamtbedarf':total,'bg_anrechenbar':income,'bg_verwertbares_vermoegen':asset,'bg_zahlbetrag':amount}.items(): check(tf,key,value,vals)
        if 'budget_start' in vals: check(tf,'budget_rest_erwartet',D(vals['budget_start'])-amount,vals)
result={'source':str(Path(sys.argv[1]).resolve()),'cases_with_calculation':len(with_blocks),'blocks':blocks,'persons':people,'numeric_comparisons':checks,'branches':sorted(branches),'errors':errors}
print(json.dumps(result,ensure_ascii=False,indent=2))
sys.exit(bool(errors))
