#!/usr/bin/env python3
"""Isolated Docker image/runtime-role/persistence validation; never uses the local Compose volume."""
import http.cookiejar
import json
import os
import secrets
import subprocess
import time
import urllib.request
import urllib.parse
import uuid
from html.parser import HTMLParser
from pathlib import Path
image=os.environ.get('TEST_IMAGE','trevo4folhas-app:sprint5')
assert image=='trevo4folhas-app:sprint5'
suffix=uuid.uuid4().hex[:12];network='trevo-s5-test-'+suffix;volume=network+'-data';db=None;app=None
output=Path('target/verification-sprint5');output.mkdir(parents=True,exist_ok=True)
env=os.environ.copy();env.update({'POSTGRES_USER':'browser_test','POSTGRES_DB':'browser_test','POSTGRES_PASSWORD':secrets.token_urlsafe(32),'DB_USERNAME':'browser_test','DB_PASSWORD':'','DB_URL':'jdbc:postgresql://db:5432/browser_test','BANK_WHATSAPP_NUMBER':'5511999999999','BANK_TIME_ZONE':'America/Sao_Paulo','MIN_STAKE_AMOUNT':'5.00','MAX_STAKE_AMOUNT':'500.00','MAX_SELECTIONS':'10','MAX_POTENTIAL_RETURN':'10000.00','TICKET_VALIDITY_MINUTES':'30','ADMIN_BOOTSTRAP_USERNAME':'image_operator','ADMIN_BOOTSTRAP_DISPLAY_NAME':'Operador fictício','ADMIN_BOOTSTRAP_PASSWORD':secrets.token_urlsafe(32),'SPRING_PROFILES_ACTIVE':'local','MANUAL_PRICE_MAX_AGE_SECONDS':'3600','MANUAL_SCHEDULE_MAX_AGE_SECONDS':'3600','MANUAL_STATUS_MAX_AGE_SECONDS':'3600','SPORTS_DATA_ENABLED':'false','SPORTS_DATA_SYNC_ENABLED':'false'})
env['DB_PASSWORD']=env['POSTGRES_PASSWORD']
bootstrap_password=env['ADMIN_BOOTSTRAP_PASSWORD']
def run(args,**kw):return subprocess.run(args,env=env,capture_output=True,check=True,**kw)
def sql(query):return run(['docker','exec','-i',db,'psql','-X','-v','ON_ERROR_STOP=1','-U','browser_test','-d','browser_test','-tA'],input=query.encode()).stdout.decode().strip()
def start_db():
 container=run(['docker','run','--rm','-d','--network',network,'--network-alias','db','-v',volume+':/var/lib/postgresql/data','-e','POSTGRES_USER','-e','POSTGRES_DB','-e','POSTGRES_PASSWORD','postgres:16-alpine']).stdout.decode().strip()
 for _ in range(60):
  if subprocess.run(['docker','exec',container,'pg_isready','-U','browser_test'],capture_output=True).returncode==0:return container
  time.sleep(.5)
 raise RuntimeError('Image test database did not start')
def start_app():
 keys=['DB_URL','DB_USERNAME','DB_PASSWORD','BANK_WHATSAPP_NUMBER','BANK_TIME_ZONE','MIN_STAKE_AMOUNT','MAX_STAKE_AMOUNT','MAX_SELECTIONS','MAX_POTENTIAL_RETURN','TICKET_VALIDITY_MINUTES','ADMIN_BOOTSTRAP_USERNAME','ADMIN_BOOTSTRAP_DISPLAY_NAME','ADMIN_BOOTSTRAP_PASSWORD','SPRING_PROFILES_ACTIVE','SPRING_FLYWAY_ENABLED','MANUAL_PRICE_MAX_AGE_SECONDS','MANUAL_SCHEDULE_MAX_AGE_SECONDS','MANUAL_STATUS_MAX_AGE_SECONDS','SPORTS_DATA_ENABLED','SPORTS_DATA_SYNC_ENABLED']
 args=['docker','run','--rm','-d','--network',network,'-p','127.0.0.1::8080']
 for key in keys:
  if key in env:args.extend(['-e',key])
 args.append(image);container=run(args).stdout.decode().strip()
 ports=json.loads(run(['docker','inspect','--format','{{json .NetworkSettings.Ports}}',container]).stdout)
 base='http://127.0.0.1:'+ports['8080/tcp'][0]['HostPort']
 for _ in range(120):
  try:
   with urllib.request.urlopen(base+'/actuator/health',timeout=1) as response:
    if json.load(response)=={'status':'UP'}:return container,base
  except OSError:pass
  time.sleep(.5)
 raise RuntimeError('Image application did not become healthy')
class Token(HTMLParser):
 value=None
 def handle_starttag(self,tag,attrs):
  values=dict(attrs)
  if tag=='input' and values.get('name')=='_csrf':self.value=values.get('value')
