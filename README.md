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

# Les mêmes, avec les vrais allers-retours contre le Supabase local (supabase start)
TEKNOTES_SUPABASE_URL=http://127.0.0.1:55321 \
TEKNOTES_SUPABASE_KEY="$(supabase status -o env | grep ^ANON_KEY= | cut -d'"' -f2)" \
TEKNOTES_MAIL_URL=http://127.0.0.1:55324 \
./gradlew :shared:jvmTest --rerun-tasks

# Tests d'écran : Android (émulateur lancé), iPhone (simulateur, Supabase local démarré)
./gradlew :androidApp:connectedDebugAndroidTest
(cd iosApp && xcodebuild test -project Teknotes.xcodeproj -scheme Teknotes \
  -destination 'platform=iOS Simulator,name=iPhone 17' CODE_SIGNING_ALLOWED=NO)

# App Android : build, puis installation sur l'émulateur ou le téléphone branché
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk

# App iPhone : build pour un simulateur arm64 (le framework Kotlin est compilé par Xcode)
cd iosApp && xcodebuild -project Teknotes.xcodeproj -scheme Teknotes \
  -destination 'platform=iOS Simulator,name=iPhone 17' CODE_SIGNING_ALLOWED=NO build

# Supabase en local (ports 553xx, pour ne pas gêner d'autres projets)
supabase start -x studio,imgproxy,vector,logflare,realtime,storage-api,supavisor
supabase db reset   # migrations + données de démo
supabase test db    # règles d'accès
supabase stop
```

Ouvrir dans les éditeurs : `iosApp/Teknotes.xcodeproj` dans Xcode, le dossier racine dans Android Studio.

## En mode debug

- Les deux apps parlent au Supabase local : `http://10.0.2.2:55321` sur l'émulateur Android, `http://127.0.0.1:55321` sur le simulateur iOS. Pour un vrai téléphone Android : `./gradlew :androidApp:assembleDebug -Pteknotes.supabaseUrl=http://<IP du Mac>:55321`.
- Les mails de connexion arrivent dans Mailpit : http://127.0.0.1:55324.
- Avant la première synchro, ou sans serveur, l'annuaire local contient les données de démo (`shared/.../DemoData.kt`, identiques à `supabase/seed.sql`). Elles sont clairement fictives.
- Une version release sans `teknotes.supabaseUrl` fonctionne hors ligne, sans compte.
