# Teknotes — règles pour les agents

Lire d'abord `docs/CAHIER_DES_CHARGES.md` : c'est la référence. Toute évolution du comportement le met à jour dans la même modification.

## À ne jamais casser

- **Discrétion** : le lieu d'une soirée s'arrête au département. Jamais de ville, d'adresse, de GPS, de photo ni de carte. Une soirée partagée n'est visible des autres qu'une fois passée (règle appliquée côté serveur par la RLS, `supabase/migrations/*_notebook.sql`).
- **Hors ligne d'abord** : le carnet marche sans réseau et sans compte.
- **Les règles métier vivent dans `shared/`** (Kotlin). Les apps SwiftUI et Compose n'affichent que des écrans : pas de règle recopiée en Swift ou dans l'app Android.
- **Swift ne reçoit pas de Flow** : chaque flux exposé à iOS a sa fonction `watchXxx(onChange)` qui renvoie un `Watcher` à annuler. Les fonctions `suspend` exposées portent `@Throws` et lèvent `TeknotesException`, dont le message s'affiche tel quel : sans `@Throws`, une exception fait planter l'app iOS.
- **Ce qui demande le serveur et un compte passe par `Community`** (historique, fusions, signalements, blocages, nuits partagées) ; le carnet n'en dépend jamais.
- **Toute modification locale passe par l'outbox** (`Outbox.record`) : sinon elle ne part jamais sur le serveur.
- **Départements** (liste fermée) : le Kotlin (`Departments.kt`) et le SQL (`*_reference_data.sql`) restent identiques. Les données de démo de `DemoData.kt` et `supabase/seed.sql` aussi. Les styles sont une liste ouverte (`Style.kt` ne fait que suggérer).
- **Partager une nuit demande un compte** : la règle est dans `NotebookRepository.updateNight`, les écrans ne font que l'annoncer.
- **Design** : « Grands chiffres » : police du système, papier et encre sans autre couleur, rangs et scores énormes, filets. Le public n'affiche jamais de note. Composants dans `iosApp/Teknotes/Theme.swift` et `androidApp/.../ui/Common.kt`.

## Supabase

- Une migration poussée sur un environnement partagé ne se modifie plus : on en crée une nouvelle.
- RLS par action, `(select auth.uid())`, `search_path = ''` sur les fonctions, un test pgTAP par règle (`supabase/tests/`).
- Ports locaux 553xx (`supabase/config.toml`) : un autre projet utilise les ports par défaut sur cette machine.

## Vérifier avant de rendre la main

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :shared:jvmTest :androidApp:assembleDebug
(cd iosApp && xcodebuild -project Teknotes.xcodeproj -scheme Teknotes -destination 'platform=iOS Simulator,name=iPhone 17' CODE_SIGNING_ALLOWED=NO build)
supabase db reset && supabase test db   # si supabase/ a changé
```

Pour la synchro et les écrans, voir les commandes de test du README (tests contre le Supabase local, tests d'écran sur émulateur et simulateur).

## Pièges connus

- **Déclencheurs et cascades** : la suppression d'un compte passe `created_by` et `updated_by` à NULL par cascade. `_stamp_directory_row` laisse donc passer les mises à jour venues d'un autre déclencheur (`pg_trigger_depth() > 1`), et `_record_revision` n'enregistre que les changements de contenu : `pg_trigger_depth()` vaut 1 dans les déclencheurs AFTER d'une cascade, il ne peut pas servir là.
- `verify_jwt = false` sur `delete-account` : la vérification de la plateforme ne lit pas les jetons ES256. La fonction vérifie elle-même l'appelant avec `auth.getUser()`.
- **iCloud** : le dossier Documents est synchronisé et laisse des copies « x 2 » dans les dossiers de build, ce qui casse la compilation. Les builds Gradle vont donc dans `build.nosync`. Si ça revient : effacer les dossiers `build.nosync`.
- **SQLDelight** : un changement du schéma local s'accompagne d'un fichier de migration (`shared/src/commonMain/sqldelight/migrations/N.sqm`), sinon les téléphones existants plantent.
- **Tests avec synchro** : le pilote SQLite en mémoire partage une connexion entre les fils ; les « téléphones » des tests utilisent un fichier temporaire.
- Le simulateur iOS est arm64 seulement : le cœur Kotlin n'est pas compilé pour x86_64 (`EXCLUDED_ARCHS` dans le projet).
- Le framework Kotlin est statique : l'app iOS lie SQLite elle-même (`OTHER_LDFLAGS = -lsqlite3`).
- AGP 9 : le module partagé utilise `com.android.kotlin.multiplatform.library`, pas `com.android.library`. Les bibliothèques Compose récentes exigent `compileSdk` 37.
