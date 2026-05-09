package io.github.doughawley.monorepo.release.git

import io.github.doughawley.monorepo.git.GitCommandExecutor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.gradle.api.logging.Logger

class DevTagScannerIntegrationTest : FunSpec({

    val repoListener = TempGitRepoListener()
    extension(repoListener)

    val logger = mockk<Logger>(relaxed = true)

    // findLatestDevTagNumber

    test("findLatestDevTagNumber returns null when remote has no dev tags") {
        // given
        val scanner = DevTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.findLatestDevTagNumber("app", "feature-auth")

        // then
        result.shouldBeNull()
    }

    test("findLatestDevTagNumber returns the single tag number pushed to remote") {
        // given
        repoListener.repo.pushTag("dev/app/feature-auth/1")
        val scanner = DevTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.findLatestDevTagNumber("app", "feature-auth")

        // then
        result shouldBe 1
    }

    test("findLatestDevTagNumber returns the maximum number when multiple tags exist") {
        // given
        repoListener.repo.pushTag("dev/app/feature-auth/1")
        repoListener.repo.pushTag("dev/app/feature-auth/2")
        repoListener.repo.pushTag("dev/app/feature-auth/3")
        val scanner = DevTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.findLatestDevTagNumber("app", "feature-auth")

        // then
        result shouldBe 3
    }

    test("findLatestDevTagNumber ignores dev tags for a different project prefix") {
        // given
        repoListener.repo.pushTag("dev/other-app/feature-auth/5")
        val scanner = DevTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.findLatestDevTagNumber("app", "feature-auth")

        // then
        result.shouldBeNull()
    }

    test("findLatestDevTagNumber ignores dev tags for a different branch name") {
        // given
        repoListener.repo.pushTag("dev/app/other-branch/5")
        val scanner = DevTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.findLatestDevTagNumber("app", "feature-auth")

        // then
        result.shouldBeNull()
    }

    test("findLatestDevTagNumber does not return local-only tags") {
        // given
        repoListener.repo.createLocalTag("dev/app/feature-auth/1")
        val scanner = DevTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.findLatestDevTagNumber("app", "feature-auth")

        // then
        result.shouldBeNull()
    }

    // devBranchExistsOnRemote

    test("devBranchExistsOnRemote returns true when branch is pushed") {
        // given
        repoListener.repo.checkoutNewBranch("dev/app/feature-auth")
        repoListener.repo.createUntrackedFile("dev-file.txt")
        repoListener.repo.commitAll("dev branch commit")
        val executor = GitCommandExecutor(logger)
        executor.executeForOutput(repoListener.repo.localDir, "push", "origin", "dev/app/feature-auth")
        val scanner = DevTagScanner(repoListener.repo.localDir, executor)

        // when
        val result = scanner.devBranchExistsOnRemote("app", "feature-auth")

        // then
        result shouldBe true
    }

    test("devBranchExistsOnRemote returns false when branch only exists locally") {
        // given
        repoListener.repo.checkoutNewBranch("dev/app/feature-auth")
        val scanner = DevTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.devBranchExistsOnRemote("app", "feature-auth")

        // then
        result shouldBe false
    }

    test("devBranchExistsOnRemote returns false when no matching branch exists") {
        // given
        val scanner = DevTagScanner(repoListener.repo.localDir, GitCommandExecutor(logger))

        // when
        val result = scanner.devBranchExistsOnRemote("app", "feature-auth")

        // then
        result shouldBe false
    }
})
