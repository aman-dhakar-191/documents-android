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
   Known limits: no search, no password-protected PDFs, no editing. Text selection uses the PDF
   text API that Android added in 15 (API 35), reached through the same `PdfRenderer`, so there is
   still no third-party PDF library; phones older than Android 15 have no text selection. The owner
   chose this over adding a library (PdfBox-Android, or MuPDF whose AGPL licence would apply to the
   whole app). Selection works like standard text selection (long press, two draggable handles, a floating Select all / Share / Copy menu, tap elsewhere to clear) and stays on one page; Select all means all the text on that page. Scanned PDFs have no text to select.
4. **Permissions.** Opening one PDF (system picker, or another app's "open with") needs none.
   The library screen, which lists every PDF on the phone as a card, needs to see other apps'
   files, so the manifest asks for `MANAGE_EXTERNAL_STORAGE` ("All files access", Android 11+,
   switched on by the user in Settings) and `READ_EXTERNAL_STORAGE` (`maxSdkVersion` 29). The
   owner chose this over picking folders or a "recently opened" list. Trade-off: it is a broad
   permission that friends will see. The app has no `INTERNET` permission and uploads nothing.
   A `<queries>` entry (not a permission) lets the app see which PDF viewers exist, to tell the
   user which one is the default. Without the permission the library screen explains why it is needed and offers Browse (the
   picker) instead. Any further permission needs a new decision here.
5. **Extensibility without speculation**: a small `Document` interface (page count, render
   page) and `DocumentFormat` / `DocumentOpener` routing by MIME type. A new format means one new
   `Document` implementation and one `when` branch. Nothing for future phases is built ahead of time.
6. **Release**: GitHub Actions only. Tag `vX.Y.Z` equals `versionName`; `versionCode` is
   `major × 1000000 + minor × 1000 + patch`; always signed with the one release key; one
   universal APK per release (the store takes the first `.apk` asset).
7. **Build oracle**: the Android SDK isn't available in the cloud dev sandbox, so GitHub Actions
   decides whether it builds and tests pass.
8. **Sharing**: PDFs (one from the reading screen, several from the library) and selected text go
   through Android's share sheet. Files are handed over as `content://` addresses with a temporary
   read grant to the receiving app, so no permission is added. `file://` paths are refused, because
   they would crash the app on Android 7 and newer.
9. **Editing (signatures).** Android's built-in PDF editing (`PdfRenderer.Page.addPageObject`) only
   exists from Android 16.1 (API 36.1 / S Extensions 18), so it can't work on Android 15. The owner
   chose **PdfBox-Android** (`com.tom-roush:pdfbox-android`, Apache-2.0) over flattening pages into
   pictures. The signature is added on top of the real page, so text stays selectable and files stay
   small. Reading and rendering still use `PdfRenderer`. The signature is drawn with a finger, kept as
   a picture in the app's private storage (or taken from a photo, optionally with the white background made see-through), and placed by dragging and resizing it while it floats above the pages: the pages scroll under it and it lands on the page it is over when the tick is pressed.
   Saving offers a new copy (the system "create file" screen, no permission) or replacing the
   original. Replacing writes the result to a temporary file and checks it opens with the same page
   count first, and puts the original back if the write fails. A file another app only let us read
   can't be replaced: the app then says so and offers a new copy. No new permission was added.
   Known limits: password-protected PDFs can't be edited; a signature is a picture, not a digital
   (cryptographic) signature.
10. **Changing text (cover and retype).** Editing the existing words of a PDF in place is not reliably possible
    (a page stores glyphs at coordinates, usually in trimmed fonts, with no paragraphs). The owner asked for an
    editing layer of our own on top of PdfBox-Android, built in stages. Stage 1: PdfBox finds the lines of text
    on a page (words with positions, joined into lines); in "Change text" mode the person taps a line, types the
    new text, and Save covers the old line with a box in the page's background colour (sampled from the rendered
    page) and writes the new text over it in Helvetica, shrunk down to 60% to fit. **The old text is covered, not
    erased: it is still in the file** (searchable, copyable), so this must not be used to hide sensitive
    information. Limits: Latin letters, digits and common symbols only (no Hindi or emoji yet); text colour is
    copied from the old text (the pixel furthest from the background; black if none stands out); the font is matched to the
    closest of the standard Helvetica / Times / Courier families with bold and italic (from the old font's name and
    descriptor), and can be changed in the dialog (the exact typeface can't be reused: PDF fonts are usually trimmed to
    the letters the document used); rotated pages and scans (no text) are not supported. Stage 2: **Add text** puts a new
    piece of text anywhere: it is typed, then dragged and resized (the width chosen sets the font size) like the
    signature, and written as real text on Save (same character limits as above, one line, at most 200 characters; rotated
    pages not supported). It starts about the size of normal 12 point text at the current zoom, not page-wide. By default
    it **matches the text nearby**: where it is dropped, the nearest line of text on that page (within about 60 points)
    gives it its font family, bold / italic, colour and, if it was not resized, its size. Unticking that in the Add text
    dialog gives a manual Sans / Serif / Mono, B and I choice instead (black, sized by width). Stage 3: **Highlight** (tap a line, tap
    again to remove: a translucent yellow box drawn over the text so the words stay readable) and **Draw** (a
    finger pen in three colours; a toggle switches between drawing and scrolling the pages; works on rotated
    pages too). Both are saved into the page content with the other changes. Stage 4 (experimental, off by
    default): the Save dialog has "Also remove the old text from the file". It drops the characters under each
    change from the page's own `Tj` / `TJ` text operators and moves the pen on by their widths so the text after
    them stays put; it then still covers and retypes as before. It refuses and leaves the page as it is (only
    covered) for rotated pages, vertical text, text inside forms, or any content it can't match exactly, and
    says which pages. Text drawn as outlines or as an image can't be removed.

