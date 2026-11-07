## Summary

<!-- What changes for hosts or extensions, and why. -->

## Release notes

<!-- The title and the label decide what the release draft shows. See "Release Notes" in AGENTS.md. -->

- [ ] The title follows `Area: Description` with a listed area, or `API:` for a change to the contracts hosts and extensions compile against
- [ ] The title describes the effect in the past tense, for example `Resolution: Fixed ...`
- [ ] Exactly one label: `feature`, `change`, `bug`, `dependencies`, or `skip-changelog`
- [ ] `bug` only for a bug that shipped in a release; a fix for a bug that only existed on `dev` is `skip-changelog`
- [ ] `major` added when hosts or extensions must change on upgrade
- [ ] Upgrade steps or warnings added to `.github/release-notes.md`, if hosts need them

## Checks

- [ ] Tests added or updated
- [ ] Documentation under `docs/content/` updated for changes hosts or extensions notice
