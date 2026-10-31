#!/usr/bin/env bash
# Compila Essenziale senza Gradle: javac -> dx -> aapt -> zipalign -> apksigner.
# Richiede (Ubuntu): android-sdk-platform-23 aapt dalvik-exchange zipalign apksigner, JDK 11+.
set -euo pipefail
cd "$(dirname "$0")"
JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
rm -rf build && mkdir -p build/classes
if ! javac -nowarn -Xlint:none --release 8 -encoding UTF-8 -cp "$JAR" -d build/classes $(find src -name '*.java') 2> build/javac.log; then
  grep -v -e '^warning' -e JAVA_TOOL_OPTIONS build/javac.log; exit 1
fi
dalvik-exchange --dex --output=build/classes.dex build/classes
aapt package -f -M AndroidManifest.xml -S res -I "$JAR" -F build/unsigned.apk
(cd build && aapt add unsigned.apk classes.dex >/dev/null)
zipalign -f 4 build/unsigned.apk build/aligned.apk
# Firma: per le build di prova si crea una chiave locale. Le release ufficiali usano una chiave
# che non sta nel repository: KEYSTORE e KS_PASS dall'ambiente.
KEYSTORE=${KEYSTORE:-debug.keystore}
KS_PASS=${KS_PASS:-android}
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias essenziale -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Essenziale" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:"$KS_PASS" --out build/Essenziale.apk build/aligned.apk
apksigner verify build/Essenziale.apk && echo "OK: build/Essenziale.apk"
