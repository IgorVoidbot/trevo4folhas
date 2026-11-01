#!/usr/bin/env python3
"""Synthetic local-only benchmark. Requires the disposable verification runner's environment."""
from concurrent.futures import ThreadPoolExecutor
from html.parser import HTMLParser
import http.cookiejar
import json
import os
from pathlib import Path
import platform
import re
import statistics
import subprocess
import threading
import time
import urllib.request
import urllib.parse
import uuid

base=os.environ['TEST_BASE_URL']; container=os.environ['TEST_DB_CONTAINER']
assert urllib.parse.urlparse(base).hostname=='127.0.0.1'
assert os.environ.get('TEST_DISPOSABLE_DATABASE')=='browser_test' and re.fullmatch(r'[a-f0-9]{64}',container)
output=Path('target/verification-sprint5');output.mkdir(parents=True,exist_ok=True)
def sql(query):
    result=subprocess.run(['docker','exec','-i',container,'psql','-X','-v','ON_ERROR_STOP=1','-U','browser_test','-d','browser_test','-tA'],input=query.encode(),capture_output=True)
    if result.returncode:raise RuntimeError('Synthetic reference data command failed')
    return result.stdout.decode().strip()
# Reference data exists only inside the runner's disposable PostgreSQL 16.
sql("""
BEGIN;
DELETE FROM public.ticket_items;DELETE FROM public.tickets;DELETE FROM public.market_selections;DELETE FROM public.markets;DELETE FROM public.matches;
CREATE TEMP TABLE bench_offers(n int,match_id uuid,market_id uuid,selection_id uuid);
INSERT INTO bench_offers SELECT i,gen_random_uuid(),gen_random_uuid(),gen_random_uuid() FROM generate_series(1,100) i;
INSERT INTO public.matches(id,competition,home_team,away_team,match_date,status,source_namespace,external_fixture_id,provider_status,manual_reviewed_at)
SELECT match_id,'TESTE carga fictícia','Equipe fictícia A '||n,'Equipe fictícia B '||n,now()+interval '1 day','OPEN','MANUAL',match_id::text,'OPEN',now() FROM bench_offers;
INSERT INTO public.markets(id,match_id,source_namespace,bookmaker_id,external_market_id,market_type,display_name,period,line,participant,specifiers_key,rule_key,rule_text,provider_status,curated,manual_reviewed_at)
SELECT market_id,match_id,'MANUAL','NONE','NONE','1X2','Resultado fictício','FT','NONE','NONE','NONE','REGULATION_TIME','Tempo regulamentar fictício.','OPEN',true,now() FROM bench_offers;
INSERT INTO public.market_selections(id,market_id,external_selection_key,selection_code,display_name,odds,provider_status,curated,manual_reviewed_at)
SELECT selection_id,market_id,'HOME','HOME','Vitória fictícia da casa',1.8,'OPEN',true,now() FROM bench_offers;
CREATE TEMP TABLE bench_tickets(n int,id uuid);
INSERT INTO bench_tickets SELECT i,gen_random_uuid() FROM generate_series(1,10000) i;
INSERT INTO public.tickets(id,ticket_code,request_key,request_hash,session_hash,bettor_name,bettor_phone,stake_amount,total_odds,potential_return,status,expires_at,handled_by,handled_at,created_at,rules_version)
SELECT id,'TF-BENCH-'||n,gen_random_uuid(),repeat('a',64),repeat('b',64),'Pessoa fictícia de carga','5511999999999',10,1.8,18,'CONFIRMED',now()-interval '23 hours 30 minutes',(SELECT id FROM public.admins WHERE username='browser_operator'),now()-interval '23 hours 50 minutes',now()-interval '1 day','PREVIEW-2026-10-31-V1' FROM bench_tickets;
INSERT INTO public.ticket_items(ticket_id,match_id,market_selection_id,selected_odds,match_version_snapshot,market_version_snapshot,selection_version_snapshot,market_snapshot,selection_snapshot,selection_code_snapshot,period_snapshot,line_snapshot,participant_snapshot,rule_snapshot,provider_snapshot,bookmaker_snapshot,external_fixture_id_snapshot,external_market_id_snapshot,external_selection_key_snapshot,specifiers_snapshot,competition_snapshot,home_team_snapshot,away_team_snapshot,match_date_snapshot)
SELECT t.id,m.id,s.id,s.odds,1,1,1,k.display_name,s.display_name,s.selection_code,k.period,k.line,k.participant,k.rule_text,k.source_namespace,k.bookmaker_id,m.external_fixture_id,k.external_market_id,s.external_selection_key,k.specifiers_key,m.competition,m.home_team,m.away_team,m.match_date
FROM bench_tickets t JOIN bench_offers b ON b.n=((t.n-1)%100)+1 JOIN public.matches m ON m.id=b.match_id JOIN public.markets k ON k.id=b.market_id JOIN public.market_selections s ON s.id=b.selection_id;
ANALYZE public.matches;ANALYZE public.markets;ANALYZE public.market_selections;ANALYZE public.tickets;ANALYZE public.ticket_items;
COMMIT;
""")
offers=json.loads(sql("SELECT json_agg(row_to_json(x)) FROM (SELECT s.id AS selection_id,m.id AS match_id FROM public.market_selections s JOIN public.markets k ON k.id=s.market_id JOIN public.matches m ON m.id=k.match_id ORDER BY m.id) x;"))
class Meta(HTMLParser):
    token=None
    def handle_starttag(self,tag,attrs):
        values=dict(attrs)
        if tag=='input' and values.get('name')=='_csrf':self.token=values.get('value')

