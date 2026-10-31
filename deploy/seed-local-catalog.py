#!/usr/bin/env python3
"""Create explicitly fictional local catalog data through authenticated, CSRF-protected APIs."""

import argparse
from datetime import datetime, timedelta
from html.parser import HTMLParser
import http.cookiejar
import json
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request
from zoneinfo import ZoneInfo

COMPETITION = 'TESTE — Campeonato fictício'
ROOT = Path(__file__).resolve().parents[1]
MARKETS = [
    ('1X2', 'TESTE — Resultado da partida', None,
     'Tempo regulamentar e acréscimos, sem prorrogação ou pênaltis. Dados fictícios para teste.',
     [('HOME', 'Vitória do mandante', '1.80'), ('DRAW', 'Empate', '3.20'), ('AWAY', 'Vitória do visitante', '4.50')]),
    ('TOTALS', 'TESTE — Total de gols', '2.5',
     'Soma dos gols das duas equipes no tempo regulamentar e acréscimos. Linha 2,5. Dados fictícios para teste.',
     [('OVER', 'Mais de 2,5 gols', '2.50'), ('UNDER', 'Menos de 2,5 gols', '1.55')]),
    ('BTTS', 'TESTE — Ambas marcam', None,
     'Sim quando as duas equipes marcam ao menos um gol no tempo regulamentar e acréscimos. Dados fictícios para teste.',
     [('YES', 'Sim', '1.90'), ('NO', 'Não', '1.85')]),
]


class TokenParser(HTMLParser):
    token = None

    def handle_starttag(self, tag, attrs):
        values = dict(attrs)
        if tag == 'input' and values.get('name') == '_csrf':
            self.token = values.get('value')


