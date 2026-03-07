# Publishing Checklist for Gradle Plugin Portal

## ✅ Pre-Publishing Setup Complete

Your plugin is now configured and ready to publish! Here's what's already in place:

### Build Configuration ✅
- [x] Plugin Publish plugin added (`com.gradle.plugin-publish` v1.3.0)
- [x] Plugin metadata configured (website, vcsUrl, tags)
- [x] Plugin ID: `io.github.doug-hawley.monorepo-build-release-plugin`
- [x] Group: `io.github.doug-hawley`
- [x] Version: `1.0.0` (in build.gradle.kts)

### GitHub Actions Workflows ✅
- [x] **release-please.yml** - Automated releases with conventional commits
- [x] **release.yml** - Manual/tag-based releases (publish step now ACTIVE)
- [x] **ci.yml** - Continuous integration testing

### Documentation ✅
- [x] CHANGELOG.md properly formatted
- [x] README.md with usage examples
- [x] RELEASE_PLEASE_GUIDE.md for contributors

---

## 🚀 Publishing Steps

### Step 1: Get Your Gradle Plugin Portal Credentials

1. Go to https://plugins.gradle.org/
2. Sign in with your GitHub account (or create an account)
3. Navigate to your profile → API Keys
4. Generate a new API key pair (if you don't have one):
   - You'll get a **Key** and a **Secret**
   - **IMPORTANT:** Save these securely - the secret is only shown once!

### Step 2: Add GitHub Secrets

1. Go to your GitHub repository: https://github.com/doug-hawley/monorepo-build-release-plugin
2. Navigate to: **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Add two secrets:

   **Secret 1:**
   - Name: `GRADLE_PUBLISH_KEY`
   - Value: [Your API Key from Plugin Portal]

   **Secret 2:**
   - Name: `GRADLE_PUBLISH_SECRET`
   - Value: [Your API Secret from Plugin Portal]

### Step 3: Test Publishing Locally (Optional but Recommended)

Before publishing to the portal, test the process locally:

```bash
# Set credentials as environment variables
export GRADLE_PUBLISH_KEY=your-key-here
export GRADLE_PUBLISH_SECRET=your-secret-here

# Validate plugin configuration
./gradlew validatePlugins

# Dry run (validates but doesn't publish)
./gradlew publishPlugins --validate-only
```

### Step 4: Choose Your Publishing Method

You have **two ways** to publish:

#### Option A: Automated Publishing with Release Please (Recommended)

1. Make commits using conventional commit format:
   ```bash
   git commit -m "feat: add awesome feature"
   git commit -m "fix: resolve bug"
   ```

2. Push to main:
   ```bash
   git push origin main
   ```

3. Release Please will automatically:
   - Create a release PR with version bump
   - Update CHANGELOG.md
   - Update version in build.gradle.kts

4. Review and merge the release PR

5. Once merged, the plugin will **automatically publish** to Gradle Plugin Portal!

#### Option B: Manual Publishing with Tag

1. Create and push a version tag:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```

2. The `release.yml` workflow will trigger and:
   - Build the plugin
   - Run all tests
   - Create a GitHub release
   - Publish to Gradle Plugin Portal

---

## 📋 First Release Checklist

Before your first release, verify:

### Code Quality
- [ ] All tests passing (`./gradlew test`)
- [ ] Plugin builds successfully (`./gradlew build`)
- [ ] Plugin descriptor valid (`./gradlew validatePlugins`)
- [ ] No TODO or FIXME comments in production code
- [ ] Version set to `1.0.0` in build.gradle.kts

### Documentation
- [ ] README.md has clear usage instructions
- [ ] CHANGELOG.md has entry for v1.0.0
- [ ] All public APIs have KDoc comments
- [ ] Examples are tested and working

### Plugin Portal Requirements
- [ ] Plugin ID is unique (check on plugins.gradle.org)
- [ ] Display name is clear and descriptive
- [ ] Description explains what the plugin does
- [ ] Tags are relevant (monorepo, git, ci, optimization, build)
- [ ] Website URL is correct (GitHub repo)
- [ ] VCS URL is correct (GitHub .git URL)

### GitHub Repository
- [ ] GitHub secrets configured (GRADLE_PUBLISH_KEY, GRADLE_PUBLISH_SECRET)
- [ ] Repository is public (required for Plugin Portal)
- [ ] License file exists (MIT License)
- [ ] README has badges (CI status)

---

## 🎯 What Happens When You Publish

### Via Release Please (Automatic)
1. **Commit** → conventional commit to main
2. **PR Created** → Release Please creates/updates release PR
3. **Review** → You review the changes and version bump
4. **Merge** → PR is merged to main
5. **Release** → GitHub release is created
6. **Publish** → Plugin automatically published to Plugin Portal
7. **Available** → Users can immediately use your plugin!

### Via Tag (Manual)
1. **Tag** → Create and push version tag (e.g., v1.0.0)
2. **Build** → GitHub Actions builds and tests
3. **Release** → GitHub release created with artifacts
4. **Publish** → Plugin published to Plugin Portal
5. **Available** → Users can immediately use your plugin!

### Timeline
- **Publishing to Portal:** Usually 5-15 minutes
- **Portal Indexing:** Additional 10-30 minutes
- **Searchable:** Plugin appears in search within 1 hour

---

## 📦 After Publishing

### Verify Publication
1. Check https://plugins.gradle.org/plugin/io.github.doug-hawley.monorepo-build-release-plugin
2. Verify version, description, and metadata
3. Check that the "Read More" link points to your GitHub repo

### Test Installation
Create a test project and verify users can install your plugin:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

// build.gradle.kts
plugins {
    id("io.github.doug-hawley.monorepo-build-release-plugin") version "0.3.2"
}

monorepo {
    primaryBranch = "main"
    build {
        includeUntracked = true
    }
}
```

```bash
./gradlew printChangedProjects
```

### Update Badges (Optional)
Add to README.md:

```markdown
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/io.github.doug-hawley.monorepo-build-release-plugin?logo=gradle)](https://plugins.gradle.org/plugin/io.github.doug-hawley.monorepo-build-release-plugin)
```

### Announce Your Plugin
- Tweet about it with #GradlePlugin
- Post on Reddit r/gradle
- Share in Kotlin Slack #gradle channel
- Write a blog post about it

---

## 🔄 Future Releases

For subsequent releases, the process is even easier:

### Using Release Please (Recommended)
1. Make conventional commits
2. Merge release PR when ready
3. Plugin automatically publishes

### Version Bumping Rules
- `feat:` → MINOR version bump (1.0.0 → 1.1.0)
- `fix:` → PATCH version bump (1.0.0 → 1.0.1)
- `feat!:` or `BREAKING CHANGE:` → MAJOR version bump (1.0.0 → 2.0.0)

---

## 🆘 Troubleshooting

### Publishing Fails - Authentication Error
- **Cause:** Invalid or missing GRADLE_PUBLISH_KEY/SECRET
- **Fix:** Verify secrets are set correctly in GitHub, no extra spaces

### Publishing Fails - Plugin ID Already Exists
- **Cause:** Plugin ID is taken by another developer
- **Fix:** Change the plugin ID in build.gradle.kts and try again

### Publishing Succeeds but Plugin Not Found
- **Cause:** Portal indexing delay
- **Fix:** Wait 30-60 minutes, it will appear

### Version Already Published
- **Cause:** You can't republish the same version
- **Fix:** Bump the version and publish again

### Validation Fails
- **Cause:** Missing required metadata (website, vcsUrl, etc.)
- **Fix:** Run `./gradlew validatePlugins` to see specific issues

---

## 📚 Resources

- [Gradle Plugin Portal](https://plugins.gradle.org/)
- [Publishing Guide](https://plugins.gradle.org/docs/publish-plugin)
- [Plugin Development Docs](https://docs.gradle.org/current/userguide/custom_plugins.html)
- [Release Please Docs](https://github.com/googleapis/release-please)
- [Conventional Commits](https://www.conventionalcommits.org/)

---

## ✨ You're Ready!

Your plugin is **fully configured and ready to publish**. Just add your GitHub secrets and either:

1. **Merge a Release Please PR** (automatic publishing), or
2. **Push a version tag** (manual publishing)

Good luck with your first release! 🚀
