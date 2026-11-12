# Documents: roadmap for new file types

Status: **plan only, nothing here is built.** Each phase needs an explicit go-ahead (see SPEC rules). Facts below are
tagged **[Certain]**, **[Likely]** or **[Guessing]**.

## What stays the same

- No third-party library unless there is a reason and a licence check; each one is asked about first.
- No new permission without a new decision in SPEC. In particular **no INTERNET permission**: nothing is uploaded and
  nothing is fetched. [Certain] This also means web pages can't load their remote images, styles or scripts.
- The `Document` interface (page count, render a page to a bitmap, optional text selection) and `DocumentFormat` /
  `DocumentOpener` routing by MIME type stay the way new formats plug in. [Certain] PDF, PowerPoint and Word (laid
  out into pages) fit "pages as bitmaps"; a web page does not, so it gets its own screen.
- The library screen (today: PDFs only) grows filters for the new types.
- Each phase ships in small PRs with unit tests, a green CI build, a release, and a README status row that says what
  is implemented, what is tested, and what has only been built.

## Recommended order: web pages, then PowerPoint, then Word

Reason: effort and risk go up in that order, and the first one reuses what Android already has. [Likely]

---

## Phase 4: Web pages (.html, .htm)

**Status: stage 1 implemented (owner confirmed the order and "remote parts blocked"); stages 2 to 4 not started.**

**Goal:** open an HTML file from the file manager, a chat app or the library and read it, then optionally save it as a
PDF (which the PDF tools can then sign and mark up).

**How:** Android's own `WebView` (part of the system, no dependency). [Certain]

**Decisions to make (defaults in bold):**
1. Scripts: **off by default**, with an "Allow scripts" switch per file. A local HTML file is untrusted content.
2. Remote content: **blocked** (no INTERNET permission). Pages that depend on external CSS, images or scripts will look
   incomplete. [Certain] Say so on screen when something was blocked.
3. Links: tapping a link to another site asks first and then opens the phone's browser; links to other local files are
   not followed.
4. Single file only: a `.html` opened from a file manager arrives as one `content://` stream, so images and styles
   saved next to it are **not** found. [Likely] Saved-page formats that bundle everything (`.mhtml` / `.mht`) can be
   supported as a second step. [Guessing] on how well WebView handles every `.mht` variant.

**Stages:**
1. Format routing and intent filters (`text/html`, `application/xhtml+xml`); a `WebDocumentScreen`; hardened WebView
   settings (no file or content access, scripts off, no remote loads); a pure link/URL policy function with tests.
2. Library filter and card previews for HTML files.
3. Save as PDF (WebView's print adapter, which Android provides) into a new file, so the result opens in the PDF
   viewer. [Likely] Long pages paginate by the print layout, not exactly as on screen.
4. Optional: `.mhtml`, find in page, text selection and share.

**Risks:** WebView security settings must be right; tests can cover the URL policy but not WebView itself.
**Size:** small to medium (about 2 to 4 PRs).

---

## Phase 5: PowerPoint (.pptx)

**Reality check:** [Certain] `.pptx` is a zip of XML files (OOXML). Android can unzip (`java.util.zip`) and parse XML
(`XmlPullParser`) with no library. Old `.ppt` (binary) is a different format and is **not** planned.

**Why this is more tractable than Word:** a slide stores where every shape is (position and size in EMU units), so
there is no text layout problem to solve, unlike Word. [Likely]

**Stages:**
1. **Open and read:** unzip, read the slide list, themes, slide layouts and masters (placeholders inherit position,
   fonts and colours from the layout and master: this is the main difficulty), then draw each slide to a bitmap:
   text boxes with their runs (size, bold, italic, colour, alignment, bullets), pictures, simple shapes and lines,
   solid and picture backgrounds, tables. Reuse the page list for scrolling and zoom.
2. **Not drawn (shown as a labelled grey box, never silently dropped):** charts, SmartArt, video and audio,
   animations and transitions, embedded objects, WordArt effects. [Certain] Some slides will look different from
   PowerPoint.
3. **Text selection and copy** (we know every text run's box, so this is easier than in PDF).
4. **Edit text in place:** change the text of a run in the XML and write the `.pptx` back. [Likely] This keeps
   formatting because the structure is real. Replace-original uses the same checked, restore-on-failure save as PDF.
5. **Slide tools:** delete, reorder, duplicate; add a picture or text box.
6. **Export:** save the deck as a PDF (draw each slide onto a PDF page with Android's `PdfDocument`).

**Risks:** inheritance rules, autofit text, fonts (a phone has few of the fonts PowerPoint decks use, so text widths
differ), and big pictures (memory). [Certain]
**Size:** large (about 8 to 12 PRs for stages 1 to 4).

---

## Phase 6: Word (.docx)

**Reality check:** [Certain] `.docx` is also OOXML (zip plus XML). Unlike a PDF, a Word file stores real paragraphs and
runs, so **editing text is far easier than in PDF**. What is hard is **page-faithful layout**: pagination, floating
images, text wrapping around them, headers and footers, fields, footnotes and sections. [Certain]
Old `.doc` (binary) would need a heavy library (Apache POI) and is **not** planned.

**Two ways to show it (to decide):**
- **A. Reading view (recommended):** reflow paragraphs, headings, lists, tables and images to the screen width. Fast,
  readable, honest about not matching Word's pages. [Likely] Good for reading and editing text on a phone.
- **B. Paginated pages:** lay the text out ourselves (Android's `StaticLayout`) into page bitmaps so it plugs into
  the page list and can be exported to PDF. [Guessing] Page breaks will often differ from Word's.

**Stages:**
1. Read and show (A): unzip, parse the body (paragraphs, runs with bold, italic, underline, size, colour, headings,
   numbered and bulleted lists, tables, inline images), styles and numbering definitions.
2. Edit text in place: change run text and write the `.docx` back with the rest of the package untouched; same safe
   save as PDF.
3. Basic formatting: bold, italic, underline, size, colour; add or delete paragraphs and table rows.
4. Export to PDF (via B's layout) and share.
5. Later, if wanted: comments, tracked changes (shown, not edited), headers and footers.

**Risks:** the long tail of Word features; round-tripping a file without losing things the app does not understand
(the plan is to keep every part of the package we do not touch byte-for-byte). [Likely]
**Size:** large (about 8 to 12 PRs for stages 1 to 3).

---

## Questions for the owner before any of this starts

1. Is the order above right (web pages, PowerPoint, Word), or is one of them urgent?
2. Web pages: is "no internet, so remote parts are blocked" acceptable, or should the app ask for the INTERNET
   permission (a bigger decision, because it changes the privacy promise in SPEC)?
3. Word: reading view (A) first, or do you need Word's exact pages?
4. Should the library list all supported types together or with filters (PDF, Word, PowerPoint, Web)?
