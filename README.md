# documents-android

An Android app for reading documents, distributed through
[private-app-store](https://github.com/aman-dhakar-191/private-app-store). Today it opens PDFs.
Editing and Word documents are planned but not started; see [docs/SPEC.md](docs/SPEC.md).

## Status

| Feature | Status |
| --- | --- |
| Open a PDF from the system picker | Implemented, **not yet tested on a device** |
| Open a PDF from another app / file manager ("Open with") | Implemented, **not yet tested on a device** |
| Scroll pages, zoom with the − / + buttons | Implemented, **not yet tested on a device** |
| Unit tests (format detection, page sizing, zoom) | Written; CI result: see the Actions tab |
| Pinch-to-zoom, text search, text selection, password-protected PDFs | Not implemented |
| Annotate / edit PDFs | Not implemented (planned) |
| Word (.doc/.docx) viewing and editing | Not implemented (planned) |

The Android SDK isn't available where this was written, so GitHub Actions is the only build
and test oracle. A feature isn't "working" here until CI is green and it has been tried on a
phone.

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
