# Teknotes — cahier des charges de la V1

Teknotes est un carnet de poche pour garder la trace des soundsystems et des artistes croisés en soirée tekno. L'annuaire des soundsystems et des artistes est rempli par ceux qui les croisent : personne ne le prépare à l'avance, personne ne valide chaque ajout (autosuffisance, comme dans Swikko).

Décisions prises au QCM du 29 octobre 2026. Toute évolution de ce document se fait dans la même modification que le code concerné.

## 1. Principes

1. **Discrétion d'abord.** Une partie de la scène n'est pas déclarée : l'app ne doit jamais rameuter du monde ni exposer un lieu.
   - Le lieu d'une soirée se limite au **département** (jamais de ville, d'adresse ni de point GPS, même en privé).
   - Une soirée partagée ne devient visible des autres **qu'une fois passée** (le lendemain de sa date au plus tôt).
   - **Aucune photo** de soirée. Pas de carte.
2. **Hors ligne d'abord.** Tout fonctionne sans réseau ; les modifications partent quand le réseau revient.
3. **Le carnet appartient à son auteur.** Les soirées sont privées par défaut ; l'export et la suppression du compte sont toujours possibles.
4. **Nuit et terrain.** Saisie en quelques secondes, d'une main, dans le noir.

## 2. Périmètre de la V1

État au 30 octobre 2026 : le code de la V1 est terminé, hors connexion Apple et Google (voir « Où on en est »).

| Dans la V1 | Plus tard (V2 et après) |
| --- | --- |
| Carnet perso hors ligne : soirées, soundsystems et artistes entendus, note, commentaire | Suivre un soundsystem et voir ses nouvelles apparitions |
| Bouton « J'y suis », ajout en 2 appuis, dictée vocale | Stats « ma saison » avec image à partager en story |
| Annuaire partagé : créer, modifier (façon wiki), chercher | Rappel du lendemain « note ta soirée » |
| Suggestions de doublons pendant la saisie, propositions de fusion | Soirée commune partagée entre plusieurs personnes |
| Signalement, masquage automatique, blocage d'un utilisateur | Fiches revendiquées par les soundsystems et artistes (vérifiées) |
| Compte facultatif (obligatoire pour contribuer) | Gardiens de l'annuaire choisis par la communauté |
| Connexion email + mot de passe et code à 6 chiffres reçu par mail ; Apple et Google quand les comptes développeur existent | Anglais |
| Export du carnet (JSON et CSV), suppression du compte | |
| Thème sombre et clair, mode « champ » | |

Hors périmètre : carte, photos, reconnaissance de morceaux, mesure d'audience.

## 3. Écrans

Trois onglets : **Carnet · Annuaire · Profil**.

### Carnet
- Liste des soirées, la plus récente en haut : date, département, nombre de sons notés.
- Bouton principal **« J'y suis »** : ouvre la soirée en cours, ou la crée.
  - Une soirée commence le soir et finit au matin : avant midi, « J'y suis » rattache à la soirée de la veille.
- Fiche soirée : date, département, liste des soundsystems et artistes entendus, note de 1 à 5 et commentaire par entrée, commentaire général, partage (privé / partagé).
- **Ajout en 2 appuis** : 1) champ de recherche dans l'annuaire, 2) toucher un résultat. Si rien ne correspond, « Créer "…" » en bas de la liste.
- Dictée vocale : un bouton micro dans la recherche et les commentaires (reconnaissance du système du téléphone), en plus de celle du clavier.

### Annuaire
- Recherche tolérante aux fautes et aux accents, qui marche aussi hors ligne (« teknokrat » trouve « Teknokrates »).
- Filtres : soundsystems / artistes, style, région d'origine.
- Fiche soundsystem : nom, alias, région d'origine, membres, liens, styles, fusions proposées, nuits partagées où il apparaît, et avec un compte : modifier, voir l'historique, signaler un doublon, signaler un problème.
- Fiche artiste : nom, alias, soundsystems, liens, styles, mêmes actions.
- Nuits partagées : date, département et pseudo, jamais les siennes. C'est là que le bouton « Partager » d'une nuit prend effet ; on peut y bloquer quelqu'un.
- Création : en tapant le nom, l'app propose les fiches proches (« Tu cherches peut-être Teknokrates ? ») avant de créer.

