# documents-android

An Android app for reading documents, distributed through
[private-app-store](https://github.com/aman-dhakar-191/private-app-store). Today it opens PDFs.
Editing and Word documents are planned but not started; see [docs/SPEC.md](docs/SPEC.md).

## Status

| Feature | Status |
| --- | --- |
| Library: every PDF on the phone as a card with a first-page preview, newest first | Implemented, **not yet tested on a device** |
| Card on the library screen that explains and opens the right Android settings to make Documents the default PDF app (Android can't do it for the app) | Implemented, **not yet tested on a device** |
| Reading screen without the app title (slim Back / zoom bar only) | Implemented, **not yet tested on a device** |
| Back after another app opened a PDF in Documents returns to that app (not to the library); Back from a PDF opened in the library returns to the library | Implemented, **not yet tested on a device** |
| Open a PDF from the system picker (Browse button) | Implemented; the owner reports PDFs open on their phone |
| Open a PDF from another app / file manager ("Open with") | Implemented; the owner reports PDFs open on their phone (which route was used isn't recorded) |
| Scroll pages, zoom with the − / + buttons | Implemented; the owner reports PDFs open on their phone, zoom buttons not separately checked |
| Pinch-to-zoom with two fingers | Implemented, **not yet tested on a device** |
| Unit tests (format detection, page sizing, zoom, card text and ordering, default-app detection logic, page-coordinate and handle/menu placement maths for text selection, which screen Back goes to, ticking PDFs in the list, which files can be shared) | Written; CI result: see the Actions tab |
| Share: **Share** in the header while reading sends the PDF file; in the library, press and hold a card to choose PDFs (tap more to add, **Select all**, **Cancel**) and **Share** sends all the chosen files at once; the text menu's **Share** sends the selected text. PDFs opened as a plain `file://` path can't be shared | Implemented, **not yet tested on a device** |
| Select text in a PDF like in any text view: press and hold on a word, drag the two handles to select more or less, tap elsewhere to clear. A small menu offers Select all (the page's text), Share and Copy. **Android 15 and newer only** (uses Android's built-in PDF text support; older phones keep the plain viewer). No scanned-image text. | Implemented, **not yet tested on a device** |
| Text search, selecting across pages, password-protected PDFs | Not implemented |
| Annotate / edit PDFs | Not implemented (planned) |
| Word (.doc/.docx) viewing and editing | Not implemented (planned) |

The Android SDK isn't available where this was written, so GitHub Actions is the only build
and test oracle. A feature isn't "working" here until CI is green and it has been tried on a
phone.

## Permissions

Opening a single PDF (Browse, or "Open with" from another app) needs none. The library screen
needs to see the PDFs on the phone: on Android 11+ you switch on **All files access** for
Documents in Settings (the app takes you there), and on Android 10 and older it asks for the
normal storage permission. The app has no internet permission and sends nothing anywhere. Without
the permission the library is empty and Browse still works.

## Building

Needs JDK 17 and the Android SDK (Android Studio installs both).

```sh
./gradlew lintDebug testDebugUnitTest assembleDebug
```

CI ([android.yml](.github/workflows/android.yml)) runs the same on every push and pull request
and attaches the debug APK. That APK uses a throwaway debug key: don't hand it to friends.

## Releasing

Releases follow the rules in the store's
[publishing guide](https://github.com/aman-dhakar-191/private-app-store/blob/main/docs/publishing-an-app.md).

1. One-time: add the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
   `KEY_PASSWORD` under **Settings → Secrets and variables → Actions**. Reuse the store's key
   or make one; **every release of this app must use the same key**, or Android refuses the
   update. Never commit the keystore.
2. **Actions → Release → Run workflow**, pick patch / minor / major.

`versionCode` is `major × 1000000 + minor × 1000 + patch` (from `scripts/next-version.sh`); minor
and patch must stay below 1000. Use this scheme for the app's whole life: don't mix it with a
different one. The release workflow refuses to run without the signing secrets and the release
build is never signed with the debug key.

Optional: add a `STORE_DISPATCH_TOKEN` secret (see the publishing guide) so the store shows a
release immediately instead of after its next scheduled index run.

## Identity

- Package name / `applicationId`: `io.github.amandhakar.documents`. It can't change after the
  first release.
- Store display name: "Documents" (set in the store's `catalog.json`).
