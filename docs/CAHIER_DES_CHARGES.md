# Teknotes — cahier des charges de la V1

Teknotes est un carnet de poche pour garder la trace des soundsystems et des artistes croisés en soirée tekno. L'annuaire des soundsystems et des artistes est rempli par ceux qui les croisent : personne ne le prépare à l'avance, personne ne valide chaque ajout (autosuffisance, comme dans Swikko).

Décisions prises au QCM du 29 octobre 2026, revues après le premier essai sur iPhone (2 novembre 2026 : sets artiste + soundsystem, styles libres, annuaire personnel, partage réservé aux comptes, parcours de compte, mode « champ » retiré, nouveau design), puis le même jour : design « Grands chiffres » choisi sur maquettes, classement personnel, code à l'ouverture, tuto, astuce, glissement entre écrans. Toute évolution de ce document se fait dans la même modification que le code concerné.

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
| Carnet perso hors ligne : soirées, sets (un artiste sur un soundsystem), note, commentaire | Suivre un soundsystem et voir ses nouvelles apparitions |
| Bouton « J'y suis », ajout en 2 appuis, dictée vocale | Stats « ma saison » avec image à partager en story |
| Annuaire personnel (ce que j'ai noté) et annuaire public : créer, modifier (façon wiki), chercher | Rappel du lendemain « note ta soirée » |
| Suggestions de doublons pendant la saisie, propositions de fusion | Soirée commune partagée entre plusieurs personnes |
| Signalement, masquage automatique, blocage d'un utilisateur | Fiches revendiquées par les soundsystems et artistes (vérifiées) |
| Compte facultatif (obligatoire pour contribuer) | Gardiens de l'annuaire choisis par la communauté |
| Connexion email + mot de passe et code à 6 chiffres reçu par mail ; Apple et Google quand les comptes développeur existent | Anglais |
| Export du carnet (JSON et CSV), suppression du compte | |
| Thème clair par défaut, thème sombre | |
| Tuto au premier lancement, code à l'ouverture de l'app, astuce quand le carnet est vide | |

Hors périmètre : carte, photos, reconnaissance de morceaux, mesure d'audience.

## 3. Écrans

Trois onglets : **Carnet · Annuaire · Profil**, dans une barre flottante. On passe de l'un à l'autre en glissant le doigt ; les écrans ouverts par-dessus arrivent aussi en glissant.

### Premier lancement et code
- Tuto en 3 pages, à passer ou à revoir depuis le Profil, puis choix d'un code (« Plus tard » possible).
- Code à 4 chiffres, propre au téléphone et sans rapport avec le compte. Demandé à l'ouverture et au retour dans l'app. 30 secondes d'attente après 5 erreurs. Face ID ou Touch ID en option sur iPhone ; pas encore d'empreinte sur Android.
- Il masque le carnet à qui prend le téléphone : les données elles-mêmes ne sont pas chiffrées. Un code oublié ne se récupère pas : il faut réinstaller, et ce qui n'est pas sur un compte est perdu. C'est écrit au moment de le choisir.

### Carnet
- Titre « Tek'Notes ! ». Liste des soirées par mois, la plus récente en haut : grand numéro du jour, titre, sets entendus, nombre de sets, moyenne de la nuit.
- Carnet vide : une **astuce** propose de créer sa première note. De même dans « Mon classement ».
- Bouton discret **« J'y suis »** en haut à droite : ouvre la soirée en cours, ou la crée.
  - Une soirée commence le soir et finit au matin : avant midi, « J'y suis » rattache à la soirée de la veille.
- Fiche soirée : date, titre, **sets**, souvenirs, puis en bas le département (facultatif) et le partage.
- Un **set** est un artiste qui joue sur un soundsystem, écrit « Rouille (Caisson Noir) » ; un seul des deux suffit si l'autre n'est pas connu, et l'app propose de le préciser. Les sets d'un même soundsystem restent groupés. Note de 1 à 5 (cinq grands chiffres) et commentaire par set.
- **Ajout en 2 appuis** : 1) champ de recherche, 2) toucher un résultat. Chercher un artiste propose ses soundsystems, chercher un soundsystem propose ses artistes. Si rien ne correspond, « Créer l'artiste "…" » ou « Créer le soundsystem "…" » en bas de la liste. Un artiste invité se rattache à un autre soundsystem depuis le set, sans toucher à l'annuaire.
- **Partager une nuit demande un compte** : sans compte, l'interrupteur est remplacé par un lien vers la création de compte. La règle est aussi dans le cœur partagé.
- Un toucher hors d'un champ referme le clavier, sur tous les écrans.
- Dictée vocale : un bouton micro dans la recherche et les commentaires (reconnaissance du système du téléphone), en plus de celle du clavier.

