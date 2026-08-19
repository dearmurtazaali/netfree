# Mesh Chat — Capacitor + GitHub Actions cloud build

Real installable APK, built entirely in GitHub's free cloud — no Android
Studio, no local Gradle, nothing heavy on your laptop.

## What's in here

```
www/index.html          ← the whole app (single file, everything you've tested on Blogger)
capacitor.config.json   ← tells Capacitor to wrap www/ into an Android app
android/                ← auto-generated native Android project (Capacitor made this)
.github/workflows/
  build-apk.yml         ← builds the APK automatically in GitHub's cloud
```

## One-time setup (5 minutes)

1. Create a free account at github.com if you don't have one.
2. Create a new repository (e.g. "mesh-chat"), keep it Public or Private — either works.
3. Upload this entire folder's contents to that repository. Easiest way:
   on the repo page, click "Add file" → "Upload files", then drag in
   everything from this project (including the hidden `.github` folder —
   if your file manager hides it, use `git` on the command line instead,
   see below).

### Using git on the command line (more reliable for the hidden .github folder)

```bash
cd mesh-chat-capacitor
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/mesh-chat.git
git push -u origin main
```

## Getting your APK

1. Go to your repository on GitHub → click the **Actions** tab.
2. You'll see "Build Android APK" running (starts automatically after your push).
   Takes about 3–5 minutes the first time.
3. When it finishes (green checkmark), click into that run → scroll down
   to **Artifacts** → download `mesh-chat-debug-apk`.
4. Unzip it — inside is `app-debug.apk`. Send that file to your Android
   phone (WhatsApp, USB, Google Drive, anything) and tap it to install.
   You may need to allow "Install from unknown sources" once.

## Making changes later

Every time you edit `www/index.html` and push to GitHub again, a new APK
builds automatically. No need to touch the `android/` folder or the
workflow file for normal app changes — Capacitor keeps it in sync.

## What's still a placeholder

- **Offline mesh (Bluetooth/WiFi Direct)**: still mock data. Wiring in a
  real Capacitor Bluetooth plugin (e.g. `@capacitor-community/bluetooth-le`)
  is the next step for that side.
- **Online mode**: fully real — WebRTC peer-to-peer, end-to-end encrypted,
  permission-based relay, seen receipts, images, groups. No server anywhere.