### Profil
- Sans compte : explication courte, boutons de connexion.
- Choix du pseudo : acceptation obligatoire des conditions d'utilisation.
- Avec compte : pseudo, état de la synchronisation, personnes bloquées, déconnexion, **suppression du compte**.
- Dans tous les cas : affichage (système / sombre / clair), mode « champ » (sombre, accent atténué, texte plus grand ; la luminosité du téléphone n'est pas modifiée), export du carnet, conditions d'utilisation, confidentialité, contact.

## 4. Données

### Annuaire (partagé, sur Supabase)
| Entité | Champs |
| --- | --- |
| Soundsystem | identifiant, nom, alias (liste), département d'origine, styles (liste fermée), liens, masqué, créé par, dates |
| Artiste | identifiant, nom, alias, styles, liens, masqué, créé par, dates |
| Membre | soundsystem, artiste |
| Révision | entité, identifiant, auteur, date, contenu avant / après (historique et retour arrière) |
| Signalement | entité, identifiant, auteur, motif, date |
| Fusion proposée | fiche gardée, fiche absorbée, auteur, votes |
| Profil | identifiant du compte, pseudo (unique), date |
| Blocage | qui bloque, qui est bloqué |
| Vote de fusion | proposition, votant |

### Carnet (sur le téléphone, copié sur Supabase seulement si le compte existe)
| Entité | Champs |
| --- | --- |
| Soirée | identifiant, date, département, titre facultatif, commentaire, partagée oui/non, dates |
| Entrée | soirée, soundsystem ou artiste, note 1 à 5, commentaire |

### Listes fermées
- **Styles** : tekno, tribe, hardtek, acid, mental, frenchcore, hardcore, speedcore, raggatek, techno, minimal, psytrance, drum & bass, dub.
- **Départements** : les 101 départements français, plus « Hors de France ».

## 5. Règles de l'annuaire (autosuffisance)

- Toute personne connectée crée et modifie les fiches. Chaque modification crée une révision ; n'importe qui peut revenir à une version précédente.
- En cas de conflit (deux modifications hors ligne), **la dernière modification gagne** ; l'autre reste dans l'historique.
- **Doublons** : suggestions pendant la saisie ; une fusion proposée s'applique quand 3 personnes différentes l'ont approuvée, proposant compris (seuil ajustable). La fiche gardée reprend le nom, les alias, les styles, les liens et les membres de l'autre ; les carnets la suivent. La fiche absorbée reste masquée sur le serveur, pour pouvoir annuler à la main.
- **Abus** : une fiche signalée par 3 personnes différentes est masquée en attendant une vérification (procédure dans `docs/AVANT_DIFFUSION.md`). Un utilisateur bloqué disparaît de l'historique et des nuits partagées visibles de celui qui l'a bloqué.
- **Historique** : ne contient que les changements de contenu. Rétablir une version crée une nouvelle modification : rien n'est perdu.
- Le pseudo est la seule information publique d'un contributeur.

## 6. Technique

| Sujet | Choix |
| --- | --- |
| Cœur partagé | Kotlin Multiplatform (`shared/`) : modèles, recherche, règles, base locale, synchronisation |
| Base locale | SQLDelight |
| iPhone | SwiftUI, iOS 17 minimum (`iosApp/`) |
| Android | Jetpack Compose, Android 8 minimum (`androidApp/`) |
| Écrans | MVVM avec un état immuable |
| Back-end | Supabase : Postgres, comptes, règles d'accès (RLS), migrations versionnées (`supabase/`) |
| Synchro | File d'attente locale des modifications, envoyée dès qu'il y a un compte et du réseau ; la dernière modification reçue gagne. Une fiche ou une nuit encore en attente n'est jamais écrasée par la copie du serveur. Sans réseau, nouvel essai automatique de plus en plus espacé (30 s à 5 min), et au retour dans l'app. Une suppression faite sur un autre téléphone est répercutée |
| Connexion par code | Un code à 6 chiffres plutôt qu'un lien : rien à ouvrir, même parcours sur les deux téléphones (`supabase/templates/code.html`) |
| Suppression de compte | Fonction serveur `supabase/functions/delete-account` : le profil et les nuits partent avec le compte, les fiches restent sans auteur, le carnet reste sur le téléphone |
| Tests | Cœur partagé bien couvert, quelques parcours d'écran |
| Automatisation | GitHub Actions (tests et builds), puis Codemagic pour TestFlight |
| Plantages | Rapports d'Xcode et de la Play Console, aucun service tiers |
| Mesure d'audience | Aucune |

## 7. Obligations des stores

- Suppression du compte depuis l'app (Apple et Google).
- Sign in with Apple proposé dès qu'une connexion Google existe.
- Contenus créés par les utilisateurs : conditions d'utilisation acceptées, signalement, blocage, contact visible.
- Fiches de confidentialité : App Privacy (Apple) et Data safety (Google).

## 8. Diffusion

- Test avec un petit groupe de proches d'abord. Android : fichier APK envoyé directement en attendant la Play Console. iPhone : TestFlight, qui demande le compte Apple Developer (99 € par an) à prendre au moment des tests.
- L'annuaire de la version de test est prérempli avec des données de démonstration (`supabase/seed.sql`), clairement fictives.

## 9. Ordre de construction

1. Fondations : dépôt, cœur partagé (modèles, recherche, base locale), deux apps qui affichent le carnet et l'annuaire locaux, schéma Supabase.
2. Carnet complet hors ligne : soirée, entrées, « J'y suis », ajout en 2 appuis, export.
3. Comptes et synchronisation avec Supabase.
4. Annuaire partagé : création, modification, historique, doublons.
5. Modération : signalement, masquage, blocage, suppression du compte.
6. Finitions : mode « champ », thèmes, accessibilité, version de test.

## 10. Où on en est

| Étape | État |
| --- | --- |
| 1. Fondations | Fait |
| 2. Carnet complet hors ligne | Fait |
| 3. Comptes et synchronisation | Fait pour l'email (mot de passe et code). **Apple et Google écartés pour l'instant** |
| 4. Annuaire partagé | Fait : modifier, historique, rétablir, fusions à trois |
| 5. Modération | Fait : signalement, blocage, conditions d'utilisation, confidentialité, suppression du compte |
| 6. Finitions | Fait : icônes, accessibilité (taille de texte, libellés lecteur d'écran, contrastes, cibles tactiles), dictée. **Reste la mise en production** |

Ce qui reste à faire demande un compte, une décision ou de l'argent : `docs/AVANT_DIFFUSION.md`.
