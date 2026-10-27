"""Eigener Oracle-Schemabesitz; Vorbereitung und Ausführung sind getrennte Schritte."""
from pathlib import Path
import datetime, json, os, re, shutil, subprocess, sys, tempfile, uuid, xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = Path(__file__).resolve().parent
STATE_POINTER = EVIDENCE / 'oracle-state-path.txt'

def sql(text, filename, timeout=90):
    script = 'whenever oserror exit failure rollback\nwhenever sqlerror exit failure rollback\nset echo off define off feedback off heading off pages 0 lines 240\nset sqlblanklines on\n' + text + '\nexit\n'
    result = subprocess.run(['docker','exec','-i','-e','NLS_LANG=.AL32UTF8','lmg-oracle','sqlplus','-s','/ as sysdba'],
                            input=script.encode('utf-8'), stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    output = result.stdout.decode('utf-8',errors='replace')
    (EVIDENCE / filename).write_text(output,encoding='utf-8')
    if result.returncode: raise RuntimeError(f'SQL*Plus fehlgeschlagen: {filename}; Exit {result.returncode}')
    return output

def load():
    private = Path(STATE_POINTER.read_text(encoding='utf-8').strip()).resolve()
    if private.parent.parent != Path(tempfile.gettempdir()).resolve() or not private.parent.name.startswith('testsweet-fix-schema-'):
        raise RuntimeError('Fremde Zustandsdatei')
    state = json.loads(private.read_text(encoding='utf-8'))
    if not re.fullmatch(r'TSFIX_[A-F0-9]{16}',state['schema']): raise RuntimeError('Fremder Schemaname')
    if state['repository'] != str(ROOT): raise RuntimeError('Fremdes Repository')
    return private,state

def prepare():
    if STATE_POINTER.exists(): raise RuntimeError('Vorbereiteter Schemazustand existiert bereits')
    state = dict(schema='TSFIX_'+uuid.uuid4().hex[:16].upper(), password='Fix'+uuid.uuid4().hex,
                 nonce=uuid.uuid4().hex, repository=str(ROOT), preparedAt=datetime.datetime.now(datetime.timezone.utc).isoformat())
    reports=list((ROOT/'testsweet/target/surefire-reports').glob('TEST-*.xml'))
    if not reports: raise RuntimeError('Kein vorläufiger Abhängigkeitsklassenpfad vorhanden')
    for report in reports:
        props=ET.parse(report).getroot().find('properties')
        if props is not None:
            classpaths=[p.attrib['value'] for p in props if p.attrib['name']=='java.class.path']
            if classpaths:
                state['classpath']=classpaths[0]
                break
    if 'classpath' not in state: raise RuntimeError('Kein XML mit Abhängigkeitsklassenpfad vorhanden')
    private = Path(tempfile.mkdtemp(prefix='testsweet-fix-schema-')).resolve() / 'private.json'
    private.write_text(json.dumps(state),encoding='utf-8')
    STATE_POINTER.write_text(str(private),encoding='utf-8')
    name,password,nonce=state['schema'],state['password'],state['nonce']
    # SQL*Plus kann ein Semikolon am Ende reiner Kommentarzeilen als Terminator
    # des laufenden CREATE-Statements behandeln. Nur diese Kommentare auslassen;
    # sämtliche ausführbaren DDL-Zeilen bleiben unverändert.
    ddl = '\n'.join(line for name in ('oracle_schema.sql','lmg_schema.sql')
                    for line in (ROOT/'ddl'/name).read_text(encoding='utf-8').splitlines()
                    if not line.lstrip().startswith('--'))
    sql(f"""alter session set container=FREEPDB1;
declare n number;
begin
 select count(*) into n from dba_users where username='{name}';
 if n<>0 then raise_application_error(-20001,'Testschema existiert bereits'); end if;
end;
/
create user {name} identified by {password} default tablespace USERS quota unlimited on USERS;
grant create session, create table, create sequence, create trigger to {name};
create table {name}.TS_REVIEW_OWNER (NONCE varchar2(32) primary key, CREATED_AT timestamp default systimestamp);
insert into {name}.TS_REVIEW_OWNER (NONCE) values ('{nonce}');
commit;
connect {name}/{password}@//localhost:1521/FREEPDB1
"""+ddl+"""
insert into SATZSTAND (GUELTIG_AB,RBS_1,RBS_2,RBS_3,RBS_4,RBS_5,RBS_6)
values ('2026-01',563,506,451,471,390,357);
commit;
select 'PREPARED:'||user from dual;
""",'oracle-prepare.txt')
    (EVIDENCE/'oracle-schema.json').write_text(json.dumps({k:v for k,v in state.items() if k not in ('password','classpath')},indent=2)+'\n',encoding='utf-8')
    print('PREPARED '+name+'; noch keine Simulatorausführung')

def run():
    private,state=load()
    runtime=private.parent/'classes';runtime.mkdir(exist_ok=False)
    cp=state['classpath']
    subprocess.run(['javac','--release','21','-encoding','UTF-8','-cp',cp,'-d',str(runtime),str(EVIDENCE/'IsolatedSimulatorProbe.java')],cwd=ROOT,check=True)
    env=os.environ.copy();env.update(TX_URL='jdbc:oracle:thin:@//localhost:1521/FREEPDB1',TX_USER=state['schema'],TX_PASS=state['password'])
    for category in ('ECHT','RECHNER'):
        filename='oracle-'+category.lower()
        with (EVIDENCE/(filename+'.log')).open('wb') as out:
            result=subprocess.run(['java','-cp',str(runtime)+os.pathsep+cp,'de.allgemein.testsweet.IsolatedSimulatorProbe',category,str(EVIDENCE/(filename+'.json'))],cwd=ROOT,env=env,stdout=out,stderr=subprocess.STDOUT,timeout=300)
        if result.returncode: raise RuntimeError(f'{category}: Exit {result.returncode}, siehe {filename}.log')
        summary=json.loads((EVIDENCE/(filename+'.json')).read_text(encoding='utf-8'))
        lifecycle=Path(summary['runDirectory'])/'run-state.json'
        lifecycle=json.loads(lifecycle.read_text(encoding='utf-8'))
        if not lifecycle['success'] or lifecycle['state']!='PASS' or not lifecycle['buildEvidence']['sourceArtifactsMatch']:
            raise RuntimeError(category+': Lauf-/Buildbindung ungültig')
        (EVIDENCE/(filename+'-lifecycle.json')).write_text(json.dumps(lifecycle,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(category+': '+str(lifecycle['verifiedCases'])+'/'+str(lifecycle['totalCases'])+' verifiziert; sourceArtifactsMatch=true')

def cleanup():
    private,state=load();name,nonce=state['schema'],state['nonce']
    sql(f"""alter session set container=FREEPDB1;
declare n number; owner_nonce varchar2(32);
begin
 select count(*) into n from dba_users where username='{name}';
 if n<>1 then raise_application_error(-20002,'Eigenes Testschema nicht eindeutig vorhanden'); end if;
 select NONCE into owner_nonce from {name}.TS_REVIEW_OWNER;
 if owner_nonce<>'{nonce}' then raise_application_error(-20003,'Herkunftsmarker stimmt nicht'); end if;
 execute immediate 'drop user {name} cascade';
end;
/
select 'REMOVED:{name}:'||count(*) from dba_users where username='{name}';
""",'oracle-cleanup.txt')
    private_root=private.parent.resolve()
    if private_root.parent!=Path(tempfile.gettempdir()).resolve() or not private_root.name.startswith('testsweet-fix-schema-'):
        raise RuntimeError('Fremdes Cleanupziel')
    shutil.rmtree(private_root)
    STATE_POINTER.unlink()
    print('REMOVED '+name+'; exakter Schemaname und eigener DB-Herkunftsmarker vor DROP geprüft')

if __name__=='__main__':
    {'prepare':prepare,'run':run,'cleanup':cleanup}[sys.argv[1]]()
