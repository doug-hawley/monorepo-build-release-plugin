# Monorepo Build Release Plugin

[![CI](https://github.com/doug-hawley/monorepo-build-release-plugin/actions/workflows/ci.yml/badge.svg)](https://github.com/doug-hawley/monorepo-build-release-plugin/actions/workflows/ci.yml)

A Gradle plugin for multi-module projects that uses git history to detect which projects have changed, enabling selective per-project builds and versioned releases.

## Key Features

- **Gradle-native dependency tracking** — changed project detection reads your existing Gradle project dependencies directly; no separate dependency map to define or maintain
- **Two detection modes** — branch-mode for local development (compare against a base branch), ref-mode for CI pipelines (compare against a commit SHA or ref)
- **Transitive impact analysis** — projects that depend on a changed project are automatically included
- **Selective builds** — run builds and tests only for affected projects, reducing CI time in large monorepos
- **Per-project versioning** — each subproject gets its own semantic version tag; only changed projects are released

## Usage

### Apply the plugin

```kotlin
plugins {
    id("io.github.doug-hawley.monorepo-build-release-plugin") version "0.3.2" // x-release-please-version
}
```

### Builds

```kotlin
monorepoBuild {
    baseBranch = "main"           // branch to compare against for branch-mode tasks; defaults to "main"
    commitRef = "HEAD~1"          // commit SHA, tag, or ref for ref-mode tasks; defaults to "HEAD~1"; can be overridden at runtime via -PmonorepoBuild.commitRef=<sha>
    includeUntracked = true       // include files not yet tracked by git; defaults to true (branch-mode only)
    excludePatterns = listOf(     // regex patterns for files to exclude globally across all projects
        ".*\\.md",
        "docs/.*"
    )
}
```

Individual subprojects can declare their own exclude patterns using the `monorepoProjectConfig` extension. Patterns are matched against paths relative to the subproject directory and are applied after global `excludePatterns`.

```kotlin
// In :api/build.gradle.kts
monorepoProjectConfig {
    excludePatterns = listOf(     // regex patterns relative to this subproject's directory
        "generated/.*",
        ".*\\.json"
    )
}
```

#### `printChangedProjectsFromBranch`

Prints a human-readable report of which projects have changed and which are transitively affected, comparing against `baseBranch`.

```bash
./gradlew printChangedProjectsFromBranch
```

#### `buildChangedProjectsFromBranch`

Builds all affected projects (including transitive dependents), comparing against `baseBranch`. Useful before opening a pull request to verify only your changed modules build correctly.

```bash
./gradlew buildChangedProjectsFromBranch
```

#### `printChangedProjectsFromRef`

Prints a human-readable report of which projects changed since a specific commit ref. Defaults to `HEAD~1`.

```bash
# Use the default (HEAD~1)
./gradlew printChangedProjectsFromRef

# Override with a specific SHA
./gradlew printChangedProjectsFromRef -PmonorepoBuild.commitRef=abc123
```

#### `buildChangedProjectsFromRef`

Builds all affected projects since a specific commit ref. Defaults to `HEAD~1`, so it works out of the box for pipelines that trigger on every commit. Override with a specific SHA when your pipeline tracks the last successful build.

```bash
# Build what changed since the previous commit (default)
./gradlew buildChangedProjectsFromRef

# Build what changed since a specific SHA (e.g., last successful CI build)
./gradlew buildChangedProjectsFromRef -PmonorepoBuild.commitRef=abc123def456
```

> **Note:** Ref-mode tasks use a two-dot diff (`git diff <ref> HEAD`), which only considers committed changes. Staged and untracked files are intentionally ignored — this mode is designed for clean CI workspaces.

#### `writeChangedProjectsFromRef`

Writes the list of affected project paths to a file — one path per line, no headers or annotations. Designed for consumption by shell scripts in CI/CD pipelines.

```bash
./gradlew writeChangedProjectsFromRef -PmonorepoBuild.commitRef=abc123
```

**Default output file:** `build/monorepo/changed-projects.txt`

Example output:
```
:common-lib
:modules:module1
:apps:app1
```

An empty file is written when nothing has changed, so downstream scripts can always assume the file exists after the task runs.

**Override the output path at runtime** (no build script changes needed):

```bash
./gradlew writeChangedProjectsFromRef \
  -PmonorepoBuild.commitRef=abc123 \
  -PmonorepoBuild.outputFile=ci/changed-projects.txt
```

**Override the output path permanently in the build script:**

```kotlin
tasks.named<io.github.doughawley.monorepo.build.task.WriteChangedProjectsFromRefTask>(
    "writeChangedProjectsFromRef"
) {
    outputFile.set(layout.projectDirectory.file("ci/changed-projects.txt"))
}
```

#### Access changed projects in other tasks

The plugin computes results during the configuration phase, so any task can access them directly from the `monorepoBuild` extension — no `dependsOn` needed:

```kotlin
tasks.register("customTask") {
    doLast {
        val extension = project.extensions.getByType(
            io.github.doughawley.monorepo.build.MonorepoBuildExtension::class.java
        )
        val changedProjects = extension.allAffectedProjects
        println("Changed projects: $changedProjects")

        changedProjects.forEach { projectPath ->
            println("Affected: $projectPath")
        }
    }
}
```

### Releases

Each subproject manages its own semantic version using git tags of the form `{globalTagPrefix}/{projectPrefix}/v{version}` (e.g. `release/api/v1.2.0`). Release is opt-in per subproject.

#### Opting in a subproject

In each subproject's `build.gradle.kts`:

```kotlin
monorepoReleaseConfig {
    enabled = true
}
```

The tag prefix is derived automatically from the Gradle path (`:api:core` → `api-core`). Override it if needed:

```kotlin
monorepoReleaseConfig {
    enabled = true
    tagPrefix = "my-api"
}
```

#### Global configuration

```kotlin
monorepoRelease {
    globalTagPrefix = "release"       // prefix for all tags and branches; default "release"
    primaryBranchScope = "minor"      // version bump on the primary branch; "minor" or "major"; default "minor"
    releaseBranchPatterns = listOf(   // regex patterns for allowed release branches
        "^main$",
        "^release/.*"
    )
}
```

#### `releaseChangedProjects`

Builds all opted-in projects that changed since the configured commit ref, then releases each one:

```bash
./gradlew releaseChangedProjects -PmonorepoBuild.commitRef=abc123
```

#### `:subproject:release`

Releases a single subproject manually, regardless of whether it changed:

```bash
./gradlew :api:release
```

Override the version bump scope (primary branch only):

```bash
./gradlew :api:release -Prelease.scope=major
```

#### Versioning rules

- First release of a project starts at `0.1.0`
- Releases from the primary branch bump using `primaryBranchScope` (default `minor`)
- Releases from a release branch (`release/api/v1.2.x`) always apply a `patch` bump
- The subproject must be built before releasing — `release` requires `build` to have run

## Configuration Reference

### `monorepoBuild`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `baseBranch` | String | `"main"` | The git branch to compare against (branch-mode tasks) |
| `commitRef` | String | `"HEAD~1"` | Commit SHA, tag, or ref expression to compare against HEAD (ref-mode tasks). Can also be supplied at runtime via `-PmonorepoBuild.commitRef=<sha>`, which takes precedence over the DSL value |
| `includeUntracked` | Boolean | `true` | Whether to include untracked files in detection (branch-mode only) |
| `excludePatterns` | List\<String\> | `[]` | Regex patterns for files to exclude globally across all projects |

### `monorepoProjectConfig`

Applied per subproject. Patterns are matched against paths **relative to the subproject directory** and applied **after** global `excludePatterns`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `excludePatterns` | List\<String\> | `[]` | Regex patterns for files to exclude in this subproject |

### `monorepoRelease`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `globalTagPrefix` | String | `"release"` | Prefix used in all tag and release branch names |
| `primaryBranchScope` | String | `"minor"` | Version bump scope when releasing from the primary branch; `"minor"` or `"major"` |
| `releaseBranchPatterns` | List\<String\> | `["^main$", "^release/.*"]` | Regex patterns for branches from which releases are permitted |

### `monorepoReleaseConfig`

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
- The base branch doesn't exist locally or remotely
- You haven't fetched the remote branch (`git fetch origin`)
- Git is not installed or not in the PATH

Solution:
```bash
git fetch origin
./gradlew printChangedProjectsFromBranch
```

### No projects detected despite changes

Check your `excludePatterns` configuration - you may be inadvertently excluding files. Enable logging to see what files are being detected:

```bash
./gradlew printChangedProjectsFromBranch --info
```

### Root project always shows as changed

This is expected if files in the root directory (outside of subproject directories) have changed. To prevent this, ensure all code is within subproject directories.

## Requirements

- Gradle 7.0 or higher
- Git installed and available in PATH
- Java 17 or higher

## Support & Contributions

- **Issues**: Report bugs or request features via [GitHub Issues](https://github.com/doug-hawley/monorepo-build-release-plugin/issues)
- **Contributing**: See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup and guidelines
- **Questions**: Start a discussion in [GitHub Discussions](https://github.com/doug-hawley/monorepo-build-release-plugin/discussions)

## License

MIT License - see [LICENSE](LICENSE) file for details
