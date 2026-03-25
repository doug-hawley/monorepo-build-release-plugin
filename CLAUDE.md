# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Gradle plugin (Kotlin) that optimizes CI/CD build times in multi-module projects by detecting which Gradle projects have changed based on git history, including transitive dependents.

## Build & Test Commands

```bash
./gradlew build                  # Full build
./gradlew unitTest               # Unit tests only
./gradlew integrationTest        # Integration tests only
./gradlew functionalTest         # Functional tests only
./gradlew check                  # All tests + validation
./gradlew publishToMavenLocal    # Publish to local Maven repo
./gradlew validatePlugins        # Validate plugin descriptor
```

To run a single test class, use the `--tests` filter:
```bash
./gradlew unitTest --tests "io.github.doughawley.monorepo.build.git.GitChangedFilesDetectorTest"
./gradlew functionalTest --tests "io.github.doughawley.monorepo.build.functional.PrintChangedFunctionalTest"
```

After running tests, check results in `build/reports/tests/*/index.html` or `build/test-results/*/*.xml` for details.

## Architecture

The plugin follows a data-flow pipeline executed during the Gradle configuration phase (`projectsEvaluated`):

```
GitChangedFilesDetector  →  ProjectFileMapper  →  ProjectMetadataFactory  →  MonorepoProjects  →  Extension / Tasks
(git diff/ls-files)          (file → project)       (dependency graph)        (transitive deps)     (results stored)
```

**Key classes** (all under `src/main/kotlin/io/github/doughawley/monorepo/`):

| Class | Role |
|---|---|
| `MonorepoBuildReleasePlugin` | Plugin entry point; registers all extensions and tasks; triggers metadata computation in `projectsEvaluated` |
| `MonorepoExtension` | Root-level DSL wrapper (`monorepo { build { } release { } }`) |
| `MonorepoProjectExtension` | Per-subproject DSL wrapper (`monorepoProject { build { } release { } }`) |
| `build/MonorepoBuildExtension` | Inner build config (`lastSuccessfulBuildTag`, `includeUntracked`, `excludePatterns`, `resolvedBaseRef`) and internal metadata storage |
| `build/task/PrintChangedProjectsTask` | Reads pre-computed metadata from extension and outputs results |
| `build/git/GitChangedFilesDetector` | Runs `git diff`, `git diff --cached`, and `git ls-files` to find changed files; applies exclude patterns |
| `build/domain/ProjectFileMapper` | Maps changed file paths to Gradle project paths |
| `build/domain/ProjectMetadataFactory` | Builds dependency graph by introspecting Gradle `ProjectDependency` objects |
| `build/domain/MonorepoProjects` | Container for all project metadata; computes changed projects with transitive dependency resolution |
| `build/domain/ProjectMetadata` | Immutable data model; `hasChanges()` traverses transitive deps |
| `release/MonorepoReleaseExtension` | Release configuration DSL (`globalTagPrefix`, `primaryBranchScope`) |
| `release/task/ReleaseTask` | Creates versioned git tag for a subproject (patch releases from release branches) |
| `release/git/GitTagScanner` | Finds the most recent version tag for a project prefix |
| `release/git/GitReleaseExecutor` | Pushes tags and release branches via git; supports atomic multi-ref push |
| `release/git/ReleaseBranchCreator` | Two-phase release branch creation: create locally → `git push --atomic` → rollback on failure; scans tags and branches for version resolution |
| `build/git/LastSuccessfulBuildTagUpdater` | Force-updates and pushes the last-successful-build tag after successful builds |
| `git/GitCommandExecutor` | Low-level `ProcessBuilder` wrapper for executing git commands |

**Root project special case**: The root project is marked as changed only when files in the root directory (not inside any subproject directory) have changed.

**Results are stored** in `MonorepoBuildExtension` via `monorepoProjects` (full metadata) and `allAffectedProjects` (set of affected project paths) for use by downstream tasks.

## Test Structure

- **Unit tests**: `src/test/unit/kotlin/` — fast, isolated Kotest tests
- **Integration tests**: `src/test/integration/kotlin/` — tests against a real git backend (no Gradle TestKit)
- **Functional tests**: `src/test/functional/kotlin/` — Gradle TestKit tests that create real temporary projects with git repositories

The functional tests use a standard 5-module dependency tree (`common-lib` ← `module1`, `module2` ← `app1`, `app2`) created by `StandardTestProject` and `TestProjectBuilder`.

**Functional test file convention**: one file per task. All scenarios for a given task live in its file regardless of what behaviour they exercise. Current files:

| File | Task |
|---|---|
| `PrintChangedFunctionalTest.kt` | `printChanged` |
| `BuildChangedFunctionalTest.kt` | `buildChanged` |
| `MonorepoPluginConfigurationTest.kt` | `printChanged` (configuration/exclude scenarios) |
| `ReleaseTaskFunctionalTest.kt` | `release` (per-subproject) |
| `ReleaseChangedFunctionalTest.kt` | `releaseChanged` |

## Code Style

- Always use block bodies with `{}` and explicit `return`; never expression bodies with `=`
- Prefer `val` over `var`; return empty collections instead of null
- Extract private methods >20 lines into separate focused classes
- Use `ProcessBuilder` (never `Runtime.exec()`) with exit code checks for all external processes
- Normalize file paths for cross-platform comparison (trailing slashes, `relativeTo()`)

## Testing Standards

Use Kotest FunSpec with Given/When/Then comments and Kotest matchers:

```kotlin
class MyTest : FunSpec({
    test("should do X when Y") {
        // given
        val input = ...
        // when
        val result = subject.process(input)
        // then
        result shouldBe expected
    }
})
```

Never use JUnit or `assertEquals` — use `shouldBe`, `shouldContain`, `shouldBeInstanceOf<T>()`, etc.

## Documentation & File Discipline

Only maintain `README.md`, `CHANGELOG.md`, and `CLAUDE.md`. Do not create summary files, migration guides, or status reports. Use KDoc for public APIs; keep inline comments minimal.

## Release Process

1. Update version in `build.gradle.kts`
2. Update `CHANGELOG.md`
3. Commit, then tag: `git tag v1.x.x && git push origin v1.x.x`
4. GitHub Actions creates the release automatically
