package io.github.doughawley.monorepo.build

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.gradle.testfixtures.ProjectBuilder

class BuildChangedProjectsTaskTest : FunSpec({

    test("buildChangedProjects task should be registered") {
        // given
        val project = ProjectBuilder.builder().build()

        // when
        project.pluginManager.apply("io.github.doug-hawley.monorepo-build-release-plugin")

        // then
        val task = project.tasks.findByName("buildChangedProjects")
        task shouldNotBe null
        task?.group shouldBe "monorepo"
        task?.description shouldBe "Builds only the projects that have been affected by changes"
    }

    test("createReleaseBranches task should be registered") {
        // given
        val project = ProjectBuilder.builder().build()

        // when
        project.pluginManager.apply("io.github.doug-hawley.monorepo-build-release-plugin")

        // then
        val task = project.tasks.findByName("createReleaseBranches")
        task shouldNotBe null
        task?.group shouldBe "monorepo-release"
        task?.description shouldBe "Creates release branches for changed projects"
    }
})
