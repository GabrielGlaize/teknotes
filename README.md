# Teknotes

Carnet de poche pour garder la trace des soundsystems et des artistes croisés en soirée tekno. L'annuaire est rempli par ceux qui les croisent, sans personne pour valider chaque ajout.

Ce que fait la V1 et pourquoi : [`docs/CAHIER_DES_CHARGES.md`](docs/CAHIER_DES_CHARGES.md).

## Organisation

| Dossier | Contenu |
| --- | --- |
| `shared/` | Cœur partagé en Kotlin Multiplatform : modèles, recherche tolérante, règles, base locale SQLDelight |
| `androidApp/` | App Android en Jetpack Compose |
| `iosApp/` | App iPhone en SwiftUI (projet Xcode écrit à la main, dossiers synchronisés) |
| `supabase/` | Schéma, règles d'accès, données de démo et tests SQL du back-end |
| `design/fonts/` | Polices du style « Flyer » (licences libres jointes) |

## Prérequis

- Xcode 26, Android Studio (son JDK sert à Gradle), Docker pour Supabase en local, CLI Supabase.
- Gradle a besoin d'un JDK : `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

## Commandes

```bash
# Tests du cœur partagé (rapides, sur la JVM)
./gradlew :shared:jvmTest

# App Android : build, puis installation sur l'émulateur ou le téléphone branché
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk

# App iPhone : build pour un simulateur arm64 (le framework Kotlin est compilé par Xcode)
cd iosApp && xcodebuild -project Teknotes.xcodeproj -scheme Teknotes \
  -destination 'platform=iOS Simulator,name=iPhone 17' CODE_SIGNING_ALLOWED=NO build

# Supabase en local (ports 553xx, pour ne pas gêner d'autres projets)
supabase start -x studio,imgproxy,vector,logflare,edge-runtime,realtime,storage-api,supavisor
supabase db reset   # migrations + données de démo
supabase test db    # règles d'accès
supabase stop
```

Ouvrir dans les éditeurs : `iosApp/Teknotes.xcodeproj` dans Xcode, le dossier racine dans Android Studio.

## En mode debug

Tant que la synchronisation n'existe pas, les deux apps remplissent l'annuaire local avec les données de démo (`shared/.../DemoData.kt`, identiques à `supabase/seed.sql`). Elles sont clairement fictives.
