-- TREVO4FOLHAS - market instances. Identity is the composite key
-- (match_id, source_namespace, bookmaker_id, external_market_id, period, line,
-- participant, rule_key, specifiers_key); display labels never take part in it.
-- Non-applicable identity components use the canonical sentinel NONE, never
-- NULL or the empty string. Line is a canonical decimal without redundant
-- zeros, keeping absence (NONE) distinct from zero (0).

CREATE TABLE public.markets (
  id UUID NOT NULL DEFAULT gen_random_uuid(),
  match_id UUID NOT NULL,
  source_namespace VARCHAR(255) NOT NULL,
  bookmaker_id VARCHAR(255) NOT NULL,
  external_market_id VARCHAR(255) NOT NULL,
  market_type VARCHAR(255) NOT NULL,
  display_name VARCHAR(255) NOT NULL,
  period VARCHAR(64) NOT NULL,
  line VARCHAR(64) NOT NULL,
  participant VARCHAR(255) NOT NULL,
  specifiers_key TEXT NOT NULL,
  rule_key VARCHAR(255) NOT NULL,
  rule_text TEXT,
  provider_status VARCHAR(255) NOT NULL,
  admin_suspended BOOLEAN NOT NULL DEFAULT FALSE,
  curated BOOLEAN NOT NULL DEFAULT FALSE,
  request_attempt_at TIMESTAMPTZ,
  last_successful_sync_at TIMESTAMPTZ,
  provider_updated_at TIMESTAMPTZ,
  manual_reviewed_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT pk_markets PRIMARY KEY (id),
  CONSTRAINT fk_markets_match FOREIGN KEY (match_id) REFERENCES public.matches (id) ON DELETE RESTRICT,
  CONSTRAINT uq_markets_identity UNIQUE (match_id, source_namespace, bookmaker_id, external_market_id, period, line, participant, rule_key, specifiers_key),
  CONSTRAINT ck_markets_market_type_not_blank CHECK (btrim(market_type) <> ''),
  CONSTRAINT ck_markets_display_name_not_blank CHECK (btrim(display_name) <> ''),
  CONSTRAINT ck_markets_source_namespace_not_blank CHECK (btrim(source_namespace) <> ''),
  CONSTRAINT ck_markets_bookmaker_id_not_blank CHECK (btrim(bookmaker_id) <> ''),
  CONSTRAINT ck_markets_external_market_id_not_blank CHECK (btrim(external_market_id) <> ''),
  CONSTRAINT ck_markets_period_not_blank CHECK (btrim(period) <> ''),
  CONSTRAINT ck_markets_line_not_blank CHECK (btrim(line) <> ''),
  CONSTRAINT ck_markets_line_canonical CHECK (line = 'NONE' OR line ~ '^-?(0|[1-9][0-9]*)(\.[0-9]*[1-9])?$'),
  CONSTRAINT ck_markets_participant_not_blank CHECK (btrim(participant) <> ''),
  CONSTRAINT ck_markets_specifiers_key_not_blank CHECK (btrim(specifiers_key) <> ''),
  CONSTRAINT ck_markets_specifiers_key_bytes CHECK (octet_length(specifiers_key) <= 1024),
  CONSTRAINT ck_markets_rule_key_not_blank CHECK (btrim(rule_key) <> ''),
  CONSTRAINT ck_markets_provider_status_not_blank CHECK (btrim(provider_status) <> ''),
  CONSTRAINT ck_markets_version_positive CHECK (version > 0),
  -- Aggregate byte budget of the identity key so the UNIQUE btree entry always
  -- fits a PostgreSQL 16 index row. Values are never truncated: identity that
  -- exceeds the budget is rejected, which blocks the import instead of
  -- corrupting the key.
  CONSTRAINT ck_markets_identity_key_bytes CHECK (
    octet_length(source_namespace) + octet_length(bookmaker_id) + octet_length(external_market_id)
    + octet_length(period) + octet_length(line) + octet_length(participant) + octet_length(rule_key)
    + octet_length(specifiers_key) <= 2048
  )
);

CREATE TRIGGER trg_markets_updated_at
BEFORE UPDATE ON public.markets
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
