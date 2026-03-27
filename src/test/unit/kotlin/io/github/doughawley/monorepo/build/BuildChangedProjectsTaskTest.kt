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

    test("buildChanged task should be registered on subprojects") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        ProjectBuilder.builder()
            .withName("sub")
            .withParent(rootProject)
            .build()

        // when
        rootProject.pluginManager.apply("io.github.doug-hawley.monorepo-build-release-plugin")

        // then
        val subproject = rootProject.findProject(":sub")!!
        val task = subproject.tasks.findByName("buildChanged")
        task shouldNotBe null
        task?.group shouldBe "monorepo"
        task?.description shouldBe "Builds this project and runs build on any changed upstream dependencies"
    }

    test("releaseChanged task should be registered") {
        // given
        val project = ProjectBuilder.builder().build()

        // when
        project.pluginManager.apply("io.github.doug-hawley.monorepo-build-release-plugin")

        // then
        val task = project.tasks.findByName("releaseChanged")
        task shouldNotBe null
        task?.group shouldBe "monorepo"
        task?.description shouldBe "Builds changed projects and creates release branches"
    }
})
