#!/bin/bash
set -e
cd "$(dirname "$0")"
SDK=/usr/lib/android-sdk
JAR=$SDK/platforms/android-23/android.jar
BT=$SDK/build-tools/debian
rm -rf gen obj out && mkdir -p gen obj out
aapt package -f -m -J gen -M AndroidManifest.xml -S res -I $JAR
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath $JAR -d obj $(find src gen -name '*.java')
$BT/dx --dex --output=out/classes.dex obj
aapt package -f -M AndroidManifest.xml -S res -I $JAR -F out/app.unsigned.apk
(cd out && aapt add app.unsigned.apk classes.dex >/dev/null)
zipalign -f 4 out/app.unsigned.apk out/app.aligned.apk
[ -f habitos.keystore ] || keytool -genkeypair -keystore habitos.keystore -storepass habitos123 -keypass habitos123 -alias habitos -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Habitos" 2>/dev/null
apksigner sign --ks habitos.keystore --ks-pass pass:habitos123 --key-pass pass:habitos123 --out out/Habitos.apk out/app.aligned.apk
apksigner verify --print-certs out/Habitos.apk | head -2
ls -la out/Habitos.apk
