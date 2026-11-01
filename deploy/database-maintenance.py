#!/usr/bin/env python3
"""Private PostgreSQL 16 backup, empty-target restore and reviewed retention operations.
Uses an explicitly named container and PGUSER/PGDATABASE; never accepts passwords in argv.
"""
import argparse
from datetime import datetime, timezone, timedelta
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid

def command(container, *args, input_data=None):
    result = subprocess.run(['docker', 'exec', '-i', container, *args], input=input_data,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError('Comando do banco recusado. Confira o destino e os requisitos; detalhes privados não foram exibidos.')
    return result.stdout

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--container', required=True)
    parser.add_argument('--user', default=os.environ.get('PGUSER'))
    parser.add_argument('--database', default=os.environ.get('PGDATABASE'))
    sub = parser.add_subparsers(dest='action', required=True)
    backup = sub.add_parser('backup'); backup.add_argument('--directory', type=Path, required=True); backup.add_argument('--prune', action='store_true')
    restore = sub.add_parser('restore'); restore.add_argument('--file', type=Path, required=True)
    anonymize = sub.add_parser('anonymize'); anonymize.add_argument('--ticket-ids-file', type=Path, required=True); anonymize.add_argument('--reviewed-retention', action='store_true'); anonymize.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if not args.user or not args.database or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,127}', args.container):
        raise RuntimeError('Informe container, usuário e banco explicitamente.')
    def sql(query):
        return command(args.container, 'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-U', args.user, '-d', args.database, '-tA', input_data=query.encode()).decode().strip()
    if not sql('SHOW server_version_num;').startswith('16'):
        raise RuntimeError('O procedimento exige PostgreSQL 16.')
    if args.action == 'backup':
        args.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        if args.directory.is_symlink() or args.directory.stat().st_mode & 0o077:
            raise RuntimeError('Use diretório privado (modo 700), sem link simbólico.')
        stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
        path = args.directory / f'trevo-{stamp}.dump'
        # Open exclusively and privately before producing any sensitive data.
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        try:
            with os.fdopen(descriptor, 'wb') as output:
                result = subprocess.run(['docker', 'exec', args.container, 'pg_dump', '-Fc', '--no-owner', '--no-acl', '-U', args.user, '-d', args.database], stdout=output, stderr=subprocess.PIPE)
                if result.returncode:
                    raise RuntimeError('Backup não concluído. Arquivo incompleto será removido.')
        except BaseException:
            path.unlink(missing_ok=True)
            raise
        removed = 0
        if args.prune:
            cutoff = (datetime.now(timezone.utc) - timedelta(days=7)).timestamp()
            for candidate in args.directory.iterdir():
                if re.fullmatch(r'trevo-\d{8}T\d{12}Z\.dump', candidate.name) and not candidate.is_symlink() and candidate.is_file() and candidate.stat().st_mtime < cutoff:
                    candidate.unlink(); removed += 1
        print(json.dumps({'backup': str(path), 'bytes': path.stat().st_size, 'expiredFilesRemoved': removed}))
    elif args.action == 'restore':
        if not args.file.is_file() or args.file.is_symlink() or args.file.stat().st_mode & 0o077:
            raise RuntimeError('Use um dump privado (modo 600), sem link simbólico.')
        if sql("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg_toast%' AND c.relkind IN ('r','p','v','m','S');") != '0':
            raise RuntimeError('Restauração recusada: o destino deve estar vazio e isolado. Não será executado --clean.')
        with args.file.open('rb') as dump:
            result = subprocess.run(['docker', 'exec', '-i', args.container, 'pg_restore', '--exit-on-error', '--single-transaction', '--no-owner', '--no-acl', '-U', args.user, '-d', args.database], stdin=dump, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode:
            raise RuntimeError('Restauração recusada ou revertida. Confira compatibilidade; detalhes privados não foram exibidos.')
        sql("DO $$ BEGIN EXECUTE format('ALTER DATABASE %I SET TIME ZONE %L',current_database(),'UTC'); END $$;")
        print(json.dumps({'restored': True}))
    else:
        if not args.reviewed_retention:
            raise RuntimeError('Exige revisão das exceções legais/operacionais e lista explícita de UUIDs aprovados; use --reviewed-retention.')
        if args.ticket_ids_file.is_symlink() or args.ticket_ids_file.stat().st_mode & 0o077:
            raise RuntimeError('A lista aprovada deve ser privada (modo 600).')
        ids = [str(uuid.UUID(line.strip())) for line in args.ticket_ids_file.read_text().splitlines() if line.strip()]
        if not ids or len(ids) > 1000 or len(set(ids)) != len(ids):
            raise RuntimeError('Informe de 1 a 1000 UUIDs distintos revisados.')
        # UUIDs are parsed above. No raw user text, PII or credentials are interpolated.
        values = ','.join("'" + identifier + "'::uuid" for identifier in ids)
        query = f"""BEGIN;
          SET LOCAL lock_timeout='2s'; SET LOCAL statement_timeout='5s';
          CREATE TEMP TABLE approved_retention AS SELECT id FROM public.tickets
            WHERE id IN ({values}) AND created_at < (CURRENT_TIMESTAMP AT TIME ZONE 'UTC')-interval '90 days'
              AND status IN ('CONFIRMED','CANCELLED','EXPIRED') ORDER BY id FOR UPDATE;
          DO $$ BEGIN IF (SELECT count(*) FROM approved_retention) <> {len(ids)} THEN
             RAISE EXCEPTION 'Retention list is not eligible'; END IF; END $$;
          WITH changed AS (UPDATE public.tickets SET bettor_name='Anonimizado',bettor_phone='Removido',
            operator_note=NULL,cancellation_reason=CASE WHEN status='CANCELLED' THEN 'Motivo removido por retenção' ELSE NULL END,version=version+1
            WHERE id IN (SELECT id FROM approved_retention) AND (bettor_name<>'Anonimizado' OR bettor_phone<>'Removido' OR operator_note IS NOT NULL OR (status='CANCELLED' AND cancellation_reason<>'Motivo removido por retenção')) RETURNING id)
          SELECT 'changed='||count(*) FROM changed;
          {'COMMIT' if args.apply else 'ROLLBACK'};"""
        output = sql(query)
        changed = next(line for line in output.splitlines() if line.startswith('changed='))
        print(json.dumps({'mode': 'applied' if args.apply else 'dry-run', 'eligible': len(ids), 'changed': int(changed.split('=')[1])}))

if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, ValueError, StopIteration):
        print('Operação não concluída. Confira destino, permissões, revisão e requisitos do procedimento.', file=sys.stderr)
        sys.exit(1)
