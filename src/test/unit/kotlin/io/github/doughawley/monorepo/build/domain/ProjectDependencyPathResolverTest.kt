package io.github.doughawley.monorepo.build.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

// Fakes deliberately don't implement org.gradle.api.artifacts.ProjectDependency: that interface's
// compile-time shape always matches whichever Gradle API version builds this project, so a real
// implementation of it can never be missing getPath() the way a genuinely old Gradle runtime would be.
// Plain lookalikes let us exercise both branches of the reflective fallback regardless of which
// Gradle version this test module happens to compile against.
private class FakeDependencyWithGetPath(private val path: String) {
    fun getPath(): String {
        return path
    }
}

private class FakeDependencyWithGetDependencyProject(private val project: Project) {
    fun getDependencyProject(): Project {
        return project
    }
}

private class FakeDependencyWithNeitherMethod

class ProjectDependencyPathResolverTest : FunSpec({

    test("should resolve path via getPath when present") {
        // given
        val logger = ProjectBuilder.builder().build().logger
        val resolver = ProjectDependencyPathResolver(logger)
        val dependency = FakeDependencyWithGetPath(":common-lib")

        // when
        val result = resolver.resolve(dependency)

        // then
        result shouldBe ":common-lib"
    }

    test("should fall back to getDependencyProject when getPath is absent") {
        // given
        val rootProject = ProjectBuilder.builder().build()
        val depProject = ProjectBuilder.builder()
            .withParent(rootProject)
            .withName("common-lib")
            .build()
        val resolver = ProjectDependencyPathResolver(rootProject.logger)
        val dependency = FakeDependencyWithGetDependencyProject(depProject)

        // when
        val result = resolver.resolve(dependency)

        // then
        result shouldBe ":common-lib"
    }

    test("should return null when neither method is present") {
        // given
        val logger = ProjectBuilder.builder().build().logger
        val resolver = ProjectDependencyPathResolver(logger)
        val dependency = FakeDependencyWithNeitherMethod()

        // when
        val result = resolver.resolve(dependency)

        // then
        result shouldBe null
    }
})
