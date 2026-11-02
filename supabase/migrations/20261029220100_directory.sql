-- Shared directory of soundsystems and artists, filled by the people who meet them
-- (self-sufficiency): any signed-in user creates and edits, every change is kept in
-- revisions, three reports from different people hide an entry.

-- Profiles -------------------------------------------------------------------

CREATE TABLE public.profiles (
  id uuid PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
  -- The only public information about a contributor.
  pseudo text NOT NULL CHECK (char_length(pseudo) BETWEEN 2 AND 24),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX profiles_pseudo_unique ON public.profiles (lower(pseudo));

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY profiles_select_all
  ON public.profiles FOR SELECT
  TO anon, authenticated
  USING (true);

CREATE POLICY profiles_insert_own
  ON public.profiles FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = id);

CREATE POLICY profiles_update_own
  ON public.profiles FOR UPDATE
  TO authenticated
  USING ((select auth.uid()) = id)
  WITH CHECK ((select auth.uid()) = id);

-- Soundsystems and artists ---------------------------------------------------

CREATE TABLE public.soundsystems (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 80),
  aliases text[] NOT NULL DEFAULT '{}',
  -- Null: origin not known yet; the community fills it in later.
  department text REFERENCES public.departments (code),
  styles public.music_style[] NOT NULL DEFAULT '{}',
  links text[] NOT NULL DEFAULT '{}',
  -- Set only by moderation (reports), never by a client update.
  hidden boolean NOT NULL DEFAULT false,
  -- Null: imported data (demo, seed) that no user wrote.
  created_by uuid REFERENCES public.profiles (id) ON DELETE SET NULL,
  updated_by uuid REFERENCES public.profiles (id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.artists (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 80),
  aliases text[] NOT NULL DEFAULT '{}',
  styles public.music_style[] NOT NULL DEFAULT '{}',
  links text[] NOT NULL DEFAULT '{}',
  hidden boolean NOT NULL DEFAULT false,
  -- Null: imported data (demo, seed) that no user wrote.
  created_by uuid REFERENCES public.profiles (id) ON DELETE SET NULL,
  updated_by uuid REFERENCES public.profiles (id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE public.memberships (
  soundsystem_id uuid NOT NULL REFERENCES public.soundsystems (id) ON DELETE CASCADE,
  artist_id uuid NOT NULL REFERENCES public.artists (id) ON DELETE CASCADE,
  -- Null: imported data (demo, seed).
  created_by uuid REFERENCES public.profiles (id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (soundsystem_id, artist_id)
);

CREATE INDEX memberships_artist ON public.memberships (artist_id);

-- Authorship is set by the server, never trusted from the client.
CREATE FUNCTION public._stamp_directory_row()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    NEW.created_by := (select auth.uid());
    NEW.created_at := now();
  ELSE
    NEW.created_by := OLD.created_by;
    NEW.created_at := OLD.created_at;
  END IF;
  NEW.updated_by := coalesce((select auth.uid()), OLD.updated_by);
  NEW.updated_at := now();
  RETURN NEW;
END;
$$;

CREATE TRIGGER soundsystems_stamp BEFORE INSERT OR UPDATE ON public.soundsystems
  FOR EACH ROW EXECUTE FUNCTION public._stamp_directory_row();
CREATE TRIGGER artists_stamp BEFORE INSERT OR UPDATE ON public.artists
  FOR EACH ROW EXECUTE FUNCTION public._stamp_directory_row();

CREATE FUNCTION public._stamp_membership()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = ''
AS $$
BEGIN
  NEW.created_by := (select auth.uid());
  NEW.created_at := now();
  RETURN NEW;
END;
$$;

CREATE TRIGGER memberships_stamp BEFORE INSERT ON public.memberships
  FOR EACH ROW EXECUTE FUNCTION public._stamp_membership();

ALTER TABLE public.soundsystems ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.artists ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.memberships ENABLE ROW LEVEL SECURITY;

-- Read: everyone, hidden entries excepted.
CREATE POLICY soundsystems_select_visible
  ON public.soundsystems FOR SELECT
  TO anon, authenticated
  USING (NOT hidden);

-- Create and edit: any signed-in user with a profile (wiki). No delete: abuse is
-- handled by reports, and history keeps every version.
CREATE POLICY soundsystems_insert_signed_in
  ON public.soundsystems FOR INSERT
  TO authenticated
  WITH CHECK (NOT hidden AND EXISTS (SELECT 1 FROM public.profiles WHERE id = (select auth.uid())));

CREATE POLICY soundsystems_update_signed_in
  ON public.soundsystems FOR UPDATE
  TO authenticated
  USING (NOT hidden)
  WITH CHECK (NOT hidden AND EXISTS (SELECT 1 FROM public.profiles WHERE id = (select auth.uid())));

CREATE POLICY artists_select_visible
  ON public.artists FOR SELECT
  TO anon, authenticated
  USING (NOT hidden);

CREATE POLICY artists_insert_signed_in
  ON public.artists FOR INSERT
  TO authenticated
  WITH CHECK (NOT hidden AND EXISTS (SELECT 1 FROM public.profiles WHERE id = (select auth.uid())));

CREATE POLICY artists_update_signed_in
  ON public.artists FOR UPDATE
  TO authenticated
  USING (NOT hidden)
  WITH CHECK (NOT hidden AND EXISTS (SELECT 1 FROM public.profiles WHERE id = (select auth.uid())));

CREATE POLICY memberships_select_all
  ON public.memberships FOR SELECT
  TO anon, authenticated
  USING (true);

CREATE POLICY memberships_insert_signed_in
  ON public.memberships FOR INSERT
  TO authenticated
  WITH CHECK (EXISTS (SELECT 1 FROM public.profiles WHERE id = (select auth.uid())));

CREATE POLICY memberships_delete_signed_in
  ON public.memberships FOR DELETE
  TO authenticated
  USING (EXISTS (SELECT 1 FROM public.profiles WHERE id = (select auth.uid())));

-- Blocks ---------------------------------------------------------------------

CREATE TABLE public.blocks (
  blocker uuid NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  blocked uuid NOT NULL REFERENCES public.profiles (id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (blocker, blocked),
  CHECK (blocker <> blocked)
);

CREATE INDEX blocks_blocked ON public.blocks (blocked);

ALTER TABLE public.blocks ENABLE ROW LEVEL SECURITY;

CREATE POLICY blocks_select_own
  ON public.blocks FOR SELECT
  TO authenticated
  USING ((select auth.uid()) = blocker);

CREATE POLICY blocks_insert_own
  ON public.blocks FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = blocker);

CREATE POLICY blocks_delete_own
  ON public.blocks FOR DELETE
  TO authenticated
  USING ((select auth.uid()) = blocker);

-- Revisions (history and undo) ----------------------------------------------

CREATE TABLE public.revisions (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  entity text NOT NULL CHECK (entity IN ('soundsystem', 'artist')),
  entity_id uuid NOT NULL,
  -- Null: imported data (demo, seed) or a deleted account.
  author uuid REFERENCES public.profiles (id) ON DELETE SET NULL,
  -- Null on creation: there is no previous version.
  before jsonb,
  after jsonb NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX revisions_entity ON public.revisions (entity, entity_id, created_at DESC);
CREATE INDEX revisions_author ON public.revisions (author);

-- Written by the server only (SECURITY DEFINER: users have no INSERT right on revisions).
CREATE FUNCTION public._record_revision()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
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

CREATE TRIGGER soundsystems_revision AFTER INSERT OR UPDATE ON public.soundsystems
  FOR EACH ROW EXECUTE FUNCTION public._record_revision('soundsystem');
CREATE TRIGGER artists_revision AFTER INSERT OR UPDATE ON public.artists
  FOR EACH ROW EXECUTE FUNCTION public._record_revision('artist');

ALTER TABLE public.revisions ENABLE ROW LEVEL SECURITY;

-- History is public, minus the authors the reader has blocked.
CREATE POLICY revisions_select_unblocked
  ON public.revisions FOR SELECT
  TO anon, authenticated
  USING (
    author IS NULL
    OR NOT EXISTS (
      SELECT 1 FROM public.blocks
      WHERE blocks.blocker = (select auth.uid()) AND blocks.blocked = revisions.author
    )
  );

-- Reports --------------------------------------------------------------------

CREATE TABLE public.reports (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  entity text NOT NULL CHECK (entity IN ('soundsystem', 'artist')),
  entity_id uuid NOT NULL,
  author uuid NOT NULL DEFAULT auth.uid() REFERENCES public.profiles (id) ON DELETE CASCADE,
  reason text NOT NULL CHECK (char_length(reason) BETWEEN 1 AND 500),
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE (entity, entity_id, author)
);

CREATE INDEX reports_author ON public.reports (author);

ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;

CREATE POLICY reports_select_own
  ON public.reports FOR SELECT
  TO authenticated
  USING ((select auth.uid()) = author);

CREATE POLICY reports_insert_own
  ON public.reports FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = author);

-- Three reports from different people hide the entry until it is checked.
CREATE FUNCTION public._hide_reported()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_reports int;
BEGIN
  SELECT count(*) INTO v_reports
  FROM public.reports
  WHERE entity = NEW.entity AND entity_id = NEW.entity_id;

  IF v_reports >= 3 THEN
    IF NEW.entity = 'soundsystem' THEN
      UPDATE public.soundsystems SET hidden = true WHERE id = NEW.entity_id;
    ELSE
      UPDATE public.artists SET hidden = true WHERE id = NEW.entity_id;
    END IF;
  END IF;
  RETURN NULL;
END;
$$;

CREATE TRIGGER reports_hide AFTER INSERT ON public.reports
  FOR EACH ROW EXECUTE FUNCTION public._hide_reported();
