#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-../tools/android-sdk}}"
sdk="$(cd "$sdk" && pwd)"
bt="$sdk/build-tools/36.0.0"
platform="$sdk/platforms/android-36/android.jar"
mkdir -p build/generated build/classes build/dex dist .signing
if [ ! -f .signing/release.p12 ]; then
  python3 -c 'import secrets,pathlib; pathlib.Path(".signing/password.txt").write_text(secrets.token_urlsafe(48))'
  keytool -genkeypair -keystore .signing/release.p12 -storetype PKCS12 \
    -storepass:file .signing/password.txt -alias phone-access-history \
    -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=Phone Access History, OU=Personal Android App, O=Cameron Rees"
  chmod 600 .signing/*
fi
"$bt/aapt2" compile --dir app/src/main/res -o build/resources.zip
"$bt/aapt2" link -o build/base.apk -I "$platform" --manifest app/src/main/AndroidManifest.xml \
  --java build/generated --min-sdk-version 26 --target-sdk-version 36 build/resources.zip
find app/src/main/java build/generated -name '*.java' > build/sources.txt
java com.sun.tools.javac.Main -encoding UTF-8 -source 8 -target 8 \
  -bootclasspath "$bt/core-lambda-stubs.jar:$platform" -d build/classes @build/sources.txt
python3 - <<'PY'
import pathlib, zipfile
with zipfile.ZipFile('build/classes.jar','w',zipfile.ZIP_DEFLATED) as jar:
    for file in pathlib.Path('build/classes').rglob('*.class'):
        jar.write(file,file.relative_to('build/classes'))
PY
"$bt/d8" --release --min-api 26 --lib "$platform" --output build/dex build/classes.jar
python3 - <<'PY'
import shutil,zipfile,pathlib
shutil.copyfile('build/base.apk','build/unsigned.apk')
with zipfile.ZipFile('build/unsigned.apk','a',zipfile.ZIP_DEFLATED) as apk:
    for file in pathlib.Path('build/dex').glob('*.dex'): apk.write(file,file.name)
PY
"$bt/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
"$bt/apksigner" sign --ks .signing/release.p12 --ks-key-alias phone-access-history \
  --ks-pass file:.signing/password.txt --out dist/Phone_Access_History.apk build/aligned.apk
"$bt/apksigner" verify --verbose --print-certs dist/Phone_Access_History.apk > dist/signature-verification.txt
"$bt/aapt2" dump badging dist/Phone_Access_History.apk > dist/package-info.txt
sha256sum dist/Phone_Access_History.apk > dist/SHA256SUMS.txt
printf 'Built and signature-verified: dist/Phone_Access_History.apk\n'
