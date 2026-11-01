#!/usr/bin/env python3
"""Exercise maintenance only on the readiness runner's explicitly disposable database."""
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import uuid
container=os.environ['TEST_DB_CONTAINER'];assert re.fullmatch(r'[a-f0-9]{64}',container) and os.environ.get('TEST_DISPOSABLE_DATABASE')=='browser_test'
root=Path.cwd();output=root/'target/verification-sprint5';backups=output/'backups';backups.mkdir(mode=0o700,exist_ok=True)
def sql(query,target=container):
 result=subprocess.run(['docker','exec','-i',target,'psql','-X','-v','ON_ERROR_STOP=1','-U','browser_test','-d','browser_test','-tA'],input=query.encode(),capture_output=True)
 if result.returncode:raise RuntimeError('Recovery fixture command failed')
 return result.stdout.decode().strip()
def run(*args,check=True):return subprocess.run(['python3','deploy/database-maintenance.py','--container',container,'--user','browser_test','--database','browser_test',*args],capture_output=True,text=True,check=check)
# Only an explicit, old, already-decided fixture is eligible. Snapshot/financial checksum survives.
ticket_id=str(uuid.uuid4())
sql(f"INSERT INTO public.tickets(id,ticket_code,request_key,request_hash,session_hash,bettor_name,bettor_phone,stake_amount,total_odds,potential_return,status,expires_at,created_at,handled_by,handled_at,operator_note) VALUES('{ticket_id}','TF-RETENTION-S5',gen_random_uuid(),repeat('a',64),repeat('b',64),'Pessoa antiga fictícia','5511999999999',10,1.8,18,'CONFIRMED',now()-interval '91 days'+interval '30 minutes',now()-interval '91 days',(SELECT id FROM public.admins WHERE username='browser_operator'),now()-interval '91 days'+interval '5 minutes','Nota antiga fictícia');")
ids=output/'reviewed-retention-ids';ids.touch(mode=0o600);ids.write_text(ticket_id+'\n')
financial_query="SELECT md5(string_agg(id::text||':'||status||':'||stake_amount::text||':'||total_odds::text||':'||potential_return::text||':'||coalesce(handled_by::text,''),',' ORDER BY id)) FROM public.tickets;"
financial=sql(financial_query)
try:
 assert run('anonymize','--ticket-ids-file',str(ids),check=False).returncode!=0
 recent=sql("SELECT id FROM public.tickets WHERE ticket_code='TF-RESTART-S5'")
 ids.write_text(ticket_id+'\n'+recent+'\n')
 assert run('anonymize','--ticket-ids-file',str(ids),'--reviewed-retention','--apply',check=False).returncode!=0
 assert sql(financial_query)==financial
 ids.write_text("not-a-uuid; DROP TABLE tickets;\n")
 assert run('anonymize','--ticket-ids-file',str(ids),'--reviewed-retention','--apply',check=False).returncode!=0
 ids.write_text(ticket_id+'\n')
 assert json.loads(run('anonymize','--ticket-ids-file',str(ids),'--reviewed-retention').stdout)['mode']=='dry-run'
 assert sql(f"SELECT bettor_name FROM public.tickets WHERE id='{ticket_id}'")=='Pessoa antiga fictícia'
 assert json.loads(run('anonymize','--ticket-ids-file',str(ids),'--reviewed-retention','--apply').stdout)['changed']==1
 assert json.loads(run('anonymize','--ticket-ids-file',str(ids),'--reviewed-retention','--apply').stdout)['changed']==0
 assert sql(financial_query)==financial
 assert sql(f"SELECT bettor_name||':'||bettor_phone||':'||(operator_note IS NULL)::text FROM public.tickets WHERE id='{ticket_id}'")=='Anonimizado:Removido:true'
finally:ids.unlink(missing_ok=True)
old=backups/'trevo-20000101T000000000000Z.dump';old.touch(mode=0o600);os.utime(old,(time.time()-8*86400,)*2)
started=time.monotonic();result=json.loads(run('backup','--directory',str(backups),'--prune').stdout);backup_seconds=time.monotonic()-started
assert result['expiredFilesRemoved']==1 and not old.exists();dump=Path(result['backup']);assert dump.stat().st_mode&0o077==0
# Current container must be refused as a restore target because it is not empty.
assert run('restore','--file',str(dump),check=False).returncode!=0
restored=None
try:
 env=os.environ.copy();env.update({'POSTGRES_USER':'browser_test','POSTGRES_DB':'browser_test','POSTGRES_PASSWORD':secrets.token_urlsafe(32)})
 restored=subprocess.check_output(['docker','run','--rm','-d','-e','POSTGRES_USER','-e','POSTGRES_DB','-e','POSTGRES_PASSWORD','postgres:16-alpine'],env=env,text=True).strip()
 for _ in range(60):
  if subprocess.run(['docker','exec',restored,'pg_isready','-U','browser_test'],capture_output=True).returncode==0:break
  time.sleep(.5)
 else:raise RuntimeError('Restore database did not start')
 start=time.monotonic()
 subprocess.run(['python3','deploy/database-maintenance.py','--container',restored,'--user','browser_test','--database','browser_test','restore','--file',str(dump)],check=True,capture_output=True)
 restore_seconds=time.monotonic()-start
 assert sql(financial_query,restored)==financial
 for table in ['admins','matches','markets','market_selections','tickets','ticket_items']:
  assert sql('SELECT count(*) FROM public.'+table,restored)==sql('SELECT count(*) FROM public.'+table)
  checksum="SELECT md5(coalesce(string_agg(md5(row_to_json(t)::text),'' ORDER BY id),'')) FROM public."+table+" t;"
  assert sql(checksum,restored)==sql(checksum)
 assert sql('SHOW TIME ZONE;',restored)=='UTC'
 report={'scope':'synthetic PostgreSQL 16 dump/isolated restore, not hosted disaster recovery','backupSeconds':round(backup_seconds,3),'restoreSeconds':round(restore_seconds,3),'dumpBytes':dump.stat().st_size,'retentionDays':7,'anonymizationDryRunAndIdempotencyVerified':True,'nonemptyRestoreRefused':True,'financialChecksumAndSixTableCountsPreserved':True}
 (output/'recovery-report.json').write_text(json.dumps(report,indent=2));print('PASS: recovery and reviewed retention:',json.dumps(report))
finally:
 if restored:subprocess.run(['docker','stop',restored],check=True,capture_output=True)
# One-off access maintenance uses real BCrypt; source app remains running, no public endpoint.
for action in ['reset-password','disable']:
 env=os.environ.copy();env.update({'TREVO_OPERATOR_ACTION':action,'TREVO_OPERATOR_USERNAME':'browser_operator','TREVO_OPERATOR_PASSWORD':secrets.token_urlsafe(24)})
 old_hash=sql("SELECT password_hash FROM public.admins WHERE username='browser_operator'")
 process=subprocess.run(['/usr/lib/jvm/java-17-openjdk-amd64/bin/java','-jar','target/trevo4folhas.jar','--operator-maintenance'],env=env,capture_output=True,text=True,timeout=45)
 assert process.returncode==0,'One-off operator maintenance failed (private output suppressed)'
 assert env['TREVO_OPERATOR_PASSWORD'] not in process.stdout+process.stderr
 if action=='reset-password':assert sql("SELECT password_hash FROM public.admins WHERE username='browser_operator'")!=old_hash
 else:assert sql("SELECT active::text FROM public.admins WHERE username='browser_operator'")=='false'
print('PASS: one-shot BCrypt password rotation and operator deactivation without exposing credentials.')
