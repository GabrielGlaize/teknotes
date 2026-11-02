-- Merging duplicates by agreement of the community, and acceptance of the terms.

-- Choosing a pseudo means accepting the terms (checked in the app before this row exists).
ALTER TABLE public.profiles ADD COLUMN terms_accepted_at timestamptz NOT NULL DEFAULT now();

-- Null: the entry was not absorbed by a merge. An absorbed entry stays hidden, so a
-- wrong merge can be undone by hand.
ALTER TABLE public.soundsystems ADD COLUMN merged_into uuid REFERENCES public.soundsystems (id) ON DELETE SET NULL;
ALTER TABLE public.artists ADD COLUMN merged_into uuid REFERENCES public.artists (id) ON DELETE SET NULL;

-- merged_into is not content either: history must not record it.
CREATE OR REPLACE FUNCTION public._record_revision()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_meta text[] := ARRAY['created_by', 'updated_by', 'created_at', 'updated_at', 'hidden', 'merged_into'];
BEGIN
  IF TG_OP = 'UPDATE' AND (to_jsonb(NEW) - v_meta) = (to_jsonb(OLD) - v_meta) THEN
    RETURN NULL;
  END IF;
  INSERT INTO public.revisions (entity, entity_id, author, before, after)
  VALUES (
    TG_ARGV[0],
    NEW.id,
    NEW.updated_by,
    CASE WHEN TG_OP = 'UPDATE' THEN to_jsonb(OLD) END,
    to_jsonb(NEW)
  );
  RETURN NULL;
END;
$$;

