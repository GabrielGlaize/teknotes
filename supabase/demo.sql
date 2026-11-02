-- Demo data for testing on a real phone: four accounts, a notebook, nights shared by others,
-- an edit history and a duplicate waiting for approval. Loaded by scripts/iphone.sh after
-- seed.sql; never in production. Everything is fictional.
--
-- Account to sign in with: demo@teknotes.test / teknotes-demo (pseudo "Gabriel").

INSERT INTO auth.users (
  id, instance_id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
  confirmation_token, recovery_token, email_change, email_change_token_new
)
SELECT v.id::uuid, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', v.email,
  extensions.crypt('teknotes-demo', extensions.gen_salt('bf')), now(),
  '{"provider": "email", "providers": ["email"]}', '{}', now(), now(), '', '', '', ''
FROM (VALUES
  ('de000000-0000-4000-8000-000000000001', 'demo@teknotes.test'),
  ('de000000-0000-4000-8000-000000000002', 'brindille@teknotes.test'),
  ('de000000-0000-4000-8000-000000000003', 'moktar@teknotes.test'),
  ('de000000-0000-4000-8000-000000000004', 'galet@teknotes.test')
) AS v(id, email);

INSERT INTO auth.identities (id, user_id, provider_id, provider, identity_data, last_sign_in_at, created_at, updated_at)
SELECT gen_random_uuid(), u.id, u.id::text, 'email',
  jsonb_build_object('sub', u.id::text, 'email', u.email, 'email_verified', true), now(), now(), now()
FROM auth.users u WHERE u.email LIKE '%@teknotes.test';

INSERT INTO public.profiles (id, pseudo) VALUES
  ('de000000-0000-4000-8000-000000000001', 'Gabriel'),
  ('de000000-0000-4000-8000-000000000002', 'Brindille'),
  ('de000000-0000-4000-8000-000000000003', 'Moktar'),
  ('de000000-0000-4000-8000-000000000004', 'Galet');

-- Entries below use the fixed ids of seed.sql:
--   soundsystems 5d…01 Caisson Noir, 02 Les Ondes Sauvages, 03 Kollectif Brume, 04 Mur du Nord,
--                05 Tribu des Talus, 06 Teknokrates, 07 Basse Marée, 08 Onde de Choc
--   artists      5a…01 Rouille, 02 Lunatek, 03 Moktar Acid, 04 Kasba, 05 Djette Brindille,
--                06 Sub Zéro, 07 Grésil, 08 Talkie, 09 Pyl, 10 Écho Bleu

-- The notebook of the demo account ------------------------------------------------------------
INSERT INTO public.nights (id, owner, date, department, title, comment, shared) VALUES
  ('de100000-0000-4000-8000-000000000001', 'de000000-0000-4000-8000-000000000001', current_date - 9, '59', 'Teknival du Nord',
   'Le mur de son de Caisson Noir était énorme. Fini à 9 h, retour en covoiturage.', true),
  ('de100000-0000-4000-8000-000000000002', 'de000000-0000-4000-8000-000000000001', current_date - 23, '62', '',
   'Petite soirée en entrepôt, très dense.', false),
  ('de100000-0000-4000-8000-000000000003', 'de000000-0000-4000-8000-000000000001', current_date - 41, '29', 'Fest-noz sous les étoiles',
   '', true),
  ('de100000-0000-4000-8000-000000000004', 'de000000-0000-4000-8000-000000000001', current_date - 66, '31', '', '', false),
  ('de100000-0000-4000-8000-000000000005', 'de000000-0000-4000-8000-000000000001', current_date - 95, '56', 'Week-end au bord de l''eau',
   'Du dub à l''aube, parfait pour la descente.', false);

