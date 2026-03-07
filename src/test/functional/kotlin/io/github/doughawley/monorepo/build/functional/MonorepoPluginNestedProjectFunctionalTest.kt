package io.github.doughawley.monorepo.build.functional

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

/**
 * Functional tests for three-level-deep project hierarchies (e.g. :services:billing:api).
 */
class MonorepoPluginNestedProjectFunctionalTest : FunSpec({
    val testProjectListener = listener(TestProjectListener())

    fun createNestedProject() = TestProjectBuilder(testProjectListener.getTestProjectDir())
        .withSubproject("services/billing/api")
        .withSubproject("services/billing/impl", dependsOn = listOf("services/billing/api"))
        .withSubproject("services/payments/gateway", dependsOn = listOf("services/billing/api"))
        .withSubproject("apps/web", dependsOn = listOf("services/payments/gateway"))
        .applyPlugin()
        .withRemote()
        .build()
        .also { project ->
            project.initGit()
            project.commitAll("Initial commit")
            project.pushToRemote()
        }

    test("plugin detects change in three-level-deep project") {
        // given
        val project = createNestedProject()

        // when
        project.appendToFile("services/billing/api/src/main/kotlin/com/example/Api.kt", "\n// Modified")
        project.commitAll("Change billing api")
        val result = project.runTask("printChangedProjects")

        // then
        result.task(":printChangedProjects")?.outcome shouldBe org.gradle.testkit.runner.TaskOutcome.SUCCESS
        val changed = result.extractChangedProjects()
        changed shouldContain ":services:billing:api"
    }

    test("change in deeply nested project propagates to all transitive dependents") {
        // given
        val project = createNestedProject()

        // when
        project.appendToFile("services/billing/api/src/main/kotlin/com/example/Api.kt", "\n// Modified")
        project.commitAll("Change billing api")
        val result = project.runTask("printChangedProjects")

        // then
        result.task(":printChangedProjects")?.outcome shouldBe org.gradle.testkit.runner.TaskOutcome.SUCCESS
        val changed = result.extractChangedProjects()
        changed shouldContainAll setOf(
            ":services:billing:api",
            ":services:billing:impl",
            ":services:payments:gateway",
            ":apps:web"
        )
    }

    test("change in mid-level nested project does not affect unrelated sibling branch") {
        // given
        val project = createNestedProject()

        // when
        project.appendToFile("services/payments/gateway/src/main/kotlin/com/example/Gateway.kt", "\n// Modified")
        project.commitAll("Change payments gateway")
        val result = project.runTask("printChangedProjects")

        // then
        result.task(":printChangedProjects")?.outcome shouldBe org.gradle.testkit.runner.TaskOutcome.SUCCESS
        val changed = result.extractChangedProjects()
        changed shouldContainAll setOf(":services:payments:gateway", ":apps:web")
        changed shouldNotContain ":services:billing:api"
        changed shouldNotContain ":services:billing:impl"
    }

    test("change in deepest leaf project with no dependents flags only that project") {
        // given
        val project = createNestedProject()

        // when
        project.appendToFile("services/billing/impl/src/main/kotlin/com/example/Impl.kt", "\n// Modified")
        project.commitAll("Change billing impl")
        val result = project.runTask("printChangedProjects")

        // then
        result.task(":printChangedProjects")?.outcome shouldBe org.gradle.testkit.runner.TaskOutcome.SUCCESS
        val changed = result.extractChangedProjects()
        changed shouldBe setOf(":services:billing:impl")
    }

    test("file-to-project mapping is correct for three-level-deep paths") {
        // given
        val project = createNestedProject()

        // when
        project.appendToFile("services/payments/gateway/src/main/kotlin/com/example/Gateway.kt", "\n// Modified")
        project.commitAll("Change gateway")
        val result = project.runTask("printChangedProjects")

        // then
        val changed = result.extractChangedProjects()
        changed shouldContain ":services:payments:gateway"
        changed shouldNotContain ":gateway"
        changed shouldNotContain ":payments:gateway"
    }
})
