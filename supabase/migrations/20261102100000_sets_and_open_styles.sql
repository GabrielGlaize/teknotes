-- A set is an artist playing on a soundsystem: an entry can now name both.
ALTER TABLE public.night_entries DROP CONSTRAINT night_entries_check;
ALTER TABLE public.night_entries ADD CONSTRAINT night_entries_names_someone
  CHECK (soundsystem_id IS NOT NULL OR artist_id IS NOT NULL);

-- Styles become an open list: anyone can add one, within reasonable limits.
CREATE FUNCTION public._styles_are_valid(p_styles text[])
RETURNS boolean
LANGUAGE sql
IMMUTABLE
SET search_path = ''
AS $$
  SELECT cardinality(p_styles) <= 12
    AND NOT EXISTS (SELECT 1 FROM unnest(p_styles) AS s WHERE char_length(btrim(s)) NOT BETWEEN 1 AND 32);
$$;

ALTER TABLE public.soundsystems
  ALTER COLUMN styles DROP DEFAULT,
  ALTER COLUMN styles TYPE text[] USING styles::text[],
  ALTER COLUMN styles SET DEFAULT '{}',
  ADD CONSTRAINT soundsystems_styles_valid CHECK (public._styles_are_valid(styles));

ALTER TABLE public.artists
  ALTER COLUMN styles DROP DEFAULT,
  ALTER COLUMN styles TYPE text[] USING styles::text[],
  ALTER COLUMN styles SET DEFAULT '{}',
  ADD CONSTRAINT artists_styles_valid CHECK (public._styles_are_valid(styles));

DROP TYPE public.music_style;
