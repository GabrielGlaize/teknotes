-- Merging duplicates by agreement: every error code, then the third approval.
BEGIN;
SELECT plan(15);

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

-- A note in D-free night referencing the entry that will be absorbed.
INSERT INTO public.nights (id, owner, date) VALUES ('11000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000002', current_date - 3);
INSERT INTO public.night_entries (id, night_id, soundsystem_id)
VALUES ('12000000-0000-4000-8000-000000000001', '11000000-0000-4000-8000-000000000001', '5d000000-0000-4000-8000-000000000003');

SET LOCAL ROLE anon;
SELECT set_config('request.jwt.claims', '{"role": "anon"}', true);
SELECT throws_ok(
  $$SELECT public.propose_merge('soundsystem', '5d000000-0000-4000-8000-000000000001', '5d000000-0000-4000-8000-000000000003')$$,
  '42501', NULL, 'anon cannot call the merge functions');

SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claims', '{"sub": "d0000000-0000-4000-8000-000000000004", "role": "authenticated"}', true);
SELECT throws_ok(
  $$SELECT public.propose_merge('soundsystem', '5d000000-0000-4000-8000-000000000001', '5d000000-0000-4000-8000-000000000003')$$,
  'P0001', 'Connecte-toi pour proposer une fusion.', 'a proposer needs a profile (not_authenticated)');

SELECT set_config('request.jwt.claims', '{"sub": "a0000000-0000-4000-8000-000000000001", "role": "authenticated"}', true);
SELECT throws_ok(
  $$SELECT public.propose_merge('soundsystem', '5d000000-0000-4000-8000-000000000001', '5d000000-0000-4000-8000-000000000001')$$,
  'P0001', 'Une fiche ne peut pas fusionner avec elle-même.', 'merge_same_entry');
SELECT throws_ok(
  $$SELECT public.propose_merge('soundsystem', '5d000000-0000-4000-8000-000000000001', gen_random_uuid())$$,
  'P0001', 'Une de ces fiches n''est plus dans l''annuaire.', 'merge_entry_not_found');

SELECT is(
  (public.propose_merge('soundsystem', '5d000000-0000-4000-8000-000000000001', '5d000000-0000-4000-8000-000000000003') ->> 'approvals'),
  '1', 'the proposer''s approval counts');
SELECT throws_ok(
  $$SELECT public.propose_merge('soundsystem', '5d000000-0000-4000-8000-000000000003', '5d000000-0000-4000-8000-000000000001')$$,
  'P0001', 'Cette fusion est déjà proposée.', 'merge_already_proposed, even the other way round');
SELECT throws_ok(
  $$INSERT INTO public.merge_votes (proposal_id, voter) SELECT id, 'b0000000-0000-4000-8000-000000000002' FROM public.merge_proposals$$,
  '42501', NULL, 'votes cannot be forged');
SELECT throws_ok(
  $$SELECT public.approve_merge((SELECT id FROM public.merge_proposals))$$,
  'P0001', 'Tu as déjà approuvé cette fusion.', 'merge_already_approved');

SELECT set_config('request.jwt.claims', '{"sub": "b0000000-0000-4000-8000-000000000002", "role": "authenticated"}', true);
SELECT is(
  (public.approve_merge((SELECT id FROM public.merge_proposals)) ->> 'applied'),
  'false', 'two approvals are not enough');

SELECT set_config('request.jwt.claims', '{"sub": "c0000000-0000-4000-8000-000000000003", "role": "authenticated"}', true);
SELECT is(
  (public.approve_merge((SELECT id FROM public.merge_proposals)) ->> 'applied'),
  'true', 'the third approval applies the merge');

SELECT is((SELECT count(*) FROM public.soundsystems WHERE id = '5d000000-0000-4000-8000-000000000003'), 0::bigint, 'the absorbed entry disappears from the directory');
SELECT ok(
  (SELECT 'Kollectif Brume' = ANY(aliases) AND 'Brume' = ANY(aliases) AND 'mental' = ANY(styles) FROM public.soundsystems WHERE id = '5d000000-0000-4000-8000-000000000001'),
  'the kept entry takes its name, aliases and styles');
SELECT is(
  (SELECT count(*) FROM public.memberships WHERE soundsystem_id = '5d000000-0000-4000-8000-000000000001' AND artist_id = '5a000000-0000-4000-8000-000000000003'),
  1::bigint, 'and its members');

RESET ROLE;
SELECT is(
  (SELECT soundsystem_id FROM public.night_entries WHERE id = '12000000-0000-4000-8000-000000000001'),
  '5d000000-0000-4000-8000-000000000001'::uuid, 'notebooks now point to the kept entry');

SET LOCAL ROLE authenticated;
SELECT throws_ok(
  $$SELECT public.approve_merge((SELECT id FROM public.merge_proposals))$$,
  'P0001', 'Cette proposition n''existe plus.', 'merge_proposal_not_found once applied');

SELECT * FROM finish();
ROLLBACK;
