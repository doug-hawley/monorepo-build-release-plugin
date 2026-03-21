# Monorepo Build Release Plugin

[![CI](https://github.com/doug-hawley/monorepo-build-release-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/doug-hawley/monorepo-build-release-plugin/actions/workflows/ci.yml)

A Gradle plugin for multi-module projects that uses git history to detect which projects have changed, enabling selective per-project builds and versioned releases.

## Key Features

- **Gradle-native dependency tracking** — changed project detection reads your existing Gradle project dependencies directly; no separate dependency map to define or maintain
- **Tag-based change detection** — CI release builds compare against a `monorepo/last-successful-build` tag that only moves on green builds; failed builds automatically include their changes in the next run. Dev and PR builds compare against `origin/{primaryBranch}`
- **Transitive impact analysis** — projects that depend on a changed project are automatically included
- **Selective builds** — run builds and tests only for affected projects, reducing CI time in large monorepos
- **Per-project versioning** — each subproject gets its own semantic version tag; only changed projects are released

## Usage

### Apply the plugin

Requires Gradle 7.0+, Java 17+, and Git in your PATH.

```kotlin
plugins {
    id("io.github.doug-hawley.monorepo-build-release-plugin") version "0.4.1" // x-release-please-version
}
```

### Builds

```kotlin
monorepo {
    primaryBranch = "main"              // main integration branch; used as baseline ref for dev/PR builds; defaults to "main"

    build {
        lastSuccessfulBuildTag = "monorepo/last-successful-build"  // tag name for change detection anchor; defaults shown
        includeUntracked = true         // include files not yet tracked by git; defaults to true
        excludePatterns = listOf(       // regex patterns for files to exclude globally across all projects
            ".*\\.md",
            "docs/.*"
        )
    }
}
```

The plugin detects changes by comparing HEAD against a baseline ref. The baseline depends on the task:

- **CI release builds** (`prepareReleasesForChanged`): uses the `lastSuccessfulBuildTag`. If the tag doesn't exist (e.g., first run), all projects are treated as changed.
- **Dev and PR builds** (all other tasks): uses `origin/{primaryBranch}`. If the remote branch isn't available, all projects are treated as changed.

Individual subprojects can declare their own exclude patterns using the `monorepoProject` extension. Patterns are matched against paths relative to the subproject directory and are applied after global `excludePatterns`.

```kotlin
// In :api/build.gradle.kts
monorepoProject {
    build {
        excludePatterns = listOf(     // regex patterns relative to this subproject's directory
            "generated/.*",
            ".*\\.json"
        )
    }
}
```

### Tasks

#### `printChanged`

Prints a human-readable report of which projects have changed and which are transitively affected.

```bash
./gradlew printChanged
```

#### `buildChanged`

Builds all affected projects (including transitive dependents). Useful for PR validation and local development.

```bash
./gradlew buildChanged
```

> **Note:** `buildChanged` does not update the last-successful-build tag or create release branches. Use `prepareReleasesForChanged` for post-merge CI workflows. `prepareReleasesForChanged` depends on `buildChanged`, so all affected projects are built first automatically.

### Releases

Each subproject manages its own semantic version using git tags of the form `{globalTagPrefix}/{projectPrefix}/v{version}` (e.g. `release/api/v1.2.0`). Release is opt-in per subproject.

### Opting in a subproject

In each subproject's `build.gradle.kts`:

```kotlin
monorepoProject {
    release {
        enabled = true
    }
}
```

The tag prefix is derived automatically from the Gradle path (`:api:core` → `api-core`). Override it if needed:

```kotlin
monorepoProject {
    release {
        enabled = true
        tagPrefix = "my-api"
    }
}
```

### Global configuration

```kotlin
monorepo {
    release {
        globalTagPrefix = "release"       // prefix for all tags and branches; default "release"
        primaryBranchScope = "minor"      // version bump on the primary branch; "minor" or "major"; default "minor"
    }
}
```

### Tasks

#### `prepareReleasesForChanged`

The CI post-merge task. Depends on `buildChanged` to build all affected projects first, then creates release branches atomically for changed opted-in projects and updates the last-successful-build tag. Fails fast if the current branch is not `primaryBranch`.

```bash
./gradlew prepareReleasesForChanged
```

Release branches are created using a two-phase atomic approach: all branches are created locally first, then pushed together via `git push --atomic`. If any step fails, all local branches are rolled back and the tag is not updated.

#### `:subproject:release`

Releases a single subproject from its release branch. Must be run from a matching release branch (e.g., `:app1:release` must be run from `release/app1/v0.1.x`):

```bash
./gradlew :api:release
```

The task will fail if run from `main`, a feature branch, or a release branch belonging to a different project.

### Versioning rules

- Release branches follow the pattern `{globalTagPrefix}/{projectPrefix}/v{major}.{minor}.x`
- The first release on a new branch (e.g., `release/api/v0.1.x`) creates `v0.1.0`
- Subsequent releases on that branch apply a `patch` bump (`v0.1.1`, `v0.1.2`, …)
- The subproject must be built before releasing — `release` requires `build` to have run

### Advanced

#### Access changed projects in other tasks

The plugin computes results during the configuration phase, so any task can access them directly from the `monorepo` extension — no `dependsOn` needed:

```kotlin
tasks.register("customTask") {
    doLast {
        val extension = project.extensions.getByType(
            io.github.doughawley.monorepo.MonorepoExtension::class.java
        ).build
        val changedProjects = extension.allAffectedProjects
        println("Changed projects: $changedProjects")

        changedProjects.forEach { projectPath ->
            println("Affected: $projectPath")
        }
    }
}
```

## Example Usage

This walkthrough uses a three-project monorepo to show how the build and release workflows fit together end-to-end.

```kotlin
// shared-module/build.gradle.kts — no monorepoProject block; release is opt-in

// app1/build.gradle.kts
dependencies {
    implementation(project(":shared-module"))
}
monorepoProject {
    release {
        enabled = true
    }
}

// app2/build.gradle.kts
dependencies {
    implementation(project(":shared-module"))
}
monorepoProject {
    release {
        enabled = true
    }
}
```

`:shared-module` is an internal module consumed by both apps. It participates in change detection and build impact analysis, but the team doesn't publish it directly — only `:app1` and `:app2` are released.

### Developer workflow

A developer has modified `:shared-module` on a feature branch. To see what's affected before opening a PR:

```bash
./gradlew printChanged
```

```
Changed projects:

  :shared-module

  :app1  (affected via :shared-module)
  :app2  (affected via :shared-module)
```

`:app1` and `:app2` appear because they depend on `:shared-module` — the plugin resolves transitive impact automatically from the Gradle dependency graph.

Then build everything affected to verify it compiles before opening the PR:

```bash
./gradlew buildChanged
```

### Releasing from main

The PR is merged into `main` and CI triggers on the merge commit. The plugin compares HEAD against the `monorepo/last-successful-build` tag to find what changed since the last green build:

```bash
./gradlew prepareReleasesForChanged
```

`:shared-module` changed, so both apps are included via transitive impact. `:shared-module` itself is skipped because it isn't opted in to releases. The task builds all affected projects, creates release branches atomically, and updates the tag — all in one step.

```
Created release branches for: :app1, :app2
```

| Project | Release branch created |
|---------|------------------------|
| `:app1` | `release/app1/v0.1.x`  |
| `:app2` | `release/app2/v0.1.x`  |

A separate CI/CD pipeline configured to trigger on pushes to `release/**` branches then runs `:subproject:release` for each project. That pipeline is responsible for creating the version tag and publishing the artifact:

```bash
./gradlew :app1:release   # creates tag release/app1/v0.1.0, writes release-version.txt
./gradlew :app2:release   # creates tag release/app2/v0.1.0, writes release-version.txt
```

Wire your publish step to the `postRelease` lifecycle hook so it runs automatically after tagging. Alternatively, the `release` task writes the released version to `build/release-version.txt`, which your CI/CD pipeline can read to determine the version for publishing:

```bash
VERSION=$(cat app1/build/release-version.txt)
./publish.sh --version "$VERSION"
```

> **Tip:** If a build fails, the tag stays at the last green state. The next successful build will automatically pick up all changes since then — nothing is lost.

### Patching a release branch

A bug is found in `:app1` after `v0.1.0`. A developer checks out `release/app1/v0.1.x` and commits a fix. Because a release branch is scoped to a single project, CI uses the per-project release task directly:

```bash
./gradlew :app1:release
```

The plugin detects it is on a release branch and applies a patch bump. Tag `release/app1/v0.1.1` is created; no new release branch is created, and `:app2` and `:shared-module` are untouched.

## CI/CD Configuration

The plugin pushes git refs (release branches and the last-successful-build tag) during task execution. These pushes can interact with your CI/CD platform's trigger rules in unexpected ways. The sections below cover platform-specific configuration to avoid recursive triggers and ensure release workflows run correctly.

### Vela

#### Pipeline for `main` (post-merge builds)

Configure your main branch pipeline to run `prepareReleasesForChanged` on pushes to `main`:

```yaml
steps:
  - name: build-and-release
    image: gradle:jdk17
    commands:
      - ./gradlew prepareReleasesForChanged

metadata:
  template: false

ruleset:
  event: push
  branch: main
```

> **Warning:** The `prepareReleasesForChanged` task force-pushes the `monorepo/last-successful-build` tag on every run. If your pipeline triggers on **all** tag events, this will create an infinite loop: task pushes tag → Vela triggers build → task pushes tag → repeat.
>
> Ensure your tag-triggered pipelines filter to version-pattern tags only (e.g., `v*`), not all tags.

#### Pipeline for release branches

Configure a separate pipeline to run the per-project release task when a release branch is pushed:

```yaml
steps:
  - name: release
    image: gradle:jdk17
    commands:
      - ./gradlew :${VELA_REPO_BRANCH_PROJECT}:release

metadata:
  template: false

ruleset:
  event: push
  branch: release/*
```

> **Warning:** The release branches pushed by `prepareReleasesForChanged` will trigger this pipeline. Make sure the release pipeline does **not** also run `prepareReleasesForChanged`, or you will get recursive triggers.

#### Tag-triggered pipelines

If you have pipelines that trigger on tag events (e.g., for publishing artifacts after a release tag is created), scope the tag filter to exclude the last-successful-build tag:

```yaml
ruleset:
  event: tag
  tag: "release/*/v*"    # only version tags, not monorepo/last-successful-build
```

### GitHub Actions

#### Workflow for `main` (post-merge builds)

```yaml
on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0    # full history needed for git diff and tag detection
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - run: ./gradlew prepareReleasesForChanged
```

> **Warning:** GitHub Actions does **not** trigger workflows from pushes made with the default `GITHUB_TOKEN`. This is a deliberate safeguard against recursive workflows, but it means the release branches created by `prepareReleasesForChanged` will **not** automatically trigger your release workflow.
>
> To trigger release branch workflows, use one of these approaches:
> 1. **GitHub App token** — use a GitHub App installation token (e.g., via [`actions/create-github-app-token`](https://github.com/actions/create-github-app-token)) instead of `GITHUB_TOKEN` for the checkout step. Pushes made with this token will trigger downstream workflows.
> 2. **`workflow_dispatch`** — add a `workflow_dispatch` trigger to your release workflow and dispatch it from the main workflow after branch creation.
> 3. **Personal Access Token** — use a PAT with repo scope (less recommended for shared repositories).

#### Workflow for release branches

```yaml
on:
  push:
    branches: ["release/**/v*.x"]

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - run: ./gradlew $(echo "${GITHUB_REF_NAME}" | sed 's|release/|:|;s|/v.*||'):release
```

This workflow will only trigger if pushes to release branches are made with a token that allows workflow triggering (see warning above).

## Configuration Reference

### `monorepo { }`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `primaryBranch` | String | `"main"` | Main integration branch; used as baseline ref (`origin/{primaryBranch}`) for dev and PR builds, and as branch guard for `prepareReleasesForChanged` |

### `monorepo { build { } }`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `lastSuccessfulBuildTag` | String | `"monorepo/last-successful-build"` | Tag name used as the anchor for change detection; updated automatically after successful builds |
| `includeUntracked` | Boolean | `true` | Whether to include untracked, staged, and working-tree files in detection |
| `excludePatterns` | List\<String\> | `[]` | Regex patterns for files to exclude globally across all projects |

### `monorepoProject { build { } }`

Applied per subproject. Patterns are matched against paths **relative to the subproject directory** and applied **after** global `excludePatterns`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `excludePatterns` | List\<String\> | `[]` | Regex patterns for files to exclude in this subproject |

### `monorepo { release { } }`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `globalTagPrefix` | String | `"release"` | Prefix used in all tag and release branch names |
| `primaryBranchScope` | String | `"minor"` | Version bump scope when creating release branches from the primary branch; `"minor"` or `"major"` |

### `monorepoProject { release { } }`

Applied per subproject to opt in to release management.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | Boolean | `false` | Whether this subproject participates in releases |
| `tagPrefix` | String? | `null` | Override the auto-derived tag prefix (default derives from Gradle path: `:api:core` → `api-core`) |

## Troubleshooting

### "Not a git repository" warning

Ensure you're running the task in a directory that's part of a git repository. The plugin looks for a `.git` directory in the project root or parent directories.

### "Git diff command failed"

This can happen if:
- The baseline ref is not available — `origin/{primaryBranch}` for dev/PR builds, or the `lastSuccessfulBuildTag` for CI release builds
- You haven't fetched the remote branch or tags (`git fetch origin`)
- Git is not installed or not in the PATH

Solution:
```bash
git fetch origin
./gradlew printChanged
```

### No projects detected despite changes

Check your `excludePatterns` configuration - you may be inadvertently excluding files. Enable logging to see what files are being detected:

```bash
./gradlew printChanged --info
```

### Root project always shows as changed

This is expected if files in the root directory (outside of subproject directories) have changed. To prevent this, ensure all code is within subproject directories.

## Support & Contributions

- **Issues**: Report bugs or request features via [GitHub Issues](https://github.com/doug-hawley/monorepo-build-release-plugin/issues)
- **Contributing**: See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and guidelines
- **Questions**: Start a discussion in [GitHub Discussions](https://github.com/doug-hawley/monorepo-build-release-plugin/discussions)

## License

MIT License - see [LICENSE](LICENSE) file for details
