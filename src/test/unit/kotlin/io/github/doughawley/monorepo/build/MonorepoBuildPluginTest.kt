package io.github.doughawley.monorepo.build

import io.github.doughawley.monorepo.MonorepoExtension
import io.github.doughawley.monorepo.build.task.PrintChangedProjectsTask
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.gradle.testfixtures.ProjectBuilder

class MonorepoBuildPluginTest : FunSpec({

    test("plugin registers task") {
        // given
        val project = ProjectBuilder.builder().build()

        // when
        project.pluginManager.apply("io.github.doug-hawley.monorepo-build-release-plugin")

        // then
        val task = project.tasks.findByName("printChangedProjectsFromBranch")
        task shouldNotBe null
        task.shouldBeInstanceOf<PrintChangedProjectsTask>()
    }

    test("plugin registers extension") {
        // given
        val project = ProjectBuilder.builder().build()

        // when
        project.pluginManager.apply("io.github.doug-hawley.monorepo-build-release-plugin")

        // then
        val extension = project.extensions.findByName("monorepo")
        extension shouldNotBe null
        extension.shouldBeInstanceOf<MonorepoExtension>()
    }

    test("extension has correct defaults") {
        // given
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.doug-hawley.monorepo-build-release-plugin")

        // when
        val extension = project.extensions.getByType(MonorepoExtension::class.java).build

        // then
        extension.baseBranch shouldBe "main"
        extension.includeUntracked shouldBe true
        extension.excludePatterns shouldBe emptyList()
    }

    test("extension can be configured") {
        // given
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.doug-hawley.monorepo-build-release-plugin")
        val extension = project.extensions.getByType(MonorepoExtension::class.java).build

        // when
        extension.baseBranch = "develop"
        extension.includeUntracked = false
        extension.excludePatterns = listOf(".*\\.md", "docs/.*")

        // then
        extension.baseBranch shouldBe "develop"
        extension.includeUntracked shouldBe false
        extension.excludePatterns.size shouldBe 2
    }

    test("task has correct group and description") {
        // given
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.doug-hawley.monorepo-build-release-plugin")

        // when
        val task = project.tasks.findByName("printChangedProjectsFromBranch")

        // then
        task shouldNotBe null
        task!!.group shouldBe "monorepo"
        task.description shouldBe "Detects which projects have changed based on git history"
    }

    test("plugin can detect projects with no git repository") {
        // given
        val tempDir = kotlin.io.path.createTempDirectory("test-no-git").toFile()
        try {
            val project = ProjectBuilder.builder()
                .withProjectDir(tempDir)
                .build()
            project.pluginManager.apply("io.github.doug-hawley.monorepo-build-release-plugin")
            val task = project.tasks.findByName("printChangedProjectsFromBranch") as PrintChangedProjectsTask

            // when
            task.detectChanges()

            // then
            val extension = project.extensions.getByType(MonorepoExtension::class.java).build
            extension.allAffectedProjects shouldBe emptySet()
        } finally {
            tempDir.deleteRecursively()
        }
    }
})
