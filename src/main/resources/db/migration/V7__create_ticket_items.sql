-- TREVO4FOLHAS - ticket items with immutable snapshots. Snapshots repeat the
-- canonical identity limits and sentinels of the live catalog and keep readable
-- names alongside them.

CREATE TABLE public.ticket_items (
  id UUID NOT NULL DEFAULT gen_random_uuid(),
  ticket_id UUID NOT NULL,
  match_id UUID NOT NULL,
  market_selection_id UUID NOT NULL,
  selected_odds NUMERIC(5, 2) NOT NULL,
  match_version_snapshot BIGINT NOT NULL,
  market_version_snapshot BIGINT NOT NULL,
  selection_version_snapshot BIGINT NOT NULL,
  market_snapshot VARCHAR(255) NOT NULL,
  selection_snapshot VARCHAR(255) NOT NULL,
  selection_code_snapshot VARCHAR(64) NOT NULL,
  period_snapshot VARCHAR(64) NOT NULL,
  line_snapshot VARCHAR(64) NOT NULL,
  participant_snapshot VARCHAR(255) NOT NULL,
  rule_snapshot TEXT,
  provider_snapshot VARCHAR(255) NOT NULL,
  bookmaker_snapshot VARCHAR(255) NOT NULL,
  external_fixture_id_snapshot VARCHAR(255) NOT NULL,
  external_market_id_snapshot VARCHAR(255) NOT NULL,
  external_selection_key_snapshot VARCHAR(255) NOT NULL,
  specifiers_snapshot TEXT NOT NULL,
  competition_snapshot VARCHAR(120) NOT NULL,
  home_team_snapshot VARCHAR(120) NOT NULL,
  away_team_snapshot VARCHAR(120) NOT NULL,
  match_date_snapshot TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT pk_ticket_items PRIMARY KEY (id),
  CONSTRAINT fk_ticket_items_ticket FOREIGN KEY (ticket_id) REFERENCES public.tickets (id) ON DELETE RESTRICT,
  CONSTRAINT fk_ticket_items_match FOREIGN KEY (match_id) REFERENCES public.matches (id) ON DELETE RESTRICT,
  CONSTRAINT fk_ticket_items_market_selection FOREIGN KEY (market_selection_id) REFERENCES public.market_selections (id) ON DELETE RESTRICT,
  CONSTRAINT uq_ticket_items_ticket_match UNIQUE (ticket_id, match_id),
  CONSTRAINT ck_ticket_items_selected_odds_range CHECK (selected_odds >= 1.01 AND selected_odds <= 100.00),
  CONSTRAINT ck_ticket_items_match_version_positive CHECK (match_version_snapshot > 0),
  CONSTRAINT ck_ticket_items_market_version_positive CHECK (market_version_snapshot > 0),
  CONSTRAINT ck_ticket_items_selection_version_positive CHECK (selection_version_snapshot > 0),
  CONSTRAINT ck_ticket_items_market_snapshot_not_blank CHECK (btrim(market_snapshot) <> ''),
  CONSTRAINT ck_ticket_items_selection_snapshot_not_blank CHECK (btrim(selection_snapshot) <> ''),
  CONSTRAINT ck_ticket_items_selection_code_not_blank CHECK (btrim(selection_code_snapshot) <> ''),
  CONSTRAINT ck_ticket_items_period_snapshot_not_blank CHECK (btrim(period_snapshot) <> ''),
  CONSTRAINT ck_ticket_items_line_snapshot_not_blank CHECK (btrim(line_snapshot) <> ''),
  CONSTRAINT ck_ticket_items_line_snapshot_canonical CHECK (line_snapshot = 'NONE' OR line_snapshot ~ '^-?(0|[1-9][0-9]*)(\.[0-9]*[1-9])?$'),
  CONSTRAINT ck_ticket_items_participant_not_blank CHECK (btrim(participant_snapshot) <> ''),
  CONSTRAINT ck_ticket_items_provider_not_blank CHECK (btrim(provider_snapshot) <> ''),
  CONSTRAINT ck_ticket_items_bookmaker_not_blank CHECK (btrim(bookmaker_snapshot) <> ''),
  CONSTRAINT ck_ticket_items_external_fixture_not_blank CHECK (btrim(external_fixture_id_snapshot) <> ''),
  CONSTRAINT ck_ticket_items_external_market_not_blank CHECK (btrim(external_market_id_snapshot) <> ''),
  CONSTRAINT ck_ticket_items_external_selection_not_blank CHECK (btrim(external_selection_key_snapshot) <> ''),
  CONSTRAINT ck_ticket_items_specifiers_not_blank CHECK (btrim(specifiers_snapshot) <> ''),
  CONSTRAINT ck_ticket_items_specifiers_bytes CHECK (octet_length(specifiers_snapshot) <= 1024),
  CONSTRAINT ck_ticket_items_competition_not_blank CHECK (btrim(competition_snapshot) <> ''),
  CONSTRAINT ck_ticket_items_home_team_not_blank CHECK (btrim(home_team_snapshot) <> ''),
  CONSTRAINT ck_ticket_items_away_team_not_blank CHECK (btrim(away_team_snapshot) <> '')
);

-- ticket_id is covered by the prefix of uq_ticket_items_ticket_match.
CREATE INDEX idx_ticket_items_match_id ON public.ticket_items (match_id);
CREATE INDEX idx_ticket_items_market_selection_id ON public.ticket_items (market_selection_id);

CREATE TRIGGER trg_ticket_items_updated_at
BEFORE UPDATE ON public.ticket_items
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