INSERT INTO public.night_entries (id, night_id, soundsystem_id, artist_id, rating, comment) VALUES
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000001', '5d000000-0000-4000-8000-000000000001', '5a000000-0000-4000-8000-000000000009', 5, 'Mur de son immense'),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000001', '5d000000-0000-4000-8000-000000000001', '5a000000-0000-4000-8000-000000000001', 4, 'Set hardtek très propre'),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000001', '5d000000-0000-4000-8000-000000000004', NULL, 3, ''),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000002', '5d000000-0000-4000-8000-000000000004', '5a000000-0000-4000-8000-000000000006', 5, 'Le meilleur frenchcore de l''année'),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000002', '5d000000-0000-4000-8000-000000000004', '5a000000-0000-4000-8000-000000000007', 4, ''),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000003', '5d000000-0000-4000-8000-000000000002', NULL, 4, ''),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000003', '5d000000-0000-4000-8000-000000000002', '5a000000-0000-4000-8000-000000000002', 5, 'Montée de malade'),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000003', '5d000000-0000-4000-8000-000000000002', '5a000000-0000-4000-8000-000000000005', NULL, ''),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000004', '5d000000-0000-4000-8000-000000000006', NULL, 4, ''),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000004', NULL, '5a000000-0000-4000-8000-000000000009', 3, ''),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000005', '5d000000-0000-4000-8000-000000000007', NULL, 5, 'Descente idéale'),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000005', NULL, '5a000000-0000-4000-8000-000000000010', 4, '');

-- Nights shared by other people (visible on the entries, once over) --------------------------
INSERT INTO public.nights (id, owner, date, department, title, shared) VALUES
  ('de100000-0000-4000-8000-000000000011', 'de000000-0000-4000-8000-000000000002', current_date - 12, '35', 'Open air breton', true),
  ('de100000-0000-4000-8000-000000000012', 'de000000-0000-4000-8000-000000000003', current_date - 20, '13', '', true),
  ('de100000-0000-4000-8000-000000000013', 'de000000-0000-4000-8000-000000000003', current_date - 3, '75', '', true),
  -- Tonight: shared, but hidden from everyone else until tomorrow.
  ('de100000-0000-4000-8000-000000000014', 'de000000-0000-4000-8000-000000000003', current_date, '75', 'Ce soir', true);

INSERT INTO public.night_entries (id, night_id, soundsystem_id, artist_id, rating, comment) VALUES
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000011', '5d000000-0000-4000-8000-000000000003', NULL, 5, ''),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000011', '5d000000-0000-4000-8000-000000000003', '5a000000-0000-4000-8000-000000000003', 4, ''),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000012', '5d000000-0000-4000-8000-000000000001', NULL, 4, ''),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000013', '5d000000-0000-4000-8000-000000000006', NULL, 5, ''),
  (gen_random_uuid(), 'de100000-0000-4000-8000-000000000014', '5d000000-0000-4000-8000-000000000001', NULL, NULL, '');

-- Edits by other people, so the history has authors -------------------------------------------
-- The server reads the author from the signed-in user; here it's given by hand.
BEGIN;
SELECT set_config('request.jwt.claims', '{"sub": "de000000-0000-4000-8000-000000000002", "role": "authenticated"}', true);
UPDATE public.soundsystems SET aliases = aliases || 'Caisson'::text WHERE id = '5d000000-0000-4000-8000-000000000001';
SELECT set_config('request.jwt.claims', '{"sub": "de000000-0000-4000-8000-000000000003", "role": "authenticated"}', true);
UPDATE public.soundsystems SET styles = styles || 'acid'::text WHERE id = '5d000000-0000-4000-8000-000000000001';
SELECT set_config('request.jwt.claims', '{"sub": "de000000-0000-4000-8000-000000000004", "role": "authenticated"}', true);
UPDATE public.soundsystems SET links = ARRAY['https://soundcloud.com/exemple-teknokrates'] WHERE id = '5d000000-0000-4000-8000-000000000006';
UPDATE public.artists SET aliases = aliases || 'Dj Rouille'::text WHERE id = '5a000000-0000-4000-8000-000000000001';

-- A duplicate created by someone else, and a merge waiting for one more approval.
SELECT set_config('request.jwt.claims', '{"sub": "de000000-0000-4000-8000-000000000003", "role": "authenticated"}', true);
INSERT INTO public.soundsystems (id, name, department, styles)
VALUES ('5d000000-0000-4000-8000-0000000000d1', 'Caisson Noir Sound', '59', '{hardtek}');
COMMIT;

INSERT INTO public.merge_proposals (id, entity, kept_id, absorbed_id, author)
OVERRIDING SYSTEM VALUE
VALUES (1, 'soundsystem', '5d000000-0000-4000-8000-000000000001', '5d000000-0000-4000-8000-0000000000d1', 'de000000-0000-4000-8000-000000000002');
INSERT INTO public.merge_votes (proposal_id, voter) VALUES
  (1, 'de000000-0000-4000-8000-000000000002'),
  (1, 'de000000-0000-4000-8000-000000000003');
SELECT setval(pg_get_serial_sequence('public.merge_proposals', 'id'), 1);
