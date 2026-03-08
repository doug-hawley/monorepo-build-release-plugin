package io.github.doughawley.monorepo.release.git

import io.github.doughawley.monorepo.git.GitCommandExecutor
import io.github.doughawley.monorepo.release.domain.SemanticVersion
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.gradle.api.logging.Logger

class GitTagScannerIntegrationTest : FunSpec({

    val repoListener = TempGitRepoListener()
    extension(repoListener)

    val logger = mockk<Logger>(relaxed = true)

    // findLatestVersion

    test("findLatestVersion returns null when remote has no tags") {
        // given
        val scanner = GitTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.findLatestVersion("release", "app")

        // then
        result.shouldBeNull()
    }

    test("findLatestVersion returns the single tag pushed to remote") {
        // given
        repoListener.repo.pushTag("release/app/v1.0.0")
        val scanner = GitTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.findLatestVersion("release", "app")

        // then
        result shouldBe SemanticVersion(1, 0, 0)
    }

    test("findLatestVersion returns the maximum version when multiple tags exist") {
        // given
        repoListener.repo.pushTag("release/app/v0.9.0")
        repoListener.repo.pushTag("release/app/v1.0.0")
        repoListener.repo.pushTag("release/app/v1.1.0")
        val scanner = GitTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.findLatestVersion("release", "app")

        // then
        result shouldBe SemanticVersion(1, 1, 0)
    }

    test("findLatestVersion ignores tags for a different project prefix") {
        // given
        repoListener.repo.pushTag("release/other-app/v2.0.0")
        val scanner = GitTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.findLatestVersion("release", "app")

        // then
        result.shouldBeNull()
    }

    test("findLatestVersion does not return local-only tags not pushed to remote") {
        // given
        repoListener.repo.createLocalTag("release/app/v3.0.0")
        val scanner = GitTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.findLatestVersion("release", "app")

        // then
        result.shouldBeNull()
    }

    // findLatestVersionInLine

    test("findLatestVersionInLine returns null when no tags exist in the version line") {
        // given
        repoListener.repo.pushTag("release/app/v1.0.0")
        val scanner = GitTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.findLatestVersionInLine("release", "app", 1, 2)

        // then
        result.shouldBeNull()
    }

    test("findLatestVersionInLine returns the highest patch version in the line") {
        // given
        repoListener.repo.pushTag("release/app/v1.2.0")
        repoListener.repo.pushTag("release/app/v1.2.3")
        repoListener.repo.pushTag("release/app/v1.2.1")
        val scanner = GitTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.findLatestVersionInLine("release", "app", 1, 2)

        // then
        result shouldBe SemanticVersion(1, 2, 3)
    }

    test("findLatestVersionInLine does not return tags from a different minor version line") {
        // given
        repoListener.repo.pushTag("release/app/v1.3.0")
        repoListener.repo.pushTag("release/app/v1.3.5")
        val scanner = GitTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.findLatestVersionInLine("release", "app", 1, 2)

        // then
        result.shouldBeNull()
    }

    // tagExists

    test("tagExists returns true for a locally created tag") {
        // given
        repoListener.repo.createLocalTag("release/app/v1.0.0")
        val scanner = GitTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.tagExists("release/app/v1.0.0")

        // then
        result shouldBe true
    }

    test("tagExists returns false when the tag does not exist") {
        // given
        val scanner = GitTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.tagExists("release/app/v1.0.0")

        // then
        result shouldBe false
    }

    test("tagExists returns false for a tag that exists only on remote") {
        // given
        repoListener.repo.pushTag("release/app/v1.0.0")
        repoListener.repo.deleteLocalTag("release/app/v1.0.0")
        val scanner = GitTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.tagExists("release/app/v1.0.0")

        // then
        result shouldBe false
    }
})
