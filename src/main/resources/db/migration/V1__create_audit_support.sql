-- TREVO4FOLHAS - database-wide UTC plus shared audit support.
-- Audit columns are TIMESTAMP WITHOUT TIME ZONE and must always store UTC,
-- regardless of the developer machine or the client session timezone.

DO $$
BEGIN
  EXECUTE format('ALTER DATABASE %I SET TIME ZONE %L', current_database(), 'UTC');
END;
$$;

-- Shared BEFORE UPDATE trigger function: refreshes updated_at and preserves
-- created_at for every table, including direct SQL and batch job updates.
CREATE FUNCTION public.set_updated_at() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  NEW.created_at := OLD.created_at;
  NEW.updated_at := (CURRENT_TIMESTAMP AT TIME ZONE 'UTC');
  RETURN NEW;
END;
$$;