CREATE TABLE public.merge_proposals (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  entity text NOT NULL CHECK (entity IN ('soundsystem', 'artist')),
  kept_id uuid NOT NULL,
  absorbed_id uuid NOT NULL,
  -- Null: the author deleted their account; the proposal stays open.
  author uuid REFERENCES public.profiles (id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  -- Null: still open.
  applied_at timestamptz,
  CHECK (kept_id <> absorbed_id)
);

-- One open proposal per pair, in either direction.
CREATE UNIQUE INDEX merge_proposals_open_pair
  ON public.merge_proposals (entity, least(kept_id, absorbed_id), greatest(kept_id, absorbed_id))
  WHERE applied_at IS NULL;
CREATE INDEX merge_proposals_kept ON public.merge_proposals (kept_id);
CREATE INDEX merge_proposals_absorbed ON public.merge_proposals (absorbed_id);
CREATE INDEX merge_proposals_author ON public.merge_proposals (author);

CREATE TABLE public.merge_votes (
  proposal_id bigint NOT NULL REFERENCES public.merge_proposals (id) ON DELETE CASCADE,
  voter uuid NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (proposal_id, voter)
);

CREATE INDEX merge_votes_voter ON public.merge_votes (voter);

ALTER TABLE public.merge_proposals ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.merge_votes ENABLE ROW LEVEL SECURITY;

-- Read by anyone signed in. Written only by the functions below (no INSERT policy).
CREATE POLICY merge_proposals_select_signed_in
  ON public.merge_proposals FOR SELECT
  TO authenticated
  USING (true);

CREATE POLICY merge_votes_select_signed_in
  ON public.merge_votes FOR SELECT
  TO authenticated
  USING (true);

-- Moves everything from the absorbed entry to the kept one, then hides the absorbed one.
-- Internal: called only by the functions below.
CREATE FUNCTION public._apply_merge(p_proposal public.merge_proposals)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_kept uuid := p_proposal.kept_id;
  v_absorbed uuid := p_proposal.absorbed_id;
BEGIN
  IF p_proposal.entity = 'soundsystem' THEN
    UPDATE public.soundsystems AS kept SET
      aliases = ARRAY(SELECT DISTINCT a FROM unnest(kept.aliases || old.aliases || old.name) AS a WHERE a <> kept.name),
      styles = ARRAY(SELECT DISTINCT s FROM unnest(kept.styles || old.styles) AS s),
      links = ARRAY(SELECT DISTINCT l FROM unnest(kept.links || old.links) AS l),
      department = coalesce(kept.department, old.department)
    FROM public.soundsystems AS old
    WHERE kept.id = v_kept AND old.id = v_absorbed;

    INSERT INTO public.memberships (soundsystem_id, artist_id)
    SELECT v_kept, artist_id FROM public.memberships WHERE soundsystem_id = v_absorbed
    ON CONFLICT DO NOTHING;
    DELETE FROM public.memberships WHERE soundsystem_id = v_absorbed;

    UPDATE public.night_entries SET soundsystem_id = v_kept WHERE soundsystem_id = v_absorbed;
    UPDATE public.soundsystems SET hidden = true, merged_into = v_kept WHERE id = v_absorbed;
  ELSE
    UPDATE public.artists AS kept SET
      aliases = ARRAY(SELECT DISTINCT a FROM unnest(kept.aliases || old.aliases || old.name) AS a WHERE a <> kept.name),
      styles = ARRAY(SELECT DISTINCT s FROM unnest(kept.styles || old.styles) AS s),
      links = ARRAY(SELECT DISTINCT l FROM unnest(kept.links || old.links) AS l)
    FROM public.artists AS old
    WHERE kept.id = v_kept AND old.id = v_absorbed;

    INSERT INTO public.memberships (soundsystem_id, artist_id)
    SELECT soundsystem_id, v_kept FROM public.memberships WHERE artist_id = v_absorbed
    ON CONFLICT DO NOTHING;
    DELETE FROM public.memberships WHERE artist_id = v_absorbed;

    UPDATE public.night_entries SET artist_id = v_kept WHERE artist_id = v_absorbed;
    UPDATE public.artists SET hidden = true, merged_into = v_kept WHERE id = v_absorbed;
  END IF;

  UPDATE public.merge_proposals SET applied_at = now() WHERE id = p_proposal.id;

  -- Other open proposals about the absorbed entry no longer mean anything.
  DELETE FROM public.merge_proposals
  WHERE applied_at IS NULL AND entity = p_proposal.entity
    AND (kept_id = v_absorbed OR absorbed_id = v_absorbed);
END;
$$;

REVOKE ALL ON FUNCTION public._apply_merge(public.merge_proposals) FROM public, anon, authenticated;

-- Proposes that p_absorbed be merged into p_kept. The proposer's approval counts.
CREATE FUNCTION public.propose_merge(p_entity text, p_kept uuid, p_absorbed uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user uuid := auth.uid();
  v_visible int;
  v_proposal public.merge_proposals;
BEGIN
  IF v_user IS NULL OR NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = v_user) THEN
    RAISE EXCEPTION 'Connecte-toi pour proposer une fusion.'
      USING ERRCODE = 'P0001', DETAIL = '{"code": "not_authenticated"}';
  END IF;
  IF p_kept = p_absorbed THEN
    RAISE EXCEPTION 'Une fiche ne peut pas fusionner avec elle-même.'
      USING ERRCODE = 'P0001', DETAIL = '{"code": "merge_same_entry"}';
  END IF;

  IF p_entity = 'soundsystem' THEN
    SELECT count(*) INTO v_visible FROM public.soundsystems WHERE id IN (p_kept, p_absorbed) AND NOT hidden;
  ELSIF p_entity = 'artist' THEN
    SELECT count(*) INTO v_visible FROM public.artists WHERE id IN (p_kept, p_absorbed) AND NOT hidden;
  ELSE
    v_visible := 0;
  END IF;
  IF v_visible <> 2 THEN
    RAISE EXCEPTION 'Une de ces fiches n''est plus dans l''annuaire.'
      USING ERRCODE = 'P0001', DETAIL = '{"code": "merge_entry_not_found"}';
  END IF;

  IF EXISTS (
    SELECT 1 FROM public.merge_proposals
    WHERE entity = p_entity AND applied_at IS NULL
      AND least(kept_id, absorbed_id) = least(p_kept, p_absorbed)
      AND greatest(kept_id, absorbed_id) = greatest(p_kept, p_absorbed)
  ) THEN
    RAISE EXCEPTION 'Cette fusion est déjà proposée.'
      USING ERRCODE = 'P0001', DETAIL = '{"code": "merge_already_proposed"}';
  END IF;

  INSERT INTO public.merge_proposals (entity, kept_id, absorbed_id, author)
  VALUES (p_entity, p_kept, p_absorbed, v_user)
  RETURNING * INTO v_proposal;

  INSERT INTO public.merge_votes (proposal_id, voter) VALUES (v_proposal.id, v_user);

  RETURN jsonb_build_object('id', v_proposal.id, 'approvals', 1, 'applied', false);
END;
$$;

-- Approves a proposal; the third approval applies the merge.
CREATE FUNCTION public.approve_merge(p_proposal bigint)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_user uuid := auth.uid();
  v_proposal public.merge_proposals;
  v_approvals int;
BEGIN
  IF v_user IS NULL OR NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = v_user) THEN
    RAISE EXCEPTION 'Connecte-toi pour approuver une fusion.'
      USING ERRCODE = 'P0001', DETAIL = '{"code": "not_authenticated"}';
  END IF;

  -- Locked so two simultaneous last approvals can't apply the merge twice.
  SELECT * INTO v_proposal FROM public.merge_proposals WHERE id = p_proposal AND applied_at IS NULL FOR UPDATE;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Cette proposition n''existe plus.'
      USING ERRCODE = 'P0001', DETAIL = '{"code": "merge_proposal_not_found"}';
  END IF;

  IF EXISTS (SELECT 1 FROM public.merge_votes WHERE proposal_id = p_proposal AND voter = v_user) THEN
    RAISE EXCEPTION 'Tu as déjà approuvé cette fusion.'
      USING ERRCODE = 'P0001', DETAIL = '{"code": "merge_already_approved"}';
  END IF;

  INSERT INTO public.merge_votes (proposal_id, voter) VALUES (p_proposal, v_user);
  SELECT count(*) INTO v_approvals FROM public.merge_votes WHERE proposal_id = p_proposal;

  IF v_approvals >= 3 THEN
    PERFORM public._apply_merge(v_proposal);
  END IF;

  RETURN jsonb_build_object('id', p_proposal, 'approvals', v_approvals, 'applied', v_approvals >= 3);
END;
$$;

REVOKE ALL ON FUNCTION public.propose_merge(text, uuid, uuid) FROM public, anon;
REVOKE ALL ON FUNCTION public.approve_merge(bigint) FROM public, anon;
GRANT EXECUTE ON FUNCTION public.propose_merge(text, uuid, uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.approve_merge(bigint) TO authenticated;
