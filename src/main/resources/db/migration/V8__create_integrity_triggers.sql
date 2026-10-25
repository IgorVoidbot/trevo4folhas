-- TREVO4FOLHAS - cross-row invariants that plain constraints cannot express:
-- a ticket item must point to the match that owns the referenced selection, and
-- catalog rows never move between parents.

CREATE FUNCTION public.check_ticket_item_selection_match() RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  selection_match_id UUID;
BEGIN
  SELECT m.match_id
  INTO selection_match_id
  FROM public.market_selections ms
  JOIN public.markets m ON m.id = ms.market_id
  WHERE ms.id = NEW.market_selection_id;

  IF selection_match_id IS NULL THEN
    RAISE EXCEPTION 'ticket_items.market_selection_id % does not resolve to a market selection', NEW.market_selection_id
      USING ERRCODE = '23503';
  END IF;
  IF selection_match_id <> NEW.match_id THEN
    RAISE EXCEPTION 'ticket_items.match_id % does not match the match of selection % (expected %)',
      NEW.match_id, NEW.market_selection_id, selection_match_id
      USING ERRCODE = '23514';
  END IF;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_ticket_items_selection_match
AFTER INSERT OR UPDATE ON public.ticket_items
FOR EACH ROW EXECUTE FUNCTION public.check_ticket_item_selection_match();

CREATE FUNCTION public.prevent_market_match_change() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.match_id IS DISTINCT FROM OLD.match_id THEN
    RAISE EXCEPTION 'markets.match_id is immutable; create a new market instead'
      USING ERRCODE = '23514';
  END IF;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_markets_parent_immutable
AFTER UPDATE ON public.markets
FOR EACH ROW EXECUTE FUNCTION public.prevent_market_match_change();

CREATE FUNCTION public.prevent_selection_market_change() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.market_id IS DISTINCT FROM OLD.market_id THEN
    RAISE EXCEPTION 'market_selections.market_id is immutable; create a new selection instead'
      USING ERRCODE = '23514';
  END IF;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_market_selections_parent_immutable
AFTER UPDATE ON public.market_selections
FOR EACH ROW EXECUTE FUNCTION public.prevent_selection_market_change();

CREATE FUNCTION public.prevent_ticket_item_parent_change() RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.ticket_id IS DISTINCT FROM OLD.ticket_id
    OR NEW.match_id IS DISTINCT FROM OLD.match_id
    OR NEW.market_selection_id IS DISTINCT FROM OLD.market_selection_id THEN
    RAISE EXCEPTION 'ticket_items parent references are immutable; issue a new item instead'
      USING ERRCODE = '23514';
  END IF;
  RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER ct_ticket_items_parent_immutable
AFTER UPDATE ON public.ticket_items
FOR EACH ROW EXECUTE FUNCTION public.prevent_ticket_item_parent_change();