try:
 run(['docker','network','create',network]);run(['docker','volume','create',volume]);db=start_db();app,base=start_app()
 actor=''
 for _ in range(40):
  actor=sql("SELECT id FROM public.admins WHERE username='image_operator'")
  if actor:break
  time.sleep(.2)
 assert actor and sql('SHOW TIME ZONE')=='UTC'
 run(['docker','stop',app]);app=None
 # This randomly generated password belongs only to the disposable role fixture.
 runtime_password=secrets.token_urlsafe(32)
 template=Path('deploy/runtime-role.sql.example').read_text().replace('YOUR_DATABASE','browser_test')
 sql(template+"\nALTER ROLE trevo_runtime PASSWORD '"+runtime_password+"';")
 sql("BEGIN; SET ROLE trevo_runtime; SELECT id FROM public.admins FOR SHARE; ROLLBACK;")
 assert sql("SELECT rolsuper::text||':'||rolcreatedb::text||':'||rolcreaterole::text FROM pg_roles WHERE rolname='trevo_runtime'")=='false:false:false'
 sql("""
 BEGIN;
 INSERT INTO public.matches(competition,home_team,away_team,match_date,status,source_namespace,external_fixture_id,provider_status,manual_reviewed_at) VALUES('TESTE imagem','Equipe A','Equipe B',now()+interval '1 day','OPEN','MANUAL','image-fixture','OPEN',now());
 INSERT INTO public.markets(match_id,source_namespace,bookmaker_id,external_market_id,market_type,display_name,period,line,participant,specifiers_key,rule_key,rule_text,provider_status,curated,manual_reviewed_at) SELECT id,'MANUAL','NONE','NONE','1X2','Resultado fictício','FT','NONE','NONE','NONE','RULE','Regra fictícia','OPEN',true,now() FROM public.matches;
 INSERT INTO public.market_selections(market_id,external_selection_key,selection_code,display_name,odds,provider_status,curated,manual_reviewed_at) SELECT id,'HOME','HOME','Casa',1.8,'OPEN',true,now() FROM public.markets;
 COMMIT;
 """)
 selection=sql('SELECT id FROM public.market_selections')
 env.update({'DB_USERNAME':'trevo_runtime','DB_PASSWORD':runtime_password,'SPRING_FLYWAY_ENABLED':'false','ADMIN_BOOTSTRAP_USERNAME':'','ADMIN_BOOTSTRAP_DISPLAY_NAME':'','ADMIN_BOOTSTRAP_PASSWORD':''})
 app,base=start_app()
 uid=run(['docker','exec',app,'id','-u']).stdout.decode().strip();assert uid=='10001'
 run(['docker','exec',app,'sh','-c','! command -v node && ! command -v mvn && ! command -v javac && test ! -e /app/.env'])
 client=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
 def api(path,body=None,headers=None):
  h={'Accept':'application/json'};h.update(headers or {})
  if body is not None:h['Content-Type']='application/json'
  with client.open(urllib.request.Request(base+path,headers=h,data=None if body is None else json.dumps(body).encode()),timeout=10) as response:return json.load(response)
 csrf=api('/api/session');ticket=api('/api/tickets',{'bettorName':'Pessoa fictícia de imagem','bettorPhone':'11999999999','stakeAmount':'10.00','rulesAccepted':True,'adultDeclared':True,'rulesVersion':'PREVIEW-2026-10-31-V1','items':[{'selectionId':selection,'matchVersion':1,'marketVersion':1,'selectionVersion':1}]},{csrf['headerName']:csrf['token'],'Idempotency-Key':str(uuid.uuid4())})
 with client.open(base+'/admin/login') as response:token=Token();token.feed(response.read().decode())
 # Bootstrap used the owner's one-time random password; no default credential exists.
 # Saved privately in this process before bootstrap was disabled.
 login_data=urllib.parse.urlencode({'username':'image_operator','password':bootstrap_password,'_csrf':token.value}).encode()
 with client.open(base+'/admin/login',login_data) as response:assert response.url.endswith('/admin')
 csrf=api('/api/session');decided=api('/api/admin/tickets/'+ticket['id']+'/confirm',{'ticketVersion':1,'pixReceiptChecked':True},{csrf['headerName']:csrf['token']});assert decided['current']['ticket']['status']=='CONFIRMED'
 before=sql("SELECT id||':'||status||':'||potential_return::text FROM public.tickets")
 run(['docker','stop',app]);app=None;run(['docker','stop',db]);db=None
 db=start_db();app,base=start_app();assert sql("SELECT id||':'||status||':'||potential_return::text FROM public.tickets")==before
 assert sql("SELECT id FROM public.admins WHERE username='image_operator'")==actor
 report={'image':image,'runtimeUid':uid,'noNodeMavenJavacOrLocalEnv':True,'runtimeRoleIsNotSuperuser':True,'runtimeFlywayDisabledAfterOwnerMigration':True,'publicHealthMinimal':True,'issueAndConfirmWithRestrictedRole':True,'namedVolumeSurvivesDatabaseRecreation':True,'publicDatabasePort':False}
 (output/'image-report.json').write_text(json.dumps(report,indent=2));print('PASS: image runtime and persistence:',json.dumps(report))
finally:
 for container in [app,db]:
  if container:subprocess.run(['docker','stop',container],capture_output=True)
 subprocess.run(['docker','volume','rm',volume],capture_output=True)
 subprocess.run(['docker','network','rm',network],capture_output=True)