def client():return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def request(opener,path,body=None,headers=None):
    h={'Accept':'application/json'};h.update(headers or {})
    if body is not None:h['Content-Type']='application/json'
    req=urllib.request.Request(base+path,data=None if body is None else json.dumps(body).encode(),headers=h)
    start=time.perf_counter()
    try:
        with opener.open(req,timeout=20) as response:return json.load(response),(time.perf_counter()-start)*1000,response.status
    except Exception:return None,(time.perf_counter()-start)*1000,0
admin=client()
with admin.open(base+'/admin/login') as response:
    meta=Meta();meta.feed(response.read().decode())
login=urllib.parse.urlencode({'username':os.environ['TEST_ADMIN_USERNAME'],'password':os.environ['TEST_ADMIN_PASSWORD'],'_csrf':meta.token}).encode()
with admin.open(base+'/admin/login',login) as response:assert response.url.endswith('/admin')
admin_csrf,_,_=request(admin,'/api/session')
anonymous=[client() for _ in range(50)]
tokens=[request(c,'/api/session')[0] for c in anonymous]
# Warm every measured route, including DTO validation, issuance and confirmation.
# These explicitly synthetic warmup rows are removed before measuring the 10k baseline.
warm_ids=[]
for i in range(100):
    request(anonymous[0],'/api/matches');request(anonymous[0],'/api/matches/'+offers[i%100]['match_id'])
    body={'bettorName':'Pessoa fictícia de aquecimento','bettorPhone':'11999999999','stakeAmount':'10.00','rulesAccepted':True,'adultDeclared':True,'rulesVersion':'PREVIEW-2026-10-31-V1','items':[{'selectionId':offers[i%100]['selection_id'],'matchVersion':1,'marketVersion':1,'selectionVersion':1}]}
    warm,_,status=request(anonymous[0],'/api/tickets',body,{tokens[0]['headerName']:tokens[0]['token'],'Idempotency-Key':str(uuid.uuid4())})
    assert status==201
    _,_,status=request(admin,'/api/admin/tickets/'+warm['id']+'/confirm',{'ticketVersion':1,'pixReceiptChecked':True},{admin_csrf['headerName']:admin_csrf['token']});assert status==200
    warm_ids.append(str(uuid.UUID(warm['id'])))
values=','.join("'"+identifier+"'::uuid" for identifier in warm_ids)
sql('BEGIN; DELETE FROM public.ticket_items WHERE ticket_id IN ('+values+'); DELETE FROM public.tickets WHERE id IN ('+values+'); COMMIT; ANALYZE public.tickets; ANALYZE public.ticket_items;')
assert sql('SELECT count(*) FROM public.tickets')=='10000'
def timer(name,tags=None):
    query=urllib.parse.urlencode([('tag',key+':'+value) for key,value in (tags or {}).items()])
    data,_,status=request(admin,'/actuator/metrics/'+name+('?' + query if query else ''))
    return {m['statistic']:m['value'] for m in data.get('measurements',[])} if status==200 else {}
