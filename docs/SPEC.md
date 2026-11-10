# Documents: spec

## Goal
A personal Android app for reading, and later editing, documents. Distributed to friends through
private-app-store as a signed APK on GitHub Releases. No Play Store.

## Decisions
1. **Name**: repo `documents-android`, package `io.github.amandhakar.documents`. The name is
   deliberately broader than "PDF viewer" because Word and editing are planned. The package name
   is permanent once released.
2. **Stack**: Kotlin, Jetpack Compose (Material 3), minSdk 26, targetSdk 35.
3. **PDF rendering**: Android's built-in `PdfRenderer`. No third-party PDF library in phase 1.
   Known limits: no text selection or search, no password-protected PDFs, no editing.
4. **Permissions.** Opening one PDF (system picker, or another app's "open with") needs none.
   The library screen, which lists every PDF on the phone as a card, needs to see other apps'
   files, so the manifest asks for `MANAGE_EXTERNAL_STORAGE` ("All files access", Android 11+,
   switched on by the user in Settings) and `READ_EXTERNAL_STORAGE` (`maxSdkVersion` 29). The
   owner chose this over picking folders or a "recently opened" list. Trade-off: it is a broad
   permission that friends will see. The app has no `INTERNET` permission and uploads nothing.
   Without the permission the library screen explains why it is needed and offers Browse (the
   picker) instead. Any further permission needs a new decision here.
5. **Extensibility without speculation**: a small `Document` interface (page count, render
   page) and `DocumentFormat` / `DocumentOpener` routing by MIME type. A new format means one new
   `Document` implementation and one `when` branch. Nothing for future phases is built ahead of time.
6. **Release**: GitHub Actions only. Tag `vX.Y.Z` equals `versionName`; `versionCode` is
   `major × 1000000 + minor × 1000 + patch`; always signed with the one release key; one
   universal APK per release (the store takes the first `.apk` asset).
7. **Build oracle**: the Android SDK isn't available in the cloud dev sandbox, so GitHub Actions
   decides whether it builds and tests pass.

## Phases
| Phase | Scope | Status |
| --- | --- | --- |
| 1 | View PDFs: library of all PDFs as cards with previews, picker (Browse), open-with, scroll, button and pinch zoom, error states, CI, signed releases | In progress (v0.1.0 released; library and pinch zoom not yet device-tested) |
| 2 | Better viewing: remember last page, password-protected PDFs, text search / selection (likely needs a third-party library: check licence, e.g. MuPDF is AGPL) | Not started |
| 3 | PDF annotation / editing (library choice and licence to decide first) | Not started, needs an explicit go-ahead |
| 4 | Word (.docx) viewing, then editing (large; Apache POI is heavy on Android, faithful layout is hard) | Not started, needs an explicit go-ahead |

"Docs" is assumed to mean Word-style `.doc` / `.docx`. Google Docs would be a separate
integration (Google sign-in and APIs) and is not planned.

## Rules
- Work phase by phase; phases 2+ need an explicit go-ahead.
- Never commit secrets, keystores or signing material.
- Every change needs tests; security-relevant paths (file access, URI handling) need
  invalid / denied cases too.
- Keep README's status table truthful: implemented vs tested vs unimplemented.
- Don't add a dependency without a reason and a licence check.
