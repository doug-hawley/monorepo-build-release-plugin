package io.github.doughawley.monorepo.release.domain

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NextVersionResolverTest : FunSpec({

    // ── forMainBranch ────────────────────────────────────────────

    test("forMainBranch with no prior tags returns 0.1.0") {
        // given
        val latestVersion: SemanticVersion? = null

        // when
        val result = NextVersionResolver.forMainBranch(latestVersion, Scope.MINOR)

        // then
        result shouldBe SemanticVersion(0, 1, 0)
    }

    test("forMainBranch with no prior tags and major scope returns 0.1.0") {
        // given
        val latestVersion: SemanticVersion? = null

        // when
        val result = NextVersionResolver.forMainBranch(latestVersion, Scope.MAJOR)

        // then
        result shouldBe SemanticVersion(0, 1, 0)
    }

    test("forMainBranch with existing tag bumps minor") {
        // given
        val latestVersion = SemanticVersion(0, 1, 0)

        // when
        val result = NextVersionResolver.forMainBranch(latestVersion, Scope.MINOR)

        // then
        result shouldBe SemanticVersion(0, 2, 0)
    }

    test("forMainBranch with existing tag bumps major") {
        // given
        val latestVersion = SemanticVersion(0, 2, 0)

        // when
        val result = NextVersionResolver.forMainBranch(latestVersion, Scope.MAJOR)

        // then
        result shouldBe SemanticVersion(1, 0, 0)
    }

    // ── forMainBranch with minimumVersion ─────────────────────────

    test("forMainBranch with no prior tags and minimumVersion bumps from minimum") {
        // given — adopting plugin on a project whose latest external release is 1.2.3
        val latestVersion: SemanticVersion? = null
        val minimumVersion = SemanticVersion(1, 2, 3)

        // when
        val result = NextVersionResolver.forMainBranch(latestVersion, Scope.MINOR, minimumVersion)

        // then — should bump minor from 1.2.3 → 1.3.0
        result shouldBe SemanticVersion(1, 3, 0)
    }

    test("forMainBranch with no prior tags and minimumVersion bumps major from minimum") {
        // given
        val latestVersion: SemanticVersion? = null
        val minimumVersion = SemanticVersion(1, 2, 3)

        // when
        val result = NextVersionResolver.forMainBranch(latestVersion, Scope.MAJOR, minimumVersion)

        // then — should bump major from 1.2.3 → 2.0.0
        result shouldBe SemanticVersion(2, 0, 0)
    }

    test("forMainBranch with existing tags above minimumVersion ignores minimum") {
        // given — plugin tags already exceed the minimum
        val latestVersion = SemanticVersion(3, 0, 0)
        val minimumVersion = SemanticVersion(1, 2, 3)

        // when
        val result = NextVersionResolver.forMainBranch(latestVersion, Scope.MINOR, minimumVersion)

        // then — tags win, bumps from 3.0.0 → 3.1.0
        result shouldBe SemanticVersion(3, 1, 0)
    }

    // ── forReleaseBranch ─────────────────────────────────────────

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
