-- TREVO4FOLHAS - selections inside a market. HOME/DRAW/AWAY are selection_code
-- values of the 1X2 market only, never a universal enum.

CREATE TABLE public.market_selections (
  id UUID NOT NULL DEFAULT gen_random_uuid(),
  market_id UUID NOT NULL,
  external_selection_key VARCHAR(255) NOT NULL,
  selection_code VARCHAR(64) NOT NULL,
  display_name VARCHAR(255) NOT NULL,
  odds NUMERIC(5, 2) NOT NULL,
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
  CONSTRAINT pk_market_selections PRIMARY KEY (id),
  CONSTRAINT fk_market_selections_market FOREIGN KEY (market_id) REFERENCES public.markets (id) ON DELETE RESTRICT,
  CONSTRAINT uq_market_selections_identity UNIQUE (market_id, external_selection_key),
  CONSTRAINT ck_market_selections_external_key_not_blank CHECK (btrim(external_selection_key) <> ''),
  CONSTRAINT ck_market_selections_code_not_blank CHECK (btrim(selection_code) <> ''),
  CONSTRAINT ck_market_selections_display_name_not_blank CHECK (btrim(display_name) <> ''),
  CONSTRAINT ck_market_selections_provider_status_not_blank CHECK (btrim(provider_status) <> ''),
  CONSTRAINT ck_market_selections_odds_range CHECK (odds >= 1.01 AND odds <= 100.00),
  CONSTRAINT ck_market_selections_version_positive CHECK (version > 0)
);

CREATE TRIGGER trg_market_selections_updated_at
BEFORE UPDATE ON public.market_selections
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