11. **Web pages (.html).** Opened in Android's `WebView` (part of the system, no library) on a screen of its own, since a
    page isn't a stack of pictures. The page is untrusted: scripts are off unless the person allows them for that
    opening; file and content access, DOM storage, pop-ups, downloads and location are off; the page's text is loaded
    from the one opened file with no base address, so nothing next to it is found. **No INTERNET permission was added**
    and every online request (`http` / `https`) is refused and counted, with a note on screen ("N online parts were
    blocked"). Tapping a web, mail or phone link asks first and then opens it outside the app; every other link kind is
    blocked. Pages over 8 MB aren't opened. The owner confirmed this approach (remote parts blocked) in place of asking for
    the internet permission. Intent filters only match local `content:` / `file:` addresses, so the app does not take
    over links to websites. The library lists web pages too, under chips (All, PDF, Web pages, each with a count; more chips are added with each new format). Web page cards show a globe instead of a first-page preview. Not yet: save as PDF, find in page, share from the viewer.

## Phases
| Phase | Scope | Status |
| --- | --- | --- |
| 1 | View PDFs: library of all PDFs as cards with previews, picker (Browse), open-with, scroll, button and pinch zoom, error states, CI, signed releases | In progress (v0.1.0 released; library and pinch zoom not yet device-tested) |
| 2 | Better viewing: text selection on Android 15+ (built in, no library), then remember last page, password-protected PDFs, text search (search is in the same Android 15 API; older phones would need a third-party library: check licence, e.g. MuPDF is AGPL) | Text selection implemented, not device-tested; rest not started |
| 3 | PDF annotation / editing: signature first (draw, place, save as new or replace), via PdfBox-Android | Signature implemented and working on the owner's phone; text change (cover and retype) implemented, not device-tested; removing old text, new text boxes and other annotations not started |
| 4 | Web pages (.html / .htm / .xhtml): safe offline viewer (done), library cards, save as PDF (not started) | Viewer implemented, not device-tested; the rest per [ROADMAP.md](ROADMAP.md) |
| 5 | PowerPoint (.pptx) viewing, then editing | Planned in [ROADMAP.md](ROADMAP.md), needs an explicit go-ahead |
| 6 | Word (.docx) viewing, then editing | Planned in [ROADMAP.md](ROADMAP.md), needs an explicit go-ahead |

"Docs" is assumed to mean Word-style `.doc` / `.docx`. Google Docs would be a separate
integration (Google sign-in and APIs) and is not planned.

## Rules
- Work phase by phase; phases 2+ need an explicit go-ahead.
- Never commit secrets, keystores or signing material.
- Every change needs tests; security-relevant paths (file access, URI handling) need
  invalid / denied cases too.
- Keep README's status table truthful: implemented vs tested vs unimplemented.
- Don't add a dependency without a reason and a licence check.
