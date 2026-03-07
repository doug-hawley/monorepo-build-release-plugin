package io.github.doughawley.monorepo.build.git

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk

class GitChangedFilesDetectorTest : FunSpec({

    val gitRepository = mockk<GitRepository>()
    val logger = mockk<org.gradle.api.logging.Logger>(relaxed = true)

    afterEach { clearAllMocks() }

    fun detector() = GitChangedFilesDetector(logger, gitRepository)

    test("returns empty set when not a git repository") {
        // given
        every { gitRepository.isRepository() } returns false

        // when
        val result = detector().getChangedFiles("origin/main", true, emptyList())

        // then
        result.shouldBeEmpty()
    }

    test("returns files from two-dot diff against resolved base ref") {
        // given
        every { gitRepository.isRepository() } returns true
        every { gitRepository.diffFromRef("origin/main") } returns listOf("module/Foo.kt", "module/Bar.kt")
        every { gitRepository.workingTreeChanges() } returns emptyList()
        every { gitRepository.stagedFiles() } returns emptyList()
        every { gitRepository.untrackedFiles() } returns emptyList()

        // when
        val result = detector().getChangedFiles("origin/main", true, emptyList())

        // then
        result shouldContainAll setOf("module/Foo.kt", "module/Bar.kt")
    }

    test("combines ref diff, working tree, staged, and untracked files when includeUntracked is true") {
        // given
        every { gitRepository.isRepository() } returns true
        every { gitRepository.diffFromRef("some-tag") } returns listOf("app/Foo.kt")
        every { gitRepository.workingTreeChanges() } returns listOf("lib/Bar.kt")
        every { gitRepository.stagedFiles() } returns listOf("lib/Baz.kt")
        every { gitRepository.untrackedFiles() } returns listOf("new/Qux.kt")

        // when
        val result = detector().getChangedFiles("some-tag", true, emptyList())

        // then
        result shouldContainAll setOf("app/Foo.kt", "lib/Bar.kt", "lib/Baz.kt", "new/Qux.kt")
    }

    test("only includes ref diff when includeUntracked is false") {
        // given
        every { gitRepository.isRepository() } returns true
        every { gitRepository.diffFromRef("origin/main") } returns listOf("committed.kt")

        // when
        val result = detector().getChangedFiles("origin/main", false, emptyList())

        // then
        result shouldBe setOf("committed.kt")
    }

    test("deduplicates files appearing in multiple sources") {
        // given
        every { gitRepository.isRepository() } returns true
        every { gitRepository.diffFromRef("origin/main") } returns listOf("shared/File.kt")
        every { gitRepository.workingTreeChanges() } returns listOf("shared/File.kt")
        every { gitRepository.stagedFiles() } returns emptyList()
        every { gitRepository.untrackedFiles() } returns emptyList()

        // when
        val result = detector().getChangedFiles("origin/main", true, emptyList())

        // then
        result shouldBe setOf("shared/File.kt")
    }

    test("applies exclude patterns to filter results") {
        // given
        every { gitRepository.isRepository() } returns true
        every { gitRepository.diffFromRef("origin/main") } returns listOf("code.kt", "README.md", "docs/guide.md")
        every { gitRepository.workingTreeChanges() } returns emptyList()
        every { gitRepository.stagedFiles() } returns emptyList()
        every { gitRepository.untrackedFiles() } returns emptyList()

        // when
        val result = detector().getChangedFiles("origin/main", true, listOf(".*\\.md", "docs/.*"))

        // then
        result shouldContain "code.kt"
        result shouldNotContain "README.md"
        result shouldNotContain "docs/guide.md"
    }

    test("propagates IllegalArgumentException from diffFromRef") {
        // given
        every { gitRepository.isRepository() } returns true
        every { gitRepository.diffFromRef("bad-ref") } throws
            IllegalArgumentException("Commit ref 'bad-ref' does not exist in this repository.")

        // when / then
        shouldThrow<IllegalArgumentException> {
            detector().getChangedFiles("bad-ref", false, emptyList())
        }
    }
})
