-- TREVO4FOLHAS - operators of the admin panel. Disabled operators are kept
-- for traceability; nothing in the product deletes them.

CREATE TABLE public.admins (
  id UUID NOT NULL DEFAULT gen_random_uuid(),
  username VARCHAR(100) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  CONSTRAINT pk_admins PRIMARY KEY (id),
  CONSTRAINT uq_admins_username UNIQUE (username),
  CONSTRAINT ck_admins_username_not_blank CHECK (btrim(username) <> ''),
  CONSTRAINT ck_admins_display_name_not_blank CHECK (btrim(display_name) <> ''),
  CONSTRAINT ck_admins_password_hash_not_blank CHECK (btrim(password_hash) <> '')
);

CREATE TRIGGER trg_admins_updated_at
BEFORE UPDATE ON public.admins
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
