-- Access rules of the directory and the notebook, checked as anon and as real users.
-- Run with: supabase test db
BEGIN;
SELECT plan(23);

-- Four accounts: A, B and C have a profile, D has none yet.
INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password, raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
VALUES
  ('a0000000-0000-4000-8000-000000000001', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'a@teknotes.test', '', '{}', '{}', now(), now()),
  ('b0000000-0000-4000-8000-000000000002', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'b@teknotes.test', '', '{}', '{}', now(), now()),
  ('c0000000-0000-4000-8000-000000000003', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'c@teknotes.test', '', '{}', '{}', now(), now()),
  ('d0000000-0000-4000-8000-000000000004', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', 'd@teknotes.test', '', '{}', '{}', now(), now());
INSERT INTO public.profiles (id, pseudo) VALUES
  ('a0000000-0000-4000-8000-000000000001', 'Alpha'),
  ('b0000000-0000-4000-8000-000000000002', 'Bravo'),
  ('c0000000-0000-4000-8000-000000000003', 'Charlie');

-- Anonymous visitor ----------------------------------------------------------
SET LOCAL ROLE anon;
SELECT set_config('request.jwt.claims', '{"role": "anon"}', true);

SELECT is((SELECT count(*) FROM public.soundsystems), 8::bigint, 'anon reads the demo directory');
SELECT throws_ok(
  $$INSERT INTO public.soundsystems (name) VALUES ('Intrus')$$,
  '42501', NULL, 'anon cannot add a soundsystem');
SELECT is((SELECT count(*) FROM public.nights), 0::bigint, 'anon sees no night at all');

-- Signed in without a profile ------------------------------------------------
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claims', '{"sub": "d0000000-0000-4000-8000-000000000004", "role": "authenticated"}', true);
SELECT throws_ok(
  $$INSERT INTO public.soundsystems (name) VALUES ('Sans profil')$$,
  '42501', NULL, 'a contributor needs a profile');

-- A: creates and edits like a wiki ------------------------------------------
SELECT set_config('request.jwt.claims', '{"sub": "a0000000-0000-4000-8000-000000000001", "role": "authenticated"}', true);

INSERT INTO public.soundsystems (id, name, created_by)
VALUES ('5d000000-0000-4000-8000-0000000000aa', 'Nouveau Son', 'b0000000-0000-4000-8000-000000000002');
SELECT is(
  (SELECT created_by FROM public.soundsystems WHERE id = '5d000000-0000-4000-8000-0000000000aa'),
  'a0000000-0000-4000-8000-000000000001'::uuid,
  'the author is the signed-in user, whatever the client sends');

UPDATE public.soundsystems SET department = '13' WHERE id = '5d000000-0000-4000-8000-000000000001';
SELECT is(
  (SELECT count(*) FROM public.revisions WHERE entity_id = '5d000000-0000-4000-8000-000000000001'),
  2::bigint, 'every change of an entry is kept in its history');
SELECT is(
  (SELECT author FROM public.revisions WHERE entity_id = '5d000000-0000-4000-8000-000000000001' ORDER BY id DESC LIMIT 1),
  'a0000000-0000-4000-8000-000000000001'::uuid, 'the history names who changed the entry');
SELECT throws_ok(
  $$UPDATE public.soundsystems SET hidden = true WHERE id = '5d000000-0000-4000-8000-000000000002'$$,
  '42501', NULL, 'nobody hides an entry by hand');
SELECT throws_ok(
  $$INSERT INTO public.revisions (entity, entity_id, after) VALUES ('soundsystem', gen_random_uuid(), '{}')$$,
  '42501', NULL, 'history cannot be forged');

-- Three reports from different people hide an entry ---------------------------
INSERT INTO public.reports (entity, entity_id, reason) VALUES ('soundsystem', '5d000000-0000-4000-8000-000000000008', 'Faux nom');
SELECT throws_ok(
  $$INSERT INTO public.reports (entity, entity_id, reason) VALUES ('soundsystem', '5d000000-0000-4000-8000-000000000008', 'Encore')$$,
  '23505', NULL, 'one person reports an entry only once');
SELECT set_config('request.jwt.claims', '{"sub": "b0000000-0000-4000-8000-000000000002", "role": "authenticated"}', true);
INSERT INTO public.reports (entity, entity_id, reason) VALUES ('soundsystem', '5d000000-0000-4000-8000-000000000008', 'Faux nom');
SELECT is((SELECT count(*) FROM public.soundsystems WHERE id = '5d000000-0000-4000-8000-000000000008'), 1::bigint, 'two reports are not enough');
SELECT set_config('request.jwt.claims', '{"sub": "c0000000-0000-4000-8000-000000000003", "role": "authenticated"}', true);
INSERT INTO public.reports (entity, entity_id, reason) VALUES ('soundsystem', '5d000000-0000-4000-8000-000000000008', 'Faux nom');
SELECT is((SELECT count(*) FROM public.soundsystems WHERE id = '5d000000-0000-4000-8000-000000000008'), 0::bigint, 'the third report hides the entry');

-- Notebook: private by default, visible to others only once over -------------
SELECT set_config('request.jwt.claims', '{"sub": "a0000000-0000-4000-8000-000000000001", "role": "authenticated"}', true);
INSERT INTO public.nights (id, date, department, shared) VALUES
  ('11000000-0000-4000-8000-000000000001', current_date - 7, '59', false),
  ('11000000-0000-4000-8000-000000000002', current_date - 1, '62', true),
  ('11000000-0000-4000-8000-000000000003', current_date, '59', true);
INSERT INTO public.night_entries (id, night_id, soundsystem_id, rating)
VALUES ('12000000-0000-4000-8000-000000000001', '11000000-0000-4000-8000-000000000002', '5d000000-0000-4000-8000-000000000001', 5);

SELECT throws_ok(
  $$INSERT INTO public.night_entries (id, night_id, soundsystem_id, rating) VALUES (gen_random_uuid(), '11000000-0000-4000-8000-000000000001', '5d000000-0000-4000-8000-000000000001', 6)$$,
  '23514', NULL, 'a rating goes from 1 to 5');
SELECT lives_ok(
  $$INSERT INTO public.night_entries (id, night_id, soundsystem_id, artist_id) VALUES (gen_random_uuid(), '11000000-0000-4000-8000-000000000001', '5d000000-0000-4000-8000-000000000001', '5a000000-0000-4000-8000-000000000001')$$,
  'a set names an artist on a soundsystem');
SELECT throws_ok(
  $$INSERT INTO public.night_entries (id, night_id) VALUES (gen_random_uuid(), '11000000-0000-4000-8000-000000000001')$$,
  '23514', NULL, 'and names at least one of the two');
SELECT throws_ok(
  $$UPDATE public.soundsystems SET styles = ARRAY[repeat('x', 33)] WHERE id = '5d000000-0000-4000-8000-000000000001'$$,
  '23514', NULL, 'a new style stays short');

SELECT set_config('request.jwt.claims', '{"sub": "b0000000-0000-4000-8000-000000000002", "role": "authenticated"}', true);
SELECT results_eq(
  $$SELECT id FROM public.nights$$,
  $$VALUES ('11000000-0000-4000-8000-000000000002'::uuid)$$,
  'others only see shared nights that are over');
SELECT is((SELECT count(*) FROM public.night_entries), 1::bigint, 'and what was heard during them');
SELECT throws_ok(
  $$INSERT INTO public.night_entries (id, night_id, artist_id) VALUES (gen_random_uuid(), '11000000-0000-4000-8000-000000000002', '5a000000-0000-4000-8000-000000000001')$$,
  '42501', NULL, 'nobody writes in someone else''s notebook');
UPDATE public.nights SET comment = 'piraté' WHERE id = '11000000-0000-4000-8000-000000000002';
SELECT is(
  (SELECT comment FROM public.nights WHERE id = '11000000-0000-4000-8000-000000000002'),
  '', 'nor changes it');

INSERT INTO public.blocks (blocker, blocked) VALUES ('b0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000001');
SELECT is((SELECT count(*) FROM public.nights), 0::bigint, 'a blocked person''s nights disappear');

-- Deleting an account keeps what it added to the directory, without author ---
RESET ROLE;
DELETE FROM auth.users WHERE id = 'a0000000-0000-4000-8000-000000000001';
SELECT is(
  (SELECT created_by FROM public.soundsystems WHERE id = '5d000000-0000-4000-8000-0000000000aa'),
  NULL, 'a deleted account leaves its soundsystems without author');
SELECT is((SELECT count(*) FROM public.nights WHERE id = '11000000-0000-4000-8000-000000000002'), 0::bigint, 'and takes its nights with it');

SELECT * FROM finish();
ROLLBACK;
