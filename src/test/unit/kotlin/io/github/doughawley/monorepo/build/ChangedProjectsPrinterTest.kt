package io.github.doughawley.monorepo.build

import io.github.doughawley.monorepo.build.domain.MonorepoProjects
import io.github.doughawley.monorepo.build.domain.ProjectMetadata
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ChangedProjectsPrinterTest : FunSpec({

    test("returns no-changes message when no projects are affected") {
        // given
        val printer = ChangedProjectsPrinter()

        // when
        val result = printer.buildReport(
            header = "Changed projects:",
            monorepoProjects = MonorepoProjects(emptyList())
        )

        // then
        result shouldBe "No projects have changed."
    }

    test("includes custom header in the output") {
        // given
        val printer = ChangedProjectsPrinter()
        val apiMetadata = ProjectMetadata("api", ":api", changedFiles = listOf("api/src/main/kotlin/Api.kt"))

        // when
        val result = printer.buildReport(
            header = "Changed projects (since abc123):",
            monorepoProjects = MonorepoProjects(listOf(apiMetadata))
        )

        // then
        result shouldContain "Changed projects (since abc123):"
    }

    test("lists directly changed project path") {
        // given
        val printer = ChangedProjectsPrinter()
        val apiMetadata = ProjectMetadata("api", ":api", changedFiles = listOf("api/src/main/kotlin/Api.kt"))

        // when
        val result = printer.buildReport(
            header = "Changed projects:",
            monorepoProjects = MonorepoProjects(listOf(apiMetadata))
        )

        // then
        result shouldContain ":api"
    }

    test("lists transitively affected project with via annotation") {
        // given
        val apiMetadata = ProjectMetadata(
            name = "api",
            fullyQualifiedName = ":api",
            changedFiles = listOf("api/src/main/kotlin/Api.kt")
        )
        val appMetadata = ProjectMetadata(
            name = "app",
            fullyQualifiedName = ":app",
            dependencies = listOf(apiMetadata)
        )
        val printer = ChangedProjectsPrinter()

        // when
        val result = printer.buildReport(
            header = "Changed projects:",
            monorepoProjects = MonorepoProjects(listOf(apiMetadata, appMetadata))
        )

        // then
        result shouldContain ":api"
        result shouldContain ":app"
        result shouldContain "affected via :api"
    }

    test("displays root project as ': (root)' instead of ':'") {
        // given
        val printer = ChangedProjectsPrinter()
        val rootMetadata = ProjectMetadata("my-project", ":", changedFiles = listOf("build.gradle.kts"))

        // when
        val result = printer.buildReport(
            header = "Changed projects:",
            monorepoProjects = MonorepoProjects(listOf(rootMetadata))
        )

        // then
        result shouldContain ": (root)"
    }

    test("lists transitively affected project with multiple via annotations") {
        // given
        val apiMetadata = ProjectMetadata(
            name = "api",
            fullyQualifiedName = ":api",
            changedFiles = listOf("api/src/main/kotlin/Api.kt")
        )
        val coreMetadata = ProjectMetadata(
            name = "core",
            fullyQualifiedName = ":core",
            changedFiles = listOf("core/src/main/kotlin/Core.kt")
        )
        val appMetadata = ProjectMetadata(
            name = "app",
            fullyQualifiedName = ":app",
            dependencies = listOf(apiMetadata, coreMetadata)
        )
        val printer = ChangedProjectsPrinter()

        // when
        val result = printer.buildReport(
            header = "Changed projects:",
            monorepoProjects = MonorepoProjects(listOf(apiMetadata, coreMetadata, appMetadata))
        )

        // then
        result shouldContain ":app"
        result shouldContain "affected via :api, :core"
    }

    test("multiple directly changed projects are listed sorted") {
        // given
        val printer = ChangedProjectsPrinter()
        val alphaMetadata = ProjectMetadata("alpha", ":alpha", changedFiles = listOf("alpha/src/main/kotlin/Alpha.kt"))
        val betaMetadata = ProjectMetadata("beta", ":beta", changedFiles = listOf("beta/src/main/kotlin/Beta.kt"))

        // when
        val result = printer.buildReport(
            header = "Changed projects:",
            monorepoProjects = MonorepoProjects(listOf(alphaMetadata, betaMetadata))
        )

        // then
        val alphaIndex = result.indexOf(":alpha")
        val betaIndex = result.indexOf(":beta")
        (alphaIndex < betaIndex) shouldBe true
    }
})
