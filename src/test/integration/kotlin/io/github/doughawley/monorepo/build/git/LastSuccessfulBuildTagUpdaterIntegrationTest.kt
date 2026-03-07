package io.github.doughawley.monorepo.build.git

import io.github.doughawley.monorepo.git.GitCommandExecutor
import io.github.doughawley.monorepo.release.git.TempGitRepo
import io.github.doughawley.monorepo.release.git.TempGitRepoListener
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import org.gradle.api.logging.Logger

class LastSuccessfulBuildTagUpdaterIntegrationTest : FunSpec({

    val repoListener = TempGitRepoListener()
    listener(repoListener)

    val logger = mockk<Logger>(relaxed = true)

    fun createUpdater(): LastSuccessfulBuildTagUpdater {
        val executor = GitCommandExecutor(logger)
        return LastSuccessfulBuildTagUpdater(repoListener.repo.localDir, executor, logger)
    }

    test("creates tag locally and pushes it to remote") {
        // given
        val updater = createUpdater()

        // when
        updater.updateTag("monorepo/last-successful-build")

        // then
        repoListener.repo.localTagExists("monorepo/last-successful-build") shouldBe true
        repoListener.repo.remoteTagExists("monorepo/last-successful-build") shouldBe true
    }

    test("force-updates existing tag to HEAD after new commit") {
        // given: create initial tag, then make a new commit
        val updater = createUpdater()
        updater.updateTag("monorepo/last-successful-build")
        repoListener.repo.modifyTrackedFile("README.md", "new content")
        repoListener.repo.commitAll("second commit")

        // when
        updater.updateTag("monorepo/last-successful-build")

        // then: tag should point at the new HEAD, not the old commit
        repoListener.repo.localTagExists("monorepo/last-successful-build") shouldBe true
        repoListener.repo.remoteTagExists("monorepo/last-successful-build") shouldBe true
    }

    test("works with slash-delimited tag names") {
        // given
        val updater = createUpdater()

        // when
        updater.updateTag("custom/prefix/last-build")

        // then
        repoListener.repo.localTagExists("custom/prefix/last-build") shouldBe true
        repoListener.repo.remoteTagExists("custom/prefix/last-build") shouldBe true
    }
})
