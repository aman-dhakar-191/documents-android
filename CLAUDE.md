# CLAUDE.md

Source of truth: `docs/SPEC.md`. Read it before changing anything.

- Work phase by phase. Phase 1 (PDF viewing) is in progress; phases 2+ need an explicit go-ahead.
- Never commit secrets, keystores or signing material. Never sign releases with the debug key.
- Every change needs tests, including invalid / denied cases for file and URI handling.
- Ask before adding dependencies (check the licence) or permissions.
- Keep the README status table truthful: distinguish implemented, tested, partial, unimplemented.
- The Android SDK is not available in the cloud dev sandbox: GitHub Actions is the build and test
  oracle. Report CI results, not assumptions.
