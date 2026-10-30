# 智神专线 · Zhishen Chat (Android)

Open-source Android client for a self-hosted AI chat backend — pure-code UI (no XML layouts), zero third-party dependencies, single-activity architecture. Built with aapt2/javac/dx directly (no Gradle).

**ZH** | [Features](#features) | [Build](#build) | [Server contract](#server-contract)

## Features
- 💬 AI chat (SSE streaming) with session list, pin/rename, cross-session memory hooks
- 🎨 Dark/light theme, font scaling, gesture navigation
- 🧠 status dashboard, income ledger, task queue panels
- 🚀 **Plugin publisher**: paste plugin `index.js` → publishes a standard DSH plugin repo to GitHub via REST (create repo + push files + set topics) — straight from the phone
- 📡 **Published list**: browse your published `dsh-*` plugin repos with stars and one-tap install
- 📦 file area, stickers, TTS voice playback
- 🔐 cookie-based auth with persisted sessions

## Build
Requirements: JDK 21, Android SDK build-tools 34, platform android-34.

```bash
BT=$ANDROID_SDK/build-tools/34.0.0
$BT/aapt2 compile --dir res -o compiled.zip
$BT/aapt2 link -o base.apk -I $ANDROID_SDK/platforms/android-34/android.jar \
  --manifest AndroidManifest.xml -R compiled.zip --auto-add-overlay --java . \
  --min-sdk-version 24 --target-sdk-version 34
mkdir -p classes dexout
$JAVA_HOME/bin/javac --release 8 -cp $ANDROID_SDK/platforms/android-34/android.jar \
  -d classes app/src/com/zhishen/chat/*.java com/zhishen/chat/R.java
$JAVA_HOME/bin/dx --dex --output=dexout/classes.dex classes
cp dexout/classes.dex . && $JAVA_HOME/bin/jar -uf base.apk classes.dex
$BT/zipalign -f 4 base.apk aligned.apk
$BT/apksigner sign --ks <your.jks> --ks-key-alias <alias> aligned.apk
```

## Server contract
The client talks to a small HTTP backend (`BASE` defaults to localhost emulator `10.0.2.2:8000`, changeable in-app):
`POST /register` `POST /login` `GET /api/who` `GET /api/list` `POST /api/chat_stream` (SSE) `GET /api/version` `GET /api/queue` `GET /api/status` `GET /api/income` — see `MainActivity.java` `serverBase()`.

Point it at your own backend, or read the endpoints to wire up anything compatible.

## Privacy
No analytics, no tracking, no third-party SDKs. Your server address and tokens stay on-device.

## License
MIT