barrier=threading.Barrier(50)
def work(index):
    c=anonymous[index];token=tokens[index];entry=offers[index];results=[];barrier.wait(timeout=20)
    for _ in range(5):
        for operation,path in [('catalog','/api/matches'),('detail','/api/matches/'+entry['match_id'])]:
            _,elapsed,status=request(c,path);results.append((operation,elapsed,status))
        body={'bettorName':'Pessoa fictícia de carga','bettorPhone':'11999999999','stakeAmount':'10.00','rulesAccepted':True,'adultDeclared':True,'rulesVersion':'PREVIEW-2026-10-31-V1','items':[{'selectionId':entry['selection_id'],'matchVersion':1,'marketVersion':1,'selectionVersion':1}]}
        ticket,elapsed,status=request(c,'/api/tickets',body,{token['headerName']:token['token'],'Idempotency-Key':str(uuid.uuid4())});results.append(('issue',elapsed,status))
        if ticket:
            # An independent opener per call avoids sharing mutable cookie jars between threads.
            auth=client();auth.addheaders=[('Cookie','; '.join(cookie.name+'='+cookie.value for handler in admin.handlers if isinstance(handler,urllib.request.HTTPCookieProcessor) for cookie in handler.cookiejar))]
            _,elapsed,status=request(auth,'/api/admin/tickets/'+ticket['id']+'/confirm',{'ticketVersion':1,'pixReceiptChecked':True},{admin_csrf['headerName']:admin_csrf['token']});results.append(('confirm',elapsed,status))
    return results
# Warm the concurrent servlet/connection/serialization paths too. A sequential-only
# warmup does not exercise the 50-user route and database pool footprint.
with ThreadPoolExecutor(max_workers=50) as pool:
    warmed=[r for rows in pool.map(work,range(50)) for r in rows]
assert all(r[2] in (200,201) for r in warmed)
sql("BEGIN; DELETE FROM public.ticket_items WHERE ticket_id IN (SELECT id FROM public.tickets WHERE ticket_code NOT LIKE 'TF-BENCH-%'); DELETE FROM public.tickets WHERE ticket_code NOT LIKE 'TF-BENCH-%'; COMMIT; ANALYZE public.tickets; ANALYZE public.ticket_items;")
assert sql('SELECT count(*) FROM public.tickets')=='10000'
server_before={op:timer('trevo.http.duration',{'operation':op,'result':'success'}) for op in ['catalog','detail','issue','confirm']}
pool_before=timer('hikaricp.connections.acquire')
started=time.monotonic();load_before=os.getloadavg()
with ThreadPoolExecutor(max_workers=50) as pool:results=[r for rows in pool.map(work,range(50)) for r in rows]
report={'scope':'synthetic local HTTP including database, JVM warmed; no provider/Pix/WhatsApp calls','referenceMatches':100,'referenceTicketsBeforeLoad':10000,'concurrentUsers':50,'iterationsPerUser':5,'elapsedSeconds':round(time.monotonic()-started,3),'host':{'system':platform.system(),'machine':platform.machine(),'cpuCount':os.cpu_count(),'loadBefore':load_before,'loadAfter':os.getloadavg()},'operations':{}}
for operation in ['catalog','detail','issue','confirm']:
    rows=[r for r in results if r[0]==operation];times=sorted(r[1] for r in rows)
    report['operations'][operation]={'samples':len(rows),'errors':sum(r[2] not in (200,201) for r in rows),'p50Ms':round(statistics.median(times),3) if times else None,'p95Ms':round(times[max(0,(len(times)*95+99)//100-1)],3) if times else None,'maxMs':round(max(times),3) if times else None}
server_after={op:timer('trevo.http.duration',{'operation':op,'result':'success'}) for op in server_before}
for op,after in server_after.items():
    before=server_before[op];count=after.get('COUNT',0)-before.get('COUNT',0)
    report['operations'][op]['serverMeanMs']=round((after.get('TOTAL_TIME',0)-before.get('TOTAL_TIME',0))*1000/count,3) if count else None
pool_after=timer('hikaricp.connections.acquire');acquires=pool_after.get('COUNT',0)-pool_before.get('COUNT',0)
report['databaseConnectionAcquire']={'samples':acquires,'meanMs':round((pool_after.get('TOTAL_TIME',0)-pool_before.get('TOTAL_TIME',0))*1000/acquires,3) if acquires else None,'windowMaxMs':round(pool_after.get('MAX',0)*1000,3)}
report['warmupPerRoute']=350
report['databasePoolMaxSize']=int(os.environ.get('DB_POOL_MAX_SIZE','10'))
report['meetsP95Target']=all(v['samples']==250 and v['errors']==0 and v['p95Ms']<200 for v in report['operations'].values())
(output/'load-report.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report))
# Query plans contain only technical reference data; save locally, without PII/parameters.
(output/'query-plans.txt').write_text(sql("EXPLAIN (ANALYZE,BUFFERS) SELECT id FROM public.tickets WHERE ticket_code='TF-BENCH-1'; EXPLAIN (ANALYZE,BUFFERS) SELECT id FROM public.matches WHERE match_date>now() ORDER BY match_date LIMIT 20;"))
assert all(v['samples']==250 and v['errors']==0 for v in report['operations'].values()),'HTTP benchmark did not complete successfully'
