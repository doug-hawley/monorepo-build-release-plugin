# Changelog

All notable changes to this project will be documented in this file.

See [Conventional Commits](https://conventionalcommits.org) for commit guidelines.

## [0.3.3](https://github.com/doug-hawley/monorepo-build-release-plugin/compare/v0.3.2...v0.3.3) (2026-03-04)


### Features

* add monorepo-release-plugin with per-project release task ([ddd3a75](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/ddd3a75b6aadd2fa26dca2f6c58b47d66f75f434))
* add releaseChangedProjects task to release plugin ([80ed655](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/80ed655a803216e3d9401f7398b861dc2ac916bd))
* merge build and release plugins into monorepo-build-release-plugin ([b076205](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/b0762057ebe4bbb16ea635d3998556f8638374d7))
* split releaseChangedProjects to create branches only; add CreateReleaseBranchTask ([45c8f0b](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/45c8f0b84ec1009d344c6f4d0ba2f1b1958cd58b))


### Bug Fixes

* address release plugin correctness gaps and expand test coverage ([b07d4e7](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/b07d4e7050a4aba57bc951aa565efe27839e607c))
* display root project as ': (root)' instead of ':' in print output ([5df2395](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/5df2395afe1378e053f9ca1b268e06826e38fd85))
* make TagPattern.isReleaseBranch and formatReleaseBranch respect configurable globalTagPrefix ([1e68285](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/1e68285a207a1beada4f2460860ccf1142bab19d))
* resolve post-rebase content conflicts with main ([c8f89e5](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/c8f89e5ce9ea84a35acfe6e5939c9895925565f1))
* update integration tests to use pushTag/pushBranch instead of pushTagAndBranch ([ef4cc66](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/ef4cc66607b49a852f6aa4272e118f1e53375c50))


### Documentation

* consolidate bash examples into single blocks with inline comments ([6ad6348](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/6ad63487705245d84a073ed0c723145e4246ef0b))
* fold Requirements into Apply the plugin section ([48dab6c](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/48dab6cb1110a67b24958bc9e3161f58da2916f8))
* move advanced topics out of main usage flow ([0d3ecbb](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/0d3ecbb240b84f225e8af9469b8cad3b0adc3406))
* rename Change Detection section to Builds for symmetry with Releases ([d31c1d9](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/d31c1d94bcbd0f6e6fd5b25180bb6a3dfab83f96))
* rewrite README for monorepo-build-release-plugin ([2871362](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/2871362d8869a442b74054143a89c4e30cbeb5e9))
* update all references from old plugin IDs to monorepo-build-release-plugin ([ca5e485](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/ca5e4852646ffac4de34cdde3bc01028d0ddac14))
* update internal guides to reflect merged plugin structure ([618c410](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/618c4102bc0896830f9406739fb7654e056dbf84))


### Miscellaneous Chores

* fix stale references after plugin merge and repo restructure ([a166905](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/a16690551e039022958ae172901e9293e395235e))
* update repository URLs after rename to monorepo-build-release-plugin ([ce3cd4b](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/ce3cd4b866297b00f294a948494147c072a90106))


### Code Refactoring

* extract GitCommandExecutor into shared monorepo-plugin-core module ([16b9f47](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/16b9f4757f731ad117c9d43724e6e367373645e7))
* inline monorepo-plugin-core into monorepo-build-release-plugin ([3cfaccc](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/3cfaccc2942f7aaff852c4d1f6c21d4e666f737f))
* move plugin to repo root, eliminating subproject nesting ([a143d82](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/a143d823c9530de73cfd563002eb2b54f7029c8c))
* rename releaseChangedProjectsScope to primaryBranchScope and reject non-patch scopes on release branches ([55a0c96](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/55a0c9609ebfd307af728bf9e1b0e5ab6996bc12))
* restructure packages under unified io.github.doughawley.monorepo root ([abae9cd](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/abae9cd70ad2f38772e285a6a9395f2f13c87a49))
* unify DSL under monorepo { } and monorepoProject { } extensions ([8f7b4a6](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/8f7b4a6c92c081e65d05f920141229935c290c35))


### Tests

* add integration tests for GitReleaseExecutor against a real git backend ([aae0d93](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/aae0d931b2f1c14b202e81b000766edcc5cc162a))
* add integration tests for GitTagScanner against a real git backend ([893fb99](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/893fb9939ed173cb9d8c2b8eee46150c3423e5a5))
* add unit tests for Scope, GitTagScanner, and GitReleaseExecutor ([bd55035](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/bd550351ea721e8cf6e0085c1f90eec8d58088ca))
* convert domain tests to Kotest withData tables ([7078463](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/7078463f99731fa0e5d48e8825afbe2a58c7c762))
* fill functional test gaps for monorepo-release-plugin ([39000a9](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/39000a93dcd70c3cf0ec57921d92879871a2fc5a))


### Build System

* shade monorepo-plugin-core into plugin jars via embed configuration ([d488ded](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/d488ded30e6321060b1e28abca3c84bfce265696))

## [0.3.2](https://github.com/doug-hawley/monorepo-gradle-plugins/compare/v0.3.1...v0.3.2) (2026-02-27)


### Features

* remove file list from print task output ([009937c](https://github.com/doug-hawley/monorepo-gradle-plugins/commit/009937c7d8f648525c9171f2294a611c7b233da2))


### Bug Fixes

* use component-scoped outputs for release-please monorepo ([1bfb15a](https://github.com/doug-hawley/monorepo-gradle-plugins/commit/1bfb15a3e1535fb8a0bdfed4535b95826811cac7))


### Build System

* prepare release-please for multi-plugin monorepo ([95ae196](https://github.com/doug-hawley/monorepo-gradle-plugins/commit/95ae196b59d8176ac772175ce7a0f1142f945a6b))

## [Unreleased]

## [0.2.0] - 2026-02-25

### Miscellaneous Chores

* reset version to 0.2.0 to reflect pre-release status