### Annuaire
- Recherche tolérante aux fautes et aux accents, qui marche aussi hors ligne (« teknokrat » trouve « Teknokrates »).
- **Mon classement** : soundsystems et artistes de mon carnet, de la meilleure moyenne à la moins bonne (puis les plus entendus ; ce qui n'est pas noté à la fin), avec un grand numéro de rang et un grand score. Filtre Tout / Artistes / Soundsystems. Marche sans compte ni réseau.
- **Public** : toutes les fiches, **sans aucune note**, ni dans la liste ni sur la fiche. Mêmes filtres, plus le style (liste cherchable, seulement les styles présents). Les artistes s'affichent « artiste (soundsystem) », les soundsystems avec leurs artistes.
- Fiche personnelle (depuis le classement) : grand score, rang, toutes mes notes, lien vers la fiche publique. Fiche publique : un lien « Voir mes notes » si j'en ai.
- Fiche soundsystem : nom, alias, styles, artistes, liens, fusions proposées, nuits partagées où il apparaît, et avec un compte : modifier, voir l'historique, signaler un doublon, signaler un problème. L'origine (département) n'apparaît qu'en petit, en bas.
- Fiche artiste : nom, alias, soundsystems, liens, styles, mêmes actions.
- Nuits partagées : date, département et pseudo, jamais les siennes. C'est là que le bouton « Partager » d'une nuit prend effet ; on peut y bloquer quelqu'un.
- Création : en tapant le nom, l'app propose les fiches proches (« Tu cherches peut-être Teknokrates ? ») avant de créer.

### Profil
- Sans compte : explication courte, et deux boutons qui mènent chacun à son écran : **Créer un compte** et **Se connecter** (celui-ci propose aussi un code par mail).
- Choix du pseudo : dernière étape de la première connexion, acceptation obligatoire des conditions d'utilisation.
- Avec compte : pseudo, état de la synchronisation, personnes bloquées, déconnexion, **suppression du compte**.
- Dans tous les cas : affichage (clair par défaut, sombre, système), code, export du carnet, revoir le tuto, conditions d'utilisation, confidentialité, contact.

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
| Set | soirée, artiste et/ou soundsystem (au moins un des deux), note 1 à 5, commentaire |

### Listes
- **Styles** : liste ouverte. L'app suggère tekno, tribe, hardtek, acid, mental, frenchcore, hardcore, speedcore, raggatek, techno, minimal, psytrance, drum & bass, dub, puis ce que l'annuaire contient déjà, les plus utilisés d'abord. Tout le monde peut en ajouter (32 caractères au plus, 12 par fiche) ; un mot qui ne diffère que par la casse ou les accents reprend le style existant, et une faute de frappe propose le style proche.
- **Départements** (liste fermée) : les 101 départements français, plus « Hors de France ».

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
6. Finitions : thèmes, accessibilité, version de test.

## 10. Où on en est

| Étape | État |
| --- | --- |
| 1. Fondations | Fait |
| 2. Carnet complet hors ligne | Fait |
| 3. Comptes et synchronisation | Fait pour l'email (mot de passe et code). **Apple et Google écartés pour l'instant** |
| 4. Annuaire partagé | Fait : modifier, historique, rétablir, fusions à trois |
| 5. Modération | Fait : signalement, blocage, conditions d'utilisation, confidentialité, suppression du compte |
| 6. Finitions | Fait : design « Grands chiffres » choisi sur maquettes (police du système, papier et encre sans couleur, rangs et scores énormes, filets), icônes, accessibilité (taille de texte, libellés lecteur d'écran, contrastes, cibles tactiles), dictée. **Reste la mise en production** |

Ce qui reste à faire demande un compte, une décision ou de l'argent : `docs/AVANT_DIFFUSION.md`.
