# Changelog

All notable changes to this project will be documented in this file.

See [Conventional Commits](https://conventionalcommits.org) for commit guidelines.

## [0.4.0](https://github.com/doug-hawley/monorepo-build-release-plugin/compare/v0.3.9...v0.4.0) (2026-03-21)


### ⚠ BREAKING CHANGES

* Tag and branch prefixes for nested projects now use slashes instead of dashes. Existing tags and branches with the old format will not be found by the plugin.
* Task names have changed. Update CI scripts and build files that reference the old names.

### Bug Fixes

* log relative path for release-version.txt instead of absolute ([aeb1e85](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/aeb1e8599aeb09f96f2c3ac1ddb43ec4fced8714))


### Documentation

* improve README readability and add CI/CD configuration guides ([cfb74fd](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/cfb74fdfc7a5a53ba813de0c7527f00095422da1))


### Miscellaneous Chores

* downgrade to Gradle 8.5 for broader consumer compatibility ([84a91e0](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/84a91e0d91ff98d990d368415cceeeee4c082c7e))


### Code Refactoring

* shorten task names and unify task group ([920368c](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/920368c6a1830c1d46bcc7531ab44895629c7c97))
* use hierarchical tag prefix preserving Gradle project path ([a4c0a31](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/a4c0a31e0173819b2d65aea73433353cf4d81805))


### Tests

* rename digital_checkouts-v1 to some_domain-v1 in test data ([975cf74](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/975cf74881146b43a90d6e4034a280d341507f98))

## [0.3.9](https://github.com/doug-hawley/monorepo-build-release-plugin/compare/v0.3.8...v0.3.9) (2026-03-19)


### Bug Fixes

* log affected projects during configuration phase instead of doLast ([#145](https://github.com/doug-hawley/monorepo-build-release-plugin/issues/145)) ([4ddac16](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/4ddac1643cdadb3d8ca924df321d3aef626e714f))


### Code Refactoring

* rename createReleaseBranches task to createReleaseBranchesForChangedProjects ([13b7077](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/13b7077108de7c6d606cc2cd08226a4ea2614912))

## [0.3.8](https://github.com/doug-hawley/monorepo-build-release-plugin/compare/v0.3.7...v0.3.8) (2026-03-14)


### Bug Fixes

* only print change detection baseline for change-detection tasks ([#139](https://github.com/doug-hawley/monorepo-build-release-plugin/issues/139)) ([454bdb9](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/454bdb99dc26932d700587b9663cbee9a6aa96ba))
* treat all projects as changed when last-successful-build tag is missing ([#136](https://github.com/doug-hawley/monorepo-build-release-plugin/issues/136)) ([ac5ddce](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/ac5ddce45792f886b3a426f87292837ec9ea3275))

## [0.3.7](https://github.com/doug-hawley/monorepo-build-release-plugin/compare/v0.3.6...v0.3.7) (2026-03-14)


### Features

* fetch lastSuccessfulBuildTag from remote before resolving ([e4d7aba](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/e4d7aba18451bec70bb907c68bd351bbf5680ebc)), closes [#129](https://github.com/doug-hawley/monorepo-build-release-plugin/issues/129)
* log change detection baseline at lifecycle level ([837b777](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/837b77719879df1c1f3974573010047a46e3ecf8))


### Bug Fixes

* address code audit findings from issue [#132](https://github.com/doug-hawley/monorepo-build-release-plugin/issues/132) ([1774961](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/1774961ef3804fbec95e7fcc1fb5cbdc5fdb4f86))
* throw on unexpected fetchTag failures instead of failing silently ([b8851ea](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/b8851eabb771734483b159943b9dcc3d7bf8a486))
* use explicit main branch in GitRepositoryTest to fix CI ([104f049](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/104f04938ca0061848919e721d1c36497bf5b7bb))

## [0.3.6](https://github.com/doug-hawley/monorepo-build-release-plugin/compare/v0.3.5...v0.3.6) (2026-03-11)


### Features

* make release task depend on build task ([7df8dbc](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/7df8dbcabed2dd36c7a84476b2f0bed4ce84b104)), closes [#111](https://github.com/doug-hawley/monorepo-build-release-plugin/issues/111)
* treat all projects as changed when no baseline tag exists ([a57100b](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/a57100b211fdaef622436dc228dac32bd7b90d94))
* use task-aware baseline resolution for change detection ([ae37e20](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/ae37e2004789332bdc235f14f7d38ec7dc057bf3)), closes [#121](https://github.com/doug-hawley/monorepo-build-release-plugin/issues/121) [#113](https://github.com/doug-hawley/monorepo-build-release-plugin/issues/113)


### Bug Fixes

* remove deprecated Task.project usage at execution time ([c9ff3b5](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/c9ff3b5ab79d23551a7e9a43482620ce686bd1ee)), closes [#110](https://github.com/doug-hawley/monorepo-build-release-plugin/issues/110)
* suppress noisy git rev-parse errors during release branch creation ([1488b35](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/1488b356f68f5cbd106604f0b45d3c795d9b402e)), closes [#109](https://github.com/doug-hawley/monorepo-build-release-plugin/issues/109)


### Performance Improvements

* speed up functional tests by reducing Gradle TestKit overhead ([c92aabf](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/c92aabfb2588cb7e59b08a71a67438f9ec2066e6)), closes [#114](https://github.com/doug-hawley/monorepo-build-release-plugin/issues/114)


### Code Refactoring

* rename buildChangedProjectsAndCreateReleaseBranches to createReleaseBranches ([5ccbed4](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/5ccbed463256c76dee879c5ebfce1e10d629f09c))


### Tests

* prove release task uses tag baseline, not origin/main ([f51c921](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/f51c92170486afd587eb9f8bed8aa57afccdd17b))
* verify subproject builds run via createReleaseBranches dependency chain ([a984024](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/a984024cd53d849be2c462a1792962c6b67ea8dd))

## [0.3.5](https://github.com/doug-hawley/monorepo-build-release-plugin/compare/v0.3.4...v0.3.5) (2026-03-08)


### Bug Fixes

* bootstrap last-successful-build tag when no projects have changed ([7f91920](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/7f919206bde2243b6009768d10739d16354bf3e4))


### Miscellaneous Chores

* remove dead diffBranch code and add edge case tests ([40046f9](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/40046f9da5934725842a3a71f7d782ecdd0d1e58))

## [0.3.4](https://github.com/doug-hawley/monorepo-build-release-plugin/compare/v0.3.3...v0.3.4) (2026-03-08)


### Features

* add atomic release branch creation task ([a5d99d4](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/a5d99d4971940ff65eca307c833508c5825d230f))
* add last-successful-build tag update mechanism ([a855600](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/a85560093a21e90c8e8bf16cf05a903ceb3a44e6))
* restrict release task to matching release branches only ([c883a80](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/c883a80de471115d4b6cc09b1f9a670146e85965))
* unify change detection to single tag-based model ([d5ed295](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/d5ed295bc5dc02c05821855b96a0a980996df68b))


### Bug Fixes

* gracefully handle initial commit when using relative parent refs ([befc917](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/befc91774a7e08de1e75b230e2701be111b18e31))
* resolve bugs, stale references, and deprecated API usage ([3bd93a0](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/3bd93a07ec5557513fc54e88e6327017e8b09f3e))
* use branch version line for initial release on release branches ([860cf50](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/860cf5014a38b9e562bdfe121b84684d186a8d20))


### Documentation

* fix stale references to removed tasks and DSL properties ([56818ea](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/56818ea3ff48efda1d5069e6d026d1cf9e90b527))
* update documentation for unified change detection model ([d04230b](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/d04230ba2456ae634b29e2df4bb1588845e58407))


### Miscellaneous Chores

* upgrade Gradle 8.12→9.4, Kotest 5.9.1→6.1.4, MockK 1.13.12→1.14.9 ([a0b6ba3](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/a0b6ba389f34d007d762c61fe3b3d316fda143b6))


### Code Refactoring

* remove unused releaseBranchPatterns property ([a1f8657](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/a1f8657acbc398aba211efb09bf584422b45eaac))
* remove writeChangedProjectsFromRef task ([67af0cd](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/67af0cd3e4e4b6c8a35ea7999fa286d4b35a8195))


### Tests

* add missing integration and functional test coverage ([0503850](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/0503850738fe99d284e05b7fdb7fb558f8863c48))


### Build System

* **deps:** Bump actions/upload-artifact from 4 to 7 ([c6bf3a2](https://github.com/doug-hawley/monorepo-build-release-plugin/commit/c6bf3a211a94a3ab47ec01ca0920cc5dd79420cf))

## [Unreleased]

### Breaking Changes

* **Unified change detection model** — replaced dual branch-mode/ref-mode with a single tag-based model anchored on `monorepo/last-successful-build`. The following DSL properties and tasks have been removed:
  * Removed `baseBranch` and `commitRef` from `monorepo { build { } }` — replaced by `lastSuccessfulBuildTag` and `primaryBranch`
  * Removed `releaseBranchPatterns` from `monorepo { release { } }` — the new aggregator task uses a `primaryBranch` guard instead
  * Removed `-Pmonorepo.commitRef` runtime override
  * Removed tasks: `printChangedProjectsFromBranch`, `printChangedProjectsFromRef`, `buildChangedProjectsFromBranch`, `buildChangedProjectsFromRef`, `writeChangedProjectsFromRef`, `createReleaseBranch` (per-subproject), `createReleaseBranchesForChangedProjects`

### Features

* add `primaryBranch` property on root `monorepo { }` extension (default: `"main"`)
* add `lastSuccessfulBuildTag` property for tag-based change detection (default: `"monorepo/last-successful-build"`)
* add unified `printChangedProjects` and `buildChangedProjects` tasks replacing 6 old tasks
* add `buildChangedProjectsAndCreateReleaseBranches` aggregator task with branch guard, atomic release branch creation, and automatic tag update
* add `AtomicReleaseBranchCreator` for two-phase branch creation with rollback on failure
* add `LastSuccessfulBuildTagUpdater` for automatic tag advancement after successful builds

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