def run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:8081')
    parser.add_argument('--credentials-file', type=Path, default=ROOT / '.env.admin-access')
    options = parser.parse_args()
    base = options.base_url.rstrip('/')
    address = urllib.parse.urlparse(base)
    if address.scheme != 'http' or address.hostname != '127.0.0.1' or address.path or address.query or address.fragment or address.username or address.password:
        raise RuntimeError('Use only a local development application at http://127.0.0.1:PORT')
    credentials = dict(line.split('=', 1) for line in options.credentials_file.read_text().splitlines() if '=' in line)
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def token():
        with opener.open(base + '/admin/login', timeout=10) as response:
            page = TokenParser()
            page.feed(response.read().decode('utf-8'))
        if not page.token:
            raise RuntimeError('CSRF token unavailable')
        return page.token

    login = urllib.parse.urlencode({
        'username': credentials['ADMIN_USERNAME'],
        'password': credentials['ADMIN_PASSWORD'], '_csrf': token(),
    }).encode()
    with opener.open(base + '/admin/login', login, timeout=10) as response:
        if urllib.parse.urlparse(response.url).path != '/admin':
            raise RuntimeError('Administrative login failed')
    # Home uses CSRF meta tags; retrieve the session token from the login endpoint
    # only before authentication and from meta tags after session fixation protection.
    class MetaParser(HTMLParser):
        csrf = None
        def handle_starttag(self, tag, attrs):
            values = dict(attrs)
            if tag == 'meta' and values.get('name') == 'csrf-token':
                self.csrf = values.get('content')
    with opener.open(base + '/admin', timeout=10) as response:
        meta = MetaParser()
        meta.feed(response.read().decode('utf-8'))
    if not meta.csrf:
        raise RuntimeError('Authenticated CSRF token unavailable')

    def api(path, body=None):
        headers = {'Accept': 'application/json'}
        if body is not None:
            headers.update({'Content-Type': 'application/json', 'X-CSRF-TOKEN': meta.csrf})
        request = urllib.request.Request(base + path, data=None if body is None else json.dumps(body).encode(), headers=headers)
        with opener.open(request, timeout=10) as response:
            if urllib.parse.urlparse(response.url).path != path.split('?', 1)[0]:
                raise RuntimeError('Unexpected redirect')
            return json.load(response)

    created = {'matches': 0, 'markets': 0, 'selections': 0}
    query = urllib.parse.urlencode({'competition': COMPETITION, 'size': 100})
    existing = api('/api/admin/matches?' + query)
    if existing['totalElements'] > 100:
        raise RuntimeError('Ambiguous test catalog; review existing records')
    for index, (home, away) in enumerate([
        ('Equipe fictícia Verde', 'Equipe fictícia Azul'),
        ('Equipe fictícia Sol', 'Equipe fictícia Lua'),
        ('Equipe fictícia Norte', 'Equipe fictícia Sul'),
    ], 1):
        found = [row for row in existing['content'] if row['homeTeam'] == home and row['awayTeam'] == away]
        if len(found) > 1:
            raise RuntimeError('Duplicate test match; existing data preserved')
        if found:
            match = found[0]
        else:
            start = (datetime.now(ZoneInfo('America/Sao_Paulo')) + timedelta(days=index)).replace(hour=15, minute=0, second=0, microsecond=0)
            match = api('/api/admin/matches', {'competition': COMPETITION, 'homeTeam': home, 'awayTeam': away, 'startsAt': start.isoformat()})
            created['matches'] += 1
        if match['sourceNamespace'] != 'MANUAL' or match['status'] != 'OPEN' or match['adminSuspended']:
            raise RuntimeError('Existing test match unavailable; no automatic reopening')
        existing_markets = api(f"/api/admin/matches/{match['id']}/markets?size=100")
        for kind, name, line, rule, selections in MARKETS:
            found = [row for row in existing_markets['content'] if row['marketType'] == kind and row['line'] == (line or 'NONE')]
            if len(found) > 1:
                raise RuntimeError('Ambiguous test market; existing data preserved')
            if found:
                market = found[0]
            else:
                market = api(f"/api/admin/matches/{match['id']}/markets", {
                    'matchVersion': match['matchVersion'], 'marketType': kind, 'displayName': name,
                    'period': 'FT', 'line': line, 'ruleKey': 'REGULATION_TIME', 'ruleText': rule, 'curated': True,
                })
                created['markets'] += 1
            existing_selections = api(f"/api/admin/markets/{market['id']}/selections?size=100")
            for code, label, price in selections:
                if any(row['selectionCode'] == code for row in existing_selections['content']):
                    continue
                api(f"/api/admin/markets/{market['id']}/selections", {
                    'matchVersion': match['matchVersion'], 'marketVersion': market['marketVersion'],
                    'selectionCode': code, 'displayName': label, 'odds': price, 'curated': True,
                })
                created['selections'] += 1
    public = api('/api/matches?' + query)
    if public['totalElements'] != 3:
        raise RuntimeError('Review local freshness configuration or existing test record state')
    market_count = 0
    selection_count = 0
    for match in public['content']:
        markets = api(f"/api/matches/{match['id']}/markets?size=100")
        market_count += markets['totalElements']
        for market in markets['content']:
            selections = api(f"/api/markets/{market['id']}/selections?size=100")
            selection_count += selections['totalElements']
            if any(not isinstance(row['odds'], str) or row['selectionVersion'] < 1 for row in selections['content']):
                raise RuntimeError('Unexpected selection contract')
    if market_count != 9 or selection_count != 21:
        raise RuntimeError('Some test offers are unavailable; review their state and freshness')
    print(json.dumps({'created': created, 'publicEligibleMatches': public['totalElements'], 'publicEligibleMarkets': market_count, 'publicEligibleSelections': selection_count, 'competition': COMPETITION}, ensure_ascii=False))


if __name__ == '__main__':
    try:
        run()
    except Exception as error:
        # Never print credentials, HTTP bodies, cookie values or raw exception messages.
        print('Local catalog setup stopped: ' + type(error).__name__, file=sys.stderr)
        sys.exit(1)
