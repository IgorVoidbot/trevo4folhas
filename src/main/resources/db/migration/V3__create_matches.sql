-- TREVO4FOLHAS - matches. Physical kickoff column is match_date; there is no
-- duplicated starts_at column. No odds live here.

CREATE TABLE public.matches (
  id UUID NOT NULL DEFAULT gen_random_uuid(),
  competition VARCHAR(120) NOT NULL,
  home_team VARCHAR(120) NOT NULL,
  away_team VARCHAR(120) NOT NULL,
  match_date TIMESTAMPTZ NOT NULL,
  status VARCHAR(16) NOT NULL,
  source_namespace VARCHAR(255) NOT NULL,
  external_fixture_id VARCHAR(255) NOT NULL,
  provider_status VARCHAR(255) NOT NULL,
  admin_suspended BOOLEAN NOT NULL DEFAULT FALSE,
  structural_conflict BOOLEAN NOT NULL DEFAULT FALSE,
  request_attempt_at TIMESTAMPTZ,
  last_successful_sync_at TIMESTAMPTZ,
  provider_updated_at TIMESTAMPTZ,
  manual_reviewed_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT pk_matches PRIMARY KEY (id),
  CONSTRAINT uq_matches_source_identity UNIQUE (source_namespace, external_fixture_id),
  CONSTRAINT ck_matches_status CHECK (status IN ('OPEN', 'SUSPENDED', 'CLOSED', 'CANCELLED')),
  CONSTRAINT ck_matches_version_positive CHECK (version > 0),
  CONSTRAINT ck_matches_teams_distinct CHECK (btrim(lower(home_team)) <> btrim(lower(away_team))),
  CONSTRAINT ck_matches_competition_not_blank CHECK (btrim(competition) <> ''),
  CONSTRAINT ck_matches_home_team_not_blank CHECK (btrim(home_team) <> ''),
  CONSTRAINT ck_matches_away_team_not_blank CHECK (btrim(away_team) <> ''),
  CONSTRAINT ck_matches_source_namespace_not_blank CHECK (btrim(source_namespace) <> ''),
  CONSTRAINT ck_matches_external_fixture_id_not_blank CHECK (btrim(external_fixture_id) <> ''),
  CONSTRAINT ck_matches_provider_status_not_blank CHECK (btrim(provider_status) <> '')
);

CREATE INDEX idx_matches_match_date ON public.matches (match_date);

CREATE TRIGGER trg_matches_updated_at
BEFORE UPDATE ON public.matches
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
