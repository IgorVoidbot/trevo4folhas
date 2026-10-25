-- TREVO4FOLHAS - pre-tickets. Physical code column is ticket_code; there is no
-- duplicated code column. Manual decisions always carry operator and timestamp.

CREATE TABLE public.tickets (
  id UUID NOT NULL DEFAULT gen_random_uuid(),
  ticket_code VARCHAR(32) NOT NULL,
  request_key UUID NOT NULL,
  request_hash CHAR(64) NOT NULL,
  session_hash CHAR(64) NOT NULL,
  bettor_name VARCHAR(100) NOT NULL,
  bettor_phone VARCHAR(15) NOT NULL,
  stake_amount NUMERIC(14, 2) NOT NULL,
  total_odds NUMERIC(41, 20) NOT NULL,
  potential_return NUMERIC(14, 2) NOT NULL,
  status VARCHAR(16) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  handled_by UUID,
  handled_at TIMESTAMPTZ,
  cancellation_reason VARCHAR(250),
  operator_note VARCHAR(500),
  whatsapp_clicked_at TIMESTAMPTZ,
  version BIGINT NOT NULL DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT pk_tickets PRIMARY KEY (id),
  CONSTRAINT uq_tickets_ticket_code UNIQUE (ticket_code),
  CONSTRAINT uq_tickets_request_key UNIQUE (request_key),
  CONSTRAINT fk_tickets_handled_by FOREIGN KEY (handled_by) REFERENCES public.admins (id) ON DELETE RESTRICT,
  CONSTRAINT ck_tickets_ticket_code_not_blank CHECK (btrim(ticket_code) <> ''),
  CONSTRAINT ck_tickets_request_hash_sha256 CHECK (request_hash ~ '^[0-9a-fA-F]{64}$'),
  CONSTRAINT ck_tickets_session_hash_sha256 CHECK (session_hash ~ '^[0-9a-fA-F]{64}$'),
  CONSTRAINT ck_tickets_bettor_name_not_blank CHECK (btrim(bettor_name) <> ''),
  CONSTRAINT ck_tickets_bettor_phone_not_blank CHECK (btrim(bettor_phone) <> ''),
  CONSTRAINT ck_tickets_stake_amount_positive CHECK (stake_amount > 0),
  CONSTRAINT ck_tickets_total_odds_positive CHECK (total_odds > 0),
  CONSTRAINT ck_tickets_potential_return_positive CHECK (potential_return > 0),
  CONSTRAINT ck_tickets_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),
  -- Manual decisions require operator and timestamp; automatic states forbid
  -- them. A cancellation reason exists if, and only if, the ticket is cancelled.
  CONSTRAINT ck_tickets_decision_consistency CHECK (
    (status IN ('PENDING', 'EXPIRED') AND handled_by IS NULL AND handled_at IS NULL)
    OR (status IN ('CONFIRMED', 'CANCELLED') AND handled_by IS NOT NULL AND handled_at IS NOT NULL)
  ),
  CONSTRAINT ck_tickets_cancellation_reason_presence CHECK ((status = 'CANCELLED') = (cancellation_reason IS NOT NULL)),
  CONSTRAINT ck_tickets_cancellation_reason_not_blank CHECK (cancellation_reason IS NULL OR btrim(cancellation_reason) <> ''),
  CONSTRAINT ck_tickets_version_positive CHECK (version > 0)
);

CREATE INDEX idx_tickets_status_created_at ON public.tickets (status, created_at);
CREATE INDEX idx_tickets_expires_at ON public.tickets (expires_at);
CREATE INDEX idx_tickets_handled_by ON public.tickets (handled_by);

CREATE TRIGGER trg_tickets_updated_at
BEFORE UPDATE ON public.tickets
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
