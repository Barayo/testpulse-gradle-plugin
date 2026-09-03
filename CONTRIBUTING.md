# Contributing

## Setup

```bash
./gradlew build
```

## Testing

- **Unit tests** (`src/test/`, `./gradlew test`) — the reflection/XML-rewrite
  logic (`JUnitReportAnnotatorTest`), the class-level attachment allowlist
  (`TestPulseAttachmentsTest`, `AttachmentStoreTest`), config resolution
  (`ConfigResolverTest`), and the submission/exit-code branches
  (`ImportSubmitterTest`, HTTP mocked via WireMock) — each in isolation,
  no real Gradle build spawned.
- **Functional tests** (`src/functionalTest/`, `./gradlew functionalTest`)
  — [Gradle TestKit](https://docs.gradle.org/current/userguide/test_kit.html)
  driving real, isolated Gradle builds against fixture projects, proving
  the pieces actually integrate: the `finalizedBy` wiring, the annotator
  against Gradle's real JUnit XML output, the attachment allowlist against
  real compiled classes and real `StackWalker` behavior, multi-project
  isolation, and scratch-directory cleanup between runs.

Run everything (also runs `validatePlugins`): `./gradlew check`.

TDD is the standing practice: write the failing test first, then the
minimal implementation to make it pass. Two real bugs were caught this
way during this plugin's own development — see `tasks.md` in the
originating OpenSpec change for what they were and how the tests that
caught them are structured, as a guide for future ones.

## Release process

Releases are automated via [`semantic-release`](https://semantic-release.gitbook.io/)
on merge to `main`, following [Angular/Conventional Commits](https://www.conventionalcommits.org/)
(`feat:`, `fix:`, etc.) — see `.releaserc.json`. Publishing to the [Gradle
Plugin Portal](https://plugins.gradle.org/) uses `GRADLE_PUBLISH_KEY`/
`GRADLE_PUBLISH_SECRET` repo secrets (classic API-key auth — the Portal has
no OIDC trusted-publishing bootstrap problem the way npm/Maven Central do,
so CI can publish even the very first version).

If a release's publish step fails after its version-bump commit/tag has
already been pushed (a real risk with `semantic-release`'s prepare-before-publish
ordering), trigger `.github/workflows/release.yml` manually
(`workflow_dispatch`) to publish the already-tagged version directly.
