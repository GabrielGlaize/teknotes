# Teknotes — règles pour les agents

Lire d'abord `docs/CAHIER_DES_CHARGES.md` : c'est la référence. Toute évolution du comportement le met à jour dans la même modification.

## À ne jamais casser

- **Discrétion** : le lieu d'une soirée s'arrête au département. Jamais de ville, d'adresse, de GPS, de photo ni de carte. Une soirée partagée n'est visible des autres qu'une fois passée (règle appliquée côté serveur par la RLS, `supabase/migrations/*_notebook.sql`).
- **Hors ligne d'abord** : le carnet marche sans réseau et sans compte.
- **Les règles métier vivent dans `shared/`** (Kotlin). Les apps SwiftUI et Compose n'affichent que des écrans : pas de règle recopiée en Swift ou dans l'app Android.
- **Swift ne reçoit pas de Flow** : chaque flux exposé à iOS a sa fonction `watchXxx(onChange)` qui renvoie un `Watcher` à annuler.
- **Listes fermées** (styles, départements) : le Kotlin (`Style.kt`, `Departments.kt`) et le SQL (`*_reference_data.sql`) restent identiques. Les données de démo de `DemoData.kt` et `supabase/seed.sql` aussi.

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

## Pièges connus

- Le simulateur iOS doit être arm64 : `generic/platform=iOS Simulator` échoue (x86_64 non compilé par Kotlin).
- Le framework Kotlin est statique : l'app iOS lie SQLite elle-même (`OTHER_LDFLAGS = -lsqlite3`).
- AGP 9 : le module partagé utilise `com.android.kotlin.multiplatform.library`, pas `com.android.library`. Les bibliothèques Compose récentes exigent `compileSdk` 37.
