#!/usr/bin/env python3
"""Validate public catalog/issuance on a disposable PostgreSQL 16 and local Java 17 app.
Build target/trevo4folhas.jar first. Requires Docker, Chrome and Playwright; set
PLAYWRIGHT_MODULE to its importable module path if it is not installed locally.
Never reads local admin credentials, calls the provider or sends WhatsApp messages.
"""
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import time
import urllib.request

root = Path.cwd()
output = root / 'target/verification-sprint3'
output.mkdir(parents=True, exist_ok=True)
env = os.environ.copy()
env['POSTGRES_PASSWORD'] = secrets.token_urlsafe(32)
env['POSTGRES_USER'] = 'browser_test'
env['POSTGRES_DB'] = 'browser_test'
container = None
app = None
try:
    container = subprocess.check_output([
        'docker', 'run', '--rm', '-d', '-p', '127.0.0.1::5432',
        '-e', 'POSTGRES_PASSWORD', '-e', 'POSTGRES_USER', '-e', 'POSTGRES_DB',
        'postgres:16-alpine'
    ], env=env, text=True).strip()
    info = json.loads(subprocess.check_output(['docker', 'inspect', container], text=True))[0]
    db_port = info['NetworkSettings']['Ports']['5432/tcp'][0]['HostPort']
    for _ in range(60):
        ready = subprocess.run(['docker', 'exec', container, 'pg_isready', '-U', env['POSTGRES_USER']], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        if ready.returncode == 0:
            break
        time.sleep(0.5)
    else:
        raise RuntimeError('Disposable database did not start')
    with socket.socket() as listener:
        listener.bind(('127.0.0.1', 0))
        port = listener.getsockname()[1]
    env.update({
        'DB_URL': f'jdbc:postgresql://127.0.0.1:{db_port}/browser_test',
        'DB_USERNAME': env['POSTGRES_USER'], 'DB_PASSWORD': env['POSTGRES_PASSWORD'],
        'ADMIN_BOOTSTRAP_USERNAME': 'browser_operator',
        'ADMIN_BOOTSTRAP_DISPLAY_NAME': 'Operador de teste',
        'ADMIN_BOOTSTRAP_PASSWORD': secrets.token_urlsafe(32),
        'MANUAL_PRICE_MAX_AGE_SECONDS': '3600',
        'MANUAL_SCHEDULE_MAX_AGE_SECONDS': '7200',
        'MANUAL_STATUS_MAX_AGE_SECONDS': '1800',
        'BANK_WHATSAPP_NUMBER': '5511999999999', 'TICKET_RATE_LIMIT_MAX_ATTEMPTS': '1000', 'SPORTS_DATA_ENABLED': 'false', 'SPORTS_DATA_SYNC_ENABLED': 'false',
        'TEST_BASE_URL': f'http://127.0.0.1:{port}',
        'TEST_ADMIN_USERNAME': 'browser_operator',
        'PLAYWRIGHT_MODULE': env.get('PLAYWRIGHT_MODULE', 'playwright'),
    })
    env['TEST_ADMIN_PASSWORD'] = env['ADMIN_BOOTSTRAP_PASSWORD']
    with (output / 'application.log').open('w') as log:
        app = subprocess.Popen([
            '/usr/lib/jvm/java-17-openjdk-amd64/bin/java', '-jar', 'target/trevo4folhas.jar',
            '--spring.profiles.active=local', '--server.address=127.0.0.1', f'--server.port={port}'
        ], env=env, stdout=log, stderr=subprocess.STDOUT)
        for _ in range(120):
            if app.poll() is not None:
                raise RuntimeError('Application exited; inspect target/verification-sprint3/application.log')
            try:
                with urllib.request.urlopen(env['TEST_BASE_URL'] + '/admin/login', timeout=1) as response:
                    if response.status == 200:
                        break
            except OSError:
                pass
            time.sleep(0.5)
        else:
            raise RuntimeError('Application did not start')
        credentials = output / 'test-credentials'
        credentials.touch(mode=0o600)
        credentials.write_text('ADMIN_USERNAME=browser_operator\nADMIN_PASSWORD=' + env['ADMIN_BOOTSTRAP_PASSWORD'] + '\n')
        try:
            subprocess.run(['python3', 'deploy/seed-local-catalog.py', '--base-url', env['TEST_BASE_URL'], '--credentials-file', str(credentials)], env=env, check=True)
            subprocess.run(['node', 'src/frontend/tests/public-catalog-browser.mjs'], env=env, check=True)
            subprocess.run(['node', 'src/frontend/tests/ticket-issuance-browser.mjs'], env=env, check=True)
        finally:
            credentials.unlink(missing_ok=True)
finally:
    if app is not None:
        app.terminate()
        try:
            app.wait(timeout=15)
        except subprocess.TimeoutExpired:
            app.kill()
            app.wait()
    if container is not None:
        subprocess.run(['docker', 'stop', container], stdout=subprocess.DEVNULL, check=True)
