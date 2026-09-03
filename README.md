# testpulse-gradle-plugin

A Gradle plugin for reporting JUnit test results into
[TestPulse](https://github.com/Barayo/TestPulse) — matches each test to an
existing TestPulse case by key and submits the run automatically.

Gradle's own `Test` task has no built-in way to write custom `<properties>`
into its JUnit XML report ([a still-open Gradle
issue](https://github.com/gradle/gradle/issues/36762) requesting exactly
this). This plugin works around it the same way
[`testpulse-maven-plugin`](https://github.com/Barayo/testpulse-maven-plugin)
does for Surefire: reflection-based post-processing, reusing the same
[`@TestPulse`](https://github.com/Barayo/testpulse-annotations) annotation
— a Java/Kotlin codebase using both Maven and Gradle in different modules
shares the exact same annotated test source.

## Install

```kotlin
// build.gradle.kts
plugins {
    id("io.github.barayo.testpulse") version "0.1.0"
}
```

That's it — the plugin automatically adds `testpulse-annotations` (for
`@TestPulse`) and its own classes (for `TestPulseAttachments`) as
`testImplementation` dependencies. No separate dependency block needed.

## Tag your tests

```java
import io.github.barayo.testpulse.TestPulse;
import org.junit.jupiter.api.Test;

public class LoginTest {
    @TestPulse(caseKey = "LOGIN-42", platform = "linux", tags = {"smoke"})
    @Test
    void loginSucceeds() {
        // ...
    }
}
```

A plain `./gradlew test` both annotates the report and submits it — no
extra task invocation needed.

## Attach screenshots/files

```java
import io.github.barayo.testpulse.gradle.attachments.TestPulseAttachments;

@TestPulse(caseKey = "LOGIN-43")
@Test
void loginFailsWithBadPassword() throws Exception {
    byte[] screenshot = takeScreenshot();
    TestPulseAttachments.attach("LOGIN-43", screenshot, "failure.png", "image/png");
    // ...
}
```

The case key is passed explicitly — `attach()` verifies it matches one of
the `@TestPulse` case keys declared on the *calling class's own methods*
and rejects the call otherwise, so a test can't accidentally (or
otherwise) attach evidence under a case key that isn't its own. Only
`image/png`, `image/jpeg`, and `image/webp` are accepted. Calling
`attach()` multiple times under the same case key keeps every attachment
locally — none get overwritten or lost to a race.

**This does not currently apply to `@ParameterizedTest` invocations end to
end.** `testpulseAnnotate` only matches a `<testcase>` to exactly one
compiled method (see "v1 limitations" below), and a parameterized test's
decorated invocation names never satisfy that exact match — so a
parameterized test's case is never matched server-side, and any
attachments registered from it are recorded locally but never delivered
in a submission. Use `attach()` from a plain (non-parameterized)
`@TestPulse`-annotated test for attachments that need to actually reach
TestPulse today.

## Configure

```kotlin
testpulse {
    url = "http://localhost:8080"
    project = "LOGIN"
    failOnUnmatched = false
    dryRun = false
}
```

Each setting resolves from a command-line project property first, falling
back to this block — except the token, which also checks an environment
variable in between:

| Setting | `-P` project property | Env var | Extension DSL |
|---|---|---|---|
| API base URL | `-Ptestpulse.url=...` | — | `url` |
| API token | `-Ptestpulse.token=...` | `TESTPULSE_TOKEN` | `token` |
| Project key | `-Ptestpulse.project=...` | — | `project` |
| Fail on unmatched | `-Ptestpulse.failOnUnmatched=...` | — | `failOnUnmatched` |
| Dry run | `-Ptestpulse.dryRun=...` | — | `dryRun` |

Put the token in `TESTPULSE_TOKEN` (a CI secret) — never in `-Ptestpulse.token=...`
or a committed `testpulse { }` block, even though both remain fully
supported and even take precedence over the environment variable if set.

## Build outcome

| API response | Behavior |
|---|---|
| `201` all matched | build succeeds; summary logged |
| `207` some unmatched | build succeeds by default (unmatched case keys + `failOnUnmatched` pointer logged); fails when `failOnUnmatched` is set |
| network/auth/4xx/5xx error | always fails the build, unconditionally |

`dryRun = true` (or `-Ptestpulse.dryRun=true`) previews matches via a
read-only `GET /cases` request and never submits anything.

## v1 limitations

- JUnit 4/5 only — no TestNG.
- Parameterized-test invocations and overloaded methods aren't matched by
  `testpulseAnnotate` (same limitation as `testpulse-maven-plugin`); the
  build log names them so nothing is silently mismatched.
- Every invocation of an annotated method shares one case key for the
  purpose of the injected XML property — only `attach()` supports multiple
  calls per case key, not the annotation matching itself.

## License

MIT
