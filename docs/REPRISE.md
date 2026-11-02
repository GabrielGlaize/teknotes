# Teknotes — reprendre le travail (lire ça d'abord)

Ce fichier remplace la lecture de l'historique d'une longue conversation. Une nouvelle session n'a besoin que de lui, d'`AGENTS.md` et, au besoin, du cahier des charges.

## Où on en est (2 novembre 2026)
Le code de la V1 est terminé et testé, sauf : connexion Apple/Google et projet Supabase de production (écartés volontairement). Reste à faire : `docs/AVANT_DIFFUSION.md`. Détail fonctionnel : `docs/CAHIER_DES_CHARGES.md` (section 10).

## Commandes utiles (une ligne chacune)
- Tout vérifier côté cœur : `./gradlew :shared:jvmTest` (JAVA_HOME = JDK d'Android Studio, voir README).
- Serveur de démo + iPhone : `scripts/iphone.sh install` (compte `demo@teknotes.test` / `teknotes-demo`).
- Règles d'accès du serveur : `supabase db reset && supabase test db`.

## Pièges déjà rencontrés (ne pas les redécouvrir)
Voir `AGENTS.md`, section « Pièges connus » : iCloud (`build.nosync`), cascades de déclencheurs, `@Throws`, migrations SQLDelight, simulateur arm64, `verify_jwt` de la fonction de suppression.
Spécifique à l'iPhone : `xcodebuild` veut l'UDID matériel, `devicectl` l'identifiant CoreDevice ; le script gère les deux. Au premier lancement : Réglages > Général > VPN et gestion de l'appareil > faire confiance au compte développeur.

## Économiser du contexte (consignes pour l'agent)
- Ne pas relire les gros fichiers en entier : lire par plage (`offset`/`limit`) ou chercher avec `grep`.
- Filtrer les sorties (`grep -E "error|BUILD|FAILED"`, `| tail -20`) : ne jamais afficher un log de build complet.
- Les captures d'écran coûtent cher : n'en prendre que pour un changement visuel, en réduisant (largeur 300 px).
- Lancer les vérifications longues en une seule commande qui ne résume que le résultat.
- Ne pas redécrire le projet : renvoyer vers ce fichier et le cahier des charges.

## État de l'essai sur iPhone (2 novembre 2026)
App installée sur l'iPhone 15 de Gabriel par `scripts/iphone.sh install`. Reste à lui : faire confiance au développeur, ouvrir l'app, se connecter avec le compte de démo. Ses retours d'essai (saisie de nuit, mode champ, micro, synchro) sont la prochaine source de travail. Le serveur de démo n'est utile que pendant l'essai : `supabase stop` ensuite.

## À faire ensuite (par priorité)
1. Corriger ce que l'essai sur iPhone révèle.
2. Créer l'adresse de contact (`Legal.CONTACT_EMAIL`) et faire relire les textes légaux (`docs/AVANT_DIFFUSION.md`).
3. À la demande seulement : connexion Apple/Google, Supabase de production, Apple Developer / Play Console.
4. Proposé, pas fait : sortir les dépôts du dossier Documents (iCloud), qui génère des notes « fichier modifié » coûteuses en contexte.

## Projets liés
- Portfolio `~/Documents/00dev/FREELANCE/PROJETS/00-Portfolio/gabrielglaize-dev` (Next.js, flyer rose). La page du projet Carnet de sets y dit encore « Flutter » : à remplacer par Teknotes (KMP, SwiftUI, Compose, Supabase) à la sortie de la V1, pas avant. Son dossier `.next` souffre du même problème iCloud que les builds d'ici.
- Contexte plus large (objectif de revenus, choix de design) : mémoire de l'agent, fichiers `carnet-de-sets` et `portfolio-perso-gabrielglaize`.
