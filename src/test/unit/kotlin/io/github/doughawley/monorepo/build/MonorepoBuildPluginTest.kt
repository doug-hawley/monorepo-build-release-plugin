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
        val task = project.tasks.findByName("printChangedProjects")
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
        val rootExtension = project.extensions.getByType(MonorepoExtension::class.java)
        val buildExtension = rootExtension.build

        // then
        rootExtension.primaryBranch shouldBe "main"
        buildExtension.lastSuccessfulBuildTag shouldBe "monorepo/last-successful-build"
        buildExtension.includeUntracked shouldBe true
        buildExtension.excludePatterns shouldBe emptyList()
    }

    test("extension can be configured") {
        // given
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.doug-hawley.monorepo-build-release-plugin")
        val rootExtension = project.extensions.getByType(MonorepoExtension::class.java)
        val buildExtension = rootExtension.build

        // when
        rootExtension.primaryBranch = "develop"
        buildExtension.includeUntracked = false
        buildExtension.excludePatterns = listOf(".*\\.md", "docs/.*")

        // then
        rootExtension.primaryBranch shouldBe "develop"
        buildExtension.includeUntracked shouldBe false
        buildExtension.excludePatterns.size shouldBe 2
    }

    test("task has correct group and description") {
        // given
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.doug-hawley.monorepo-build-release-plugin")

        // when
        val task = project.tasks.findByName("printChangedProjects")

        // then
        task shouldNotBe null
        task!!.group shouldBe "monorepo"
        task.description shouldBe "Detects which projects have changed based on git history"
    }
})
