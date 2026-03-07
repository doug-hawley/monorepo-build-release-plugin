package io.github.doughawley.monorepo.release.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NextVersionResolverTest : FunSpec({

    test("forReleaseBranch with no prior tags in version line returns major.minor.0") {
        // given — on release branch v0.2.x with no v0.2.* tags
        val latestInLine: SemanticVersion? = null

        // when
        val result = NextVersionResolver.forReleaseBranch(latestInLine, major = 0, minor = 2, Scope.PATCH)

        // then — should be v0.2.0, NOT v0.1.0
        result shouldBe SemanticVersion(0, 2, 0)
    }

    test("forReleaseBranch with no prior tags on v1.0.x returns v1.0.0") {
        // given
        val latestInLine: SemanticVersion? = null

        // when
        val result = NextVersionResolver.forReleaseBranch(latestInLine, major = 1, minor = 0, Scope.PATCH)

        // then
        result shouldBe SemanticVersion(1, 0, 0)
    }

    test("forReleaseBranch with existing tag bumps patch") {
        // given — on release branch v0.1.x with v0.1.1 as latest
        val latestInLine = SemanticVersion(0, 1, 1)

        // when
        val result = NextVersionResolver.forReleaseBranch(latestInLine, major = 0, minor = 1, Scope.PATCH)

        // then
        result shouldBe SemanticVersion(0, 1, 2)
    }

    test("forReleaseBranch with no prior tags on v3.5.x returns v3.5.0") {
        // given
        val latestInLine: SemanticVersion? = null

        // when
        val result = NextVersionResolver.forReleaseBranch(latestInLine, major = 3, minor = 5, Scope.PATCH)

        // then
        result shouldBe SemanticVersion(3, 5, 0)
    }
})
