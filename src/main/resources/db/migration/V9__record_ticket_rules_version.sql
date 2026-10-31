-- Preserve pre-issuance fixtures/history; new issuance supplies the accepted release version.
ALTER TABLE public.tickets
  ADD COLUMN rules_version VARCHAR(64) DEFAULT 'LEGACY_PRE_S3' NOT NULL,
  ADD CONSTRAINT ck_tickets_rules_version_not_blank CHECK (btrim(rules_version) <> '');
