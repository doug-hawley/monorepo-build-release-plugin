package io.github.doughawley.monorepo.build

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.gradle.testfixtures.ProjectBuilder

class BuildChangedProjectsTaskTest : FunSpec({

    test("buildChanged task should be registered") {
        // given
        val project = ProjectBuilder.builder().build()

        // when
        project.pluginManager.apply("io.github.doug-hawley.monorepo-build-release-plugin")

        // then
        val task = project.tasks.findByName("buildChanged")
        task shouldNotBe null
        task?.group shouldBe "monorepo"
        task?.description shouldBe "Builds only the projects that have been affected by changes"
    }

    test("prepareReleases task should be registered") {
        // given
        val project = ProjectBuilder.builder().build()

        // when
        project.pluginManager.apply("io.github.doug-hawley.monorepo-build-release-plugin")

        // then
        val task = project.tasks.findByName("prepareReleases")
        task shouldNotBe null
        task?.group shouldBe "monorepo"
        task?.description shouldBe "Prepares releases by creating release branches for changed projects"
    }
})
