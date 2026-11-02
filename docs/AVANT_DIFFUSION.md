# Teknotes — avant la diffusion

Ce qui reste à faire par un humain avant de donner l'app à des testeurs, puis de la publier. Le code de la V1 est prêt ; rien de cette liste ne se fait sans compte, sans décision ou sans argent.

## 1. Décisions et textes

- [ ] Créer l'adresse de contact affichée dans l'app (`Legal.CONTACT_EMAIL` dans `shared/.../Legal.kt`, aujourd'hui `contact@gabrielglaize.dev`, qui n'existe pas encore).
- [ ] Faire relire les conditions d'utilisation et la confidentialité (`Legal.kt`). Ce sont des brouillons, pas un avis juridique. Vérifier notamment l'âge minimum (16 ans) et la mention de l'hébergeur.
- [ ] Publier ces deux textes sur une page web : les stores demandent une URL de politique de confidentialité.

## 2. Supabase en production

- [ ] Créer le projet, **région Union européenne**.
- [ ] `supabase link`, puis `supabase db push` : toutes les migrations.
- [ ] `supabase functions deploy delete-account`.
- [ ] Ne pas lancer `seed.sql` en production, sauf pour la version de test : ses fiches sont fictives.
- [ ] Configurer un vrai envoi de mails (SMTP) : celui de Supabase est très limité. Reprendre le gabarit `supabase/templates/code.html` pour les mails « magic link » et « confirmation », sans quoi le mail contient un lien au lieu du code.
- [ ] Activer la confirmation d'email (`enable_confirmations`) si l'inscription par mot de passe doit la demander. L'app gère ce cas : après l'inscription, le compte reste déconnecté tant que le mail n'est pas confirmé. À tester, car le parcours local ne l'active pas.
- [ ] Fixer la limite de mails (`email_sent`, mise à 30 par heure en local pour les tests).
- [ ] Sauvegardes : vérifier ce que comprend le plan choisi.

Brancher ensuite les apps :
- Android : `./gradlew :androidApp:assembleRelease -Pteknotes.supabaseUrl=https://… -Pteknotes.supabaseKey=…`
- iPhone : la configuration release dans `iosApp/Teknotes/TeknotesApp.swift` (`config = nil` aujourd'hui).

Seule la clé publique (anon) va dans les apps. La clé de service ne quitte jamais le serveur.

## 3. Version de test

- **Android** : signer l'APK avec une clé à toi (à garder précieusement : la perdre empêche toute mise à jour), puis l'envoyer directement. Ou compte Play Console (25 $, une fois) pour un test fermé.
- **iPhone** : compte Apple Developer (99 €/an), puis TestFlight. Sans lui, l'app ne s'installe que sur un iPhone branché au Mac, et expire au bout de 7 jours.
- Identifiant de l'app sur les deux plateformes : `dev.gabrielglaize.teknotes`.
- Dictée à essayer sur de vrais téléphones : le micro ne se teste pas sur simulateur.

## 4. Connexion Apple et Google

Hors V1 pour l'instant, à la demande. Ces deux connexions demandent le compte Apple Developer, un projet Google Cloud et des identifiants OAuth. Rappel : proposer Google sur iPhone oblige à proposer aussi une connexion respectueuse de la vie privée, en pratique Sign in with Apple.

## 5. Fiches des stores

### App Privacy (Apple) et Data safety (Google)

| Donnée | Collectée | Liée à l'utilisateur | Pourquoi | Partagée | Pistage |
| --- | --- | --- | --- | --- | --- |
| Adresse email | Oui, avec un compte | Oui | Connexion | Non | Non |
| Pseudo | Oui, avec un compte | Oui | Affiché dans l'annuaire | Visible des autres utilisateurs | Non |
| Contenu utilisateur (carnet, fiches) | Oui, avec un compte | Oui | Fonction de l'app | Fiches et nuits partagées visibles des autres | Non |
| Position | Non | | Le département est choisi à la main | | |
| Photos, contacts | Non | | | | |
| Audio | Non conservé | | Dictée : traitée par le système du téléphone, aucun son n'est envoyé à nos serveurs | | |
| Mesure d'audience, publicité | Non | | | | |
| Diagnostics | Rapports fournis par Apple et Google uniquement | Non | Stabilité | Non | Non |

Autres réponses utiles :
- Chiffrement en transit : oui (HTTPS, une fois en production).
- Suppression du compte : oui, depuis le profil. Google demande aussi une page web pour en faire la demande.
- Export iOS : pas de chiffrement propre à l'app, donc `ITSAppUsesNonExemptEncryption` à `NO`.
- Âge conseillé : à déclarer en cohérence avec les conditions (le sujet touche aux fêtes, l'app ne vend rien).

### Contrôle Apple sur les contenus d'utilisateurs (règle 1.2)

- [x] Filtrage et signalement : signalement avec motifs, masquage automatique au 3e.
- [x] Blocage d'un utilisateur : depuis l'historique et les nuits partagées, liste dans le profil.
- [x] Acceptation des conditions : obligatoire pour choisir un pseudo, donc pour contribuer.
- [ ] Contact visible : affiché dans le profil, **adresse à créer** (voir 1).
- [ ] Réagir aux signalements sous 24 h : voir la procédure ci-dessous.

## 6. Modération au quotidien

Les fiches signalées 3 fois sont masquées automatiquement. Il reste à les vérifier, dans l'éditeur SQL de Supabase :

```sql
-- Fiches masquées, avec les signalements (les fiches fusionnées ont merged_into rempli)
select s.id, s.name, s.merged_into, array_agg(r.reason) as motifs
from soundsystems s left join reports r on r.entity = 'soundsystem' and r.entity_id = s.id
where s.hidden group by s.id;

-- Fiche à tort masquée : la rétablir et effacer ses signalements
update soundsystems set hidden = false where id = '…';
delete from reports where entity = 'soundsystem' and entity_id = '…';

-- Fusion à annuler : réafficher la fiche absorbée. Les membres et les carnets déplacés
-- restent sur la fiche gardée, à reprendre à la main si besoin.
update soundsystems set hidden = false, merged_into = null where id = '…';

-- Compte abusif : supprimer l'utilisateur depuis Authentication > Users.
-- Ses fiches restent, sans auteur.
```

Remplacer `soundsystems` et `'soundsystem'` par `artists` et `'artist'` pour une fiche artiste.
