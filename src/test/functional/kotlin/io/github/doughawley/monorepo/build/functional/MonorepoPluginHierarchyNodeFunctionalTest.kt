package io.github.doughawley.monorepo.build.functional

import io.github.doughawley.monorepo.build.functional.StandardTestProject.Files
import io.github.doughawley.monorepo.build.functional.StandardTestProject.Projects
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.TaskOutcome

/**
 * Functional tests verifying that intermediate directory nodes (e.g. :apps, :modules, :services:billing)
 * do not appear in changed or affected project results.
 */
class MonorepoPluginHierarchyNodeFunctionalTest : FunSpec({
    val testProjectListener = listener(TestProjectListener())

    test("hierarchy node does not appear in directly changed projects when child file changes") {
        // given
        val project = testProjectListener.createStandardProject()

        // when
        project.appendToFile(Files.APP1_SOURCE, "\n// Modified")
        project.commitAll("Change app1")
        val result = project.runTask("printChangedProjects")

        // then
        result.task(":printChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val directlyChanged = result.extractDirectlyChangedProjects()
        directlyChanged shouldContain Projects.APP1
        directlyChanged shouldNotContain ":apps"
    }

    test("hierarchy node does not appear in all affected projects when child file changes") {
        // given
        val project = testProjectListener.createStandardProject()

        // when
        project.appendToFile(Files.MODULE1_SOURCE, "\n// Modified")
        project.commitAll("Change module1")
        val result = project.runTask("printChangedProjects")

        // then
        result.task(":printChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val changed = result.extractChangedProjects()
        changed shouldContain Projects.MODULE1
        changed shouldContain Projects.APP1
        changed shouldNotContain ":modules"
        changed shouldNotContain ":apps"
    }

    test("multiple hierarchy nodes are excluded when children in sibling trees change") {
        // given
        val project = testProjectListener.createStandardProject()

        // when
        project.appendToFile(Files.APP2_SOURCE, "\n// Modified")
        project.appendToFile(Files.MODULE2_SOURCE, "\n// Modified")
        project.commitAll("Change app2 and module2")
        val result = project.runTask("printChangedProjects")

        // then
        result.task(":printChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val directlyChanged = result.extractDirectlyChangedProjects()
        directlyChanged shouldContain Projects.APP2
        directlyChanged shouldContain Projects.MODULE2
        directlyChanged shouldNotContain ":apps"
        directlyChanged shouldNotContain ":modules"
    }

    test("untracked file in nested project does not cause hierarchy node to appear") {
        // given
        val project = testProjectListener.createStandardProject()

        // when
        project.createNewFile(
            "modules/module2/src/main/kotlin/com/example/NewFeature.kt",
            "package com.example\nclass NewFeature"
        )
        val result = project.runTask("printChangedProjects")

        // then
        result.task(":printChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val directlyChanged = result.extractDirectlyChangedProjects()
        directlyChanged shouldContain Projects.MODULE2
        directlyChanged shouldNotContain ":modules"
    }

    test("three-level hierarchy nodes do not appear for deeply nested project change") {
        // given
        val project = TestProjectBuilder(testProjectListener.getTestProjectDir())
            .withSubproject("services/billing/api")
            .withSubproject("services/billing/impl", dependsOn = listOf("services/billing/api"))
            .applyPlugin()
            .withRemote()
            .build()
        project.initGit()
        project.commitAll("Initial commit")
        project.pushToRemote()

        // when
        project.appendToFile(
            "services/billing/impl/src/main/kotlin/com/example/Impl.kt",
            "\n// Modified"
        )
        project.commitAll("Change billing impl")
        val result = project.runTask("printChangedProjects")

        // then
        result.task(":printChangedProjects")?.outcome shouldBe TaskOutcome.SUCCESS
        val directlyChanged = result.extractDirectlyChangedProjects()
        directlyChanged shouldContain ":services:billing:impl"
        directlyChanged shouldNotContain ":services"
        directlyChanged shouldNotContain ":services:billing"
    }
})
