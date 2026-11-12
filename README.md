# documents-android

An Android app for reading documents, distributed through
[private-app-store](https://github.com/aman-dhakar-191/private-app-store). Today it opens PDFs and can add a signature to them.
Word documents are planned but not started; see [docs/SPEC.md](docs/SPEC.md).

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
| Unit tests (highlights and pen strokes, new text box sizing, text line grouping and tapping, text fitting, signature placement, PDF coordinate and rotation maths, signature ink layout, save names and error messages, format detection, page sizing, zoom, card text and ordering, default-app detection logic, page-coordinate and handle/menu placement maths for text selection, which screen Back goes to, ticking PDFs in the list, which files can be shared) | Written; CI result: see the Actions tab |
| Share: **Share** in the header while reading sends the PDF file; in the library, press and hold a card to choose PDFs (tap more to add, **Select all**, **Cancel**) and **Share** sends all the chosen files at once; the text menu's **Share** sends the selected text. PDFs opened as a plain `file://` path can't be shared | Implemented, **not yet tested on a device** |
| Select text in a PDF like in any text view: press and hold on a word, drag the two handles to select more or less (a magnifier bubble floats above while you drag, on Android 9 and newer), tap elsewhere to clear. A small menu offers Select all (the page's text), Share and Copy. **Android 15 and newer only** (uses Android's built-in PDF text support; older phones keep the plain viewer). No scanned-image text. | Implemented, **not yet tested on a device** |
| Text search, selecting across pages, password-protected PDFs | Not implemented |
| Sign a PDF: tap the pen in the reading bar, draw your signature once (kept on the phone for reuse), or pick a photo of your signature instead (white paper can be made see-through), then drag and resize it with the round handle: the signature floats above the pages, so you can scroll the document under it and tick to drop it on whichever page it is over (repeat for more places), then Save: **Save as new copy** (you choose where) or **Replace original**. The signature is added on top of the real page with the PdfBox-Android library (Apache-2.0), so text stays selectable. Replacing checks the result first and restores the original if the write fails; a file another app only let Documents read can't be replaced (use a new copy). A picture of a signature, not a cryptographic one. Password-protected PDFs can't be edited | Implemented; placement, page-rotation and file-name maths have unit tests; placement across pages and image clean-up maths also have unit tests; **the PdfBox writing and all screens are not tested on a device (the owner has tried placing a signature and found it could not move between pages, which this change addresses)**, and the writing code is only checked by the build |
| Change text: ⋮ → **Change text**, tap a line of text (lines are outlined), type the new text, **Apply** (or **Remove text**). Changes show on the page straight away and are written when you **Save** (new copy or replace, together with any signatures). The old line is **covered by a box in the background colour, not erased: the old text is still in the file** (don't use this to hide private information). Latin letters, digits and common symbols only; the new text is drawn in Helvetica (not the original font), in black, and shrunk a little if longer. Rotated pages and scanned pages (no text) aren't supported | Implemented; line finding, hit testing, size fitting and character checks have unit tests; **the PdfBox text extraction and drawing and all screens are not tested on a device** (the extraction's coordinates are the least certain part) |
| Add text: ⋮ → **Add text**, type it, then drag it onto any page (scroll under it, like the signature), resize it with the round handle, tick to place. Written as real text (Helvetica, black, one line) on Save. Same characters as Change text; rotated pages aren't supported. **Undo last added text** is in the ⋮ menu | Implemented; sizing maths has unit tests; **the drawing and all screens are not tested on a device** |
| Highlight: ⋮ → **Highlight**, tap a line (tap again to remove). Draw: ⋮ → **Draw**, pick a colour and draw with a finger; the button in the bar switches between drawing and scrolling. **Undo last highlight or drawing** is in the ⋮ menu. Saved with everything else on Save; drawing works on rotated pages, highlighting needs lines of text (not scans, not rotated pages) | Implemented; mark logic and page-rotation coordinate maths have unit tests; **the PDF drawing and all screens are not tested on a device** |
| Really removing the old text, matching fonts | Not implemented (planned, in that order) |
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
