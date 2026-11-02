#!/bin/bash
# Teknotes on a real iPhone, with a seeded server.
#
#   scripts/iphone.sh           server only (start, reset, demo data), prints what to do next
#   scripts/iphone.sh install   the same, then builds the app and installs it on the iPhone
#   scripts/iphone.sh install <device id>   if several devices are plugged in
#
# Needs Docker running, the iPhone plugged in and unlocked (Developer Mode on), on the same
# Wi-Fi as this Mac.
set -euo pipefail
cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
BUNDLE_ID="dev.gabrielglaize.teknotes"
PORT=55321

IP="$(ipconfig getifaddr en0 || ipconfig getifaddr en1 || true)"
if [ -z "$IP" ]; then
  echo "Le Mac n'a pas d'adresse sur le réseau : connecte-le au Wi-Fi." >&2
  exit 1
fi
URL="http://$IP:$PORT"

echo "==> Serveur"
docker info >/dev/null 2>&1 || { echo "Docker n'est pas lancé : ouvre Docker Desktop." >&2; exit 1; }
supabase start -x studio,imgproxy,vector,logflare,realtime,storage-api,supavisor >/dev/null
supabase db reset >/dev/null 2>&1
DB_URL="postgresql://postgres:postgres@127.0.0.1:55322/postgres"
psql "$DB_URL" -v ON_ERROR_STOP=1 -q -f supabase/demo.sql
echo "    données chargées : annuaire de démo, compte de démo, nuits, historique, fusion à approuver"

# What the iPhone will do: reach the server through the Mac's address.
if ! curl -fsS --max-time 5 "$URL/auth/v1/health" >/dev/null 2>&1; then
  echo "    ATTENTION : $URL ne répond pas. Vérifie le pare-feu du Mac (Réglages > Réseau > Pare-feu)." >&2
else
  echo "    serveur joignable sur $URL"
fi

if [ "${1:-}" = "install" ]; then
  echo "==> iPhone"
  DEVICE="${2:-}"
  if [ -z "$DEVICE" ]; then
    DEVICE="$(xcrun devicectl list devices 2>/dev/null | awk '/iPhone/ && /available/ {print $(NF-3)}' | head -1)"
  fi
  if [ -z "$DEVICE" ]; then
    echo "Aucun iPhone disponible. Branche-le, déverrouille-le, accepte « Faire confiance à cet ordinateur »," >&2
    echo "puis relance : scripts/iphone.sh install" >&2
    xcrun devicectl list devices >&2 || true
    exit 1
  fi
  echo "    appareil : $DEVICE"
  (cd iosApp && xcodebuild -project Teknotes.xcodeproj -scheme Teknotes -configuration Debug \
    -destination "id=$DEVICE" -derivedDataPath /tmp/teknotes-device -allowProvisioningUpdates \
    TEKNOTES_SUPABASE_URL="$URL" build 2>&1 | grep -E "error:|BUILD (SUCCEEDED|FAILED)" || true)
  APP="/tmp/teknotes-device/Build/Products/Debug-iphoneos/Teknotes.app"
  [ -d "$APP" ] || { echo "La compilation a échoué (voir au-dessus)." >&2; exit 1; }
  xcrun devicectl device install app --device "$DEVICE" "$APP"
  xcrun devicectl device process launch --device "$DEVICE" "$BUNDLE_ID" || \
    echo "    Installé. Si l'ouverture a échoué : fais confiance au développeur (voir plus bas), puis ouvre Teknotes."
fi

cat <<TXT

==> À faire sur l'iPhone
  Compte de démo : demo@teknotes.test  /  mot de passe : teknotes-demo   (pseudo Gabriel)
  Profil > Se connecter : le carnet de démo arrive, avec 5 nuits.
  Autres comptes : brindille@, moktar@, galet@teknotes.test (même mot de passe).
  Serveur : $URL  (l'iPhone doit être sur le même Wi-Fi que ce Mac, Mac allumé)
TXT
