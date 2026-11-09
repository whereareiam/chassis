# AGENTS.md

## Chassis At A Glance

Chassis is a **general extension library for JVM hosts**, such as Minecraft plugins and mods. A host names its own kind of extension (providers, modules), the folder their jars lie in and the descriptor file each jar carries. Chassis discovers the jars, reads their YAML descriptors, resolves dependencies, loads each extension in its own class loader over the host's **export layer**, starts and stops them in dependency order, removes everything an extension registered when it stops, keeps administrators' choices in a selection file, and offers a typed service registry and a runtime control API.

When changing behavior:

- keep Chassis host-agnostic: a host's own idea of an extension, its context methods and its offers belong to the host, not to Chassis
- keep what extensions see limited to the JDK and what the host exports
- never leave a reference to a stopped extension's classes behind; a stopped extension's class loader must be collectable

---

## Repository Orientation

### Module Map

- `chassis-api`
  The contracts hosts and extensions compile against: `Extension`, `ExtensionContext`, `ExtensionFactory`, `ExtensionPreparation`, `Extensions`, `Services` with `ServiceKey` and `ServiceRegistration`, `Platform`, `ExtensionDescriptor`, `InstalledExtension`, `Version`, `VersionRange`, `ExtensionState`, events and exceptions.
- `chassis-common`
  The standard host. `ChassisHost` owns every installed extension and implements `Extensions`; `HostOptions` is what a host tells it; `SelectionFile` keeps turned-off ids. Packages: `discovery` (`ExtensionDiscovery`, `DescriptorReader`), `resolution` (`DependencyResolver`), `loading` (`ExportLayer`, `ExtensionClassLoader`), `lifecycle` (`LoadedContext`), `service` (`ServiceRegistry`).
- `chassis-adapter-guice`
  `GuiceExtensionFactory`: an injector of its own per extension, never a child injector, because Guice puts a child's just-in-time bindings into the parent and the host would keep the extension's class loader. `GuiceExtensionFactoryTest` proves it. Guice is `compileOnly`.
- `chassis-adapter-attache`
  `AttacheLibraries`: an `ExtensionPreparation` that reads the Attache descriptor inside an extension's jar and loads its libraries into the extension's own loader through `ExtensionLibraryManager`, an Attache manager per load that Chassis closes when the extension stops. `attache-launcher` is `compileOnly`.
- `chassis-adapter-commandant`
  `ExtensionCommands`: `/<root> <kind> list|info|enable|disable|reload|rescan` on Cloud, each with the permission `<root>.command.<kind>.<name>` the host checks and Commandant's definition id `<kind>-<name>`; each answer a translatable component under the host's key prefix with named arguments, worded by the host's translations. Cloud, Commandant's API and Adventure are `compileOnly`.
- `chassis-platform/type/platform-neoforge` (project `:platform-neoforge`)
  `NeoForgePlatform`: the name `neoforge` and loaded mods for descriptors' `plugins`. NeoForge's loader is `compileOnly`.
- `chassis-testing`
  `ExtensionJar` (writes extension jars from test classes) and `LeakCheck` (tells whether a stopped extension's class loader was collected), for hosts' tests and Chassis's own.
- `build-logic/`
  Shared Gradle conventions. `module-java` (group `me.whereareiam.chassis`, Java 21 toolchain, Lombok, JetBrains annotations, JUnit, sources and Javadoc jars), `module-api` (`module-java` plus publication, for `chassis-api`), `packaging-publication` (Maven publication). A published module that is not the API applies `module-java` and `packaging-publication`.
- `docs/content/`
  Documentation for hosts and contributors.

Not in the repository yet: platforms for Bukkit, Velocity and BungeeCord. Do not document them as existing.

### Where To Look First

- For contracts and public models, start in `chassis-api`.
- For discovery, resolution, loading, lifecycle, selection or services, start in the matching package of `chassis-common`; for the order of operations, start in `ChassisHost`.
- For the commands of a host's extensions, start in `chassis-adapter-commandant`. For libraries extensions declare, start in `chassis-adapter-attache`. For Guice, start in `chassis-adapter-guice`; for NeoForge, in `chassis-platform/type/platform-neoforge`.
- For test helpers, start in `chassis-testing`.
- For build behavior or publication, check `build-logic/` and `gradle/libs.versions.toml`.
- For changes hosts or extensions notice, check the matching pages under `docs/content/developers/`.

---

## Architecture Rules

### Module Boundaries

- Keep responsibilities in the narrowest owning module.
- Contracts go into `chassis-api`, behavior into `chassis-common`.
- Adapters and platforms depend on `chassis-api` only and keep their third-party library `compileOnly`, so the host chooses its version.
- `DependencyResolver` stays free of side effects; `ChassisHost` makes every change, under one lock, on the caller's thread.

### API Surfaces

- `chassis-api` is the public API surface, together with the public types of `chassis-common`, `chassis-adapter-guice`, `chassis-adapter-attache`, `chassis-adapter-commandant`, `platform-neoforge` and `chassis-testing` that hosts use (`ExtensionCommands`, `ChassisHost`, `HostOptions`, `ExportLayer`, `SelectionFile`, `GuiceExtensionFactory`, `AttacheLibraries`, `NeoForgePlatform`, `ExtensionJar`, `LeakCheck`).
- Public API changes are compatibility-sensitive.
- Public API code must have complete Javadocs for public classes, interfaces, enums, and public methods.

### Class Loading And Cleanup

- An extension's loader looks in the export layer first, then its own jar and the jars the host's preparation added, then the packages its dependencies export (`exports` in their descriptors). Keep that order: an extension must not replace a class the host exports, and must see of a dependency only what it exports.
- Everything an extension registers goes through its context, so the host can remove it. A new place that holds extension objects removes them in the stop path and gets a `LeakCheck` test.
- A failed `enable` cleans up exactly like a stop.

### Existing Patterns First

- Follow neighboring naming, packaging, and builder patterns before inventing new ones.
- Reason texts in `getWaitingFor()` and failure messages are read by administrators; keep their wording consistent with the existing ones.

---

## Dependencies

- Use Java 21-compatible code and APIs.
- Follow the version catalog in `gradle/libs.versions.toml` when adding or changing dependencies.
- Add dependencies in the narrowest owning module. `chassis-api` has no runtime dependencies; `chassis-common` uses Configura for YAML.
- Prefer `compileOnly` for libraries a host already ships (Guice, platform APIs).
- When a new dependency is required, check the latest available version instead of relying on memory.

---

## Build, Test, And Validation

### Common Commands

- `./gradlew test`
- `./gradlew test build` (what the pull request checks run)
- `./gradlew :chassis-common:test --tests 'me.whereareiam.chassis.common.ChassisHostTest'`
- `VERSION=<version> ./gradlew publishToMavenLocal` (to try a build in a host)

The version comes from the `VERSION` environment variable and defaults to `dev`.

`build.gradle.kts` activates no git hooks; Chassis has no translations to check, so there is no `.githooks/`.

### Verification Guidance

- Prefer targeted Gradle tasks for the module you changed before running broad root verification.
- Add or update automated tests when behavior changes, in the closest existing test: `VersionRangeTest`, `DescriptorReaderTest`, `ChassisHostTest`, `ServiceRegistryTest`, `GuiceExtensionFactoryTest`, `AttacheLibrariesTest`.
- Host tests write extension jars with `ExtensionJar`; the extensions live in `org.example.<name>` under the test sources, outside every exported package.
- A change that could keep an extension in memory gets a `LeakCheck` assertion.

See `docs/content/contributing/testing/`.

---

## Branching And Pull Requests

### Branch Flow

- Build each feature, fix, or isolated piece of work in its own `feature/...` branch.
- Merge feature work into `dev` through a pull request. Pull requests are merged by rebase: each commit lands on `dev` as written, so a branch holds clean conventional commits and no fixups.
- Releases are cut from `dev`: publishing the release draft tags the current `dev`.

### Pull Request Checks

- Every pull request gets the metadata check (title and label) and the checks: `./gradlew test build`.
- The `dev` ruleset requires these checks, so no pull request merges with a failing or missing check unless an administrator bypasses it.
- Dependabot groups minor and patch updates into one pull request per ecosystem, and `dependabot-auto-merge.yml` turns on auto-merge for it, so it merges once the checks pass; a major update gets a pull request of its own and waits for review. The merge goes through a GitHub App, because a merge by `GITHUB_TOKEN` would not start the workflows of the push to `dev`; its `MERGE_APP_CLIENT_ID` and `MERGE_APP_PRIVATE_KEY` are Dependabot secrets, since a run Dependabot starts gets no Actions secrets. When the checks gain or rename a job, update the required checks of the ruleset.
- Label a pull request with exactly one of `feature`, `change`, `bug`, `dependencies`, or with `skip-changelog`; `major` may accompany a category.
- A run keeps `test-results` as an artifact when a test failed.
- Development builds are started manually and publish a branch-qualified version. A published GitHub release publishes the Maven artifacts.
- Entry workflows only select a trigger and call `reusable-verify.yml` and `reusable-publish.yml`; shared steps come from `whereareiam/devops` and `whereareiam/blockops`.
- The Gradle build cache is on. Pull requests only read caches, so `build-cache.yml` compiles the project and its tests on every push to `dev` and writes the Gradle cache; when the checks start running other tasks before their tests, add those tasks to its command.

### Issues

- A suggestion opens with `suggestion`, the label of an idea not decided on yet. An accepted one trades it for `feature` or `change`; a declined one is closed as not planned.
- A bug report opens with `bug`.

### Release Notes

A merged pull request lands in the release draft as `* <title> ([#<number>](<url>))`, under the category of its label. The draft is written for the developers of hosts and extensions, so the title and the label decide what they read.

#### Titles

- use the strict format `Area: Description`, with a past-tense description of what changed for the reader: `Introduced`, `Changed`, `Fixed`, `Removed`
- describe the effect, not the implementation: `Lifecycle: Fixed dependents staying stopped after their dependency was reloaded`, not `Lifecycle: Marked dependents as candidates in reload`
- the draft sorts by the exact area, so spell an area exactly as listed below

Areas for behavior hosts and extensions notice:

`Discovery`, `Resolution`, `Loading`, `Lifecycle`, `Selection`, `Services`, `Guice`, `Attache`, `Commands`, `NeoForge`, `Testing`

- reuse an existing area whenever one fits
- add a new area only for something readers recognise by that name, such as a new adapter or platform, and add it to this list, to the issue template's areas and to the pull request template in the same pull request
- never use class names or internal packages as an area: no `ChassisHost`, `Common`, `Registry`

`API:` is for changes to the contracts hosts and extensions compile against: types, methods and their meaning in `chassis-api` and the public types of the other modules. The drafter moves `API:` changes out of their category into a closing "For developers" section. A change that is both behavior and API takes the behavior area; mention the API part in the pull request body.

#### Labels

| Label | Use for | Draft section |
| --- | --- | --- |
| `feature` | something new for hosts or extensions | What’s new |
| `change` | changed behavior or API | Enhancements |
| `bug` | a fix for a bug that shipped in a release | Fixes |
| `dependencies` | a library update | Dependencies |
| `skip-changelog` | anything a reader of the release would not notice | not listed |

Use `skip-changelog` for:

- internal refactors with no effect on hosts, extensions, or the public API
- build, CI, test, and documentation-only changes
- a fix for a bug that never shipped: when the broken code was introduced after the last release, the fix belongs to the work that introduced it, not to the release notes; compare against the last release tag (`git grep <symbol> <tag>`, `git log <tag>..HEAD -- <path>`) to decide
- changes to something that was itself added after the last release and never shipped

Add `major` next to the category when hosts or extensions must change on upgrade: an incompatible public API change, a descriptor field that changed meaning, or a selection file that is no longer read. One `major` pull request makes the draft a major version.

#### Release notes file

The drafter rebuilds the draft on every push to `dev`, so text typed into the draft is lost. Everything written by hand, such as the subtitle, the summary, upgrade notes, and security callouts, lives in `.github/release-notes.md` and is changed through pull requests like any other file.

```markdown
---
version: 0.2.0
---

# Update 0.2.0 - Shared Libraries

One or two sentences about the release.

## Upgrading

* What a host or an extension has to change.
```

- the content replaces the subtitle and summary placeholders of the draft only while `version` matches the version the draft resolves to; notes of a published release therefore never reach the next draft
- when a change needs a note, add it to this file in the same pull request; if the file names an already published version, start the notes of the next release by replacing the whole file and setting `version` to the version the draft shows
- keep the heading as `# Update <version> - <subtitle>`
- bullets in this file are not sorted into the changelog

#### Publishing a release

- fix the changelog part of the draft by editing titles and labels of merged pull requests, then rerun the `Release drafter` workflow
- the subtitle and summary come from `.github/release-notes.md`; the release workflow stops while a placeholder is left
- the summary names what hosts and extensions must change on upgrade (everything labelled `major`) and calls out security fixes
- publishing the release verifies the tagged commit and publishes the Maven artifacts to `https://registry.whereareiam.me/maven/packages`; nothing is attached to the release and nothing goes to plugin stores

---

## Documentation Rules

### Structure

`docs/content/` is split into two sections, one per reader, each a root folder (`"root": true` in its `meta.json`) that Scriptorium shows as its own sidebar: `developers/` (Hosting extensions) and `contributing/`. The docs open on the first page of the first section, `developers/index.md`. `scriptorium.project.json` configures the site; there is no published site yet. Put new text where its reader looks for it:

- **Hosting extensions** (`developers/`): setting up a host, writing extensions for it, descriptors, what extensions see, lifecycle, selection, services, the control API, Guice, platforms, and testing a host.
- **Contributing** (`contributing/`): working on Chassis itself: building, modules and internal flow, and where tests go.

Rules that keep the sections apart:

- Internal classes of `chassis-common` that hosts never call (`ExtensionDiscovery`, `DependencyResolver`, `LoadedContext`, `ServiceRegistry`, `ExtensionClassLoader`) belong in Contributing, not in the host guides.
- A folder exists only for two or more pages.
- Each page is `<name>/index.md`, and each folder with several pages has a `meta.json` that orders them. Only the two section folders carry an `icon`; no other folder or page gets one. Group separators (`---Getting Started---`) live in the `meta.json` of their section.
- Links between pages are relative and point at the page file, such as `../services/index.md`.

### Content

- When changing behavior hosts or extensions notice, update the relevant documentation in the same work.
- Documentation in `docs/content/` describes the **current state** of the library.
- Do not write historical “used to” documentation unless the task explicitly calls for migration notes or release notes.
- Every code example uses real symbols of the current source; check it against the code.
- Do not document planned modules as existing.

---

## Working Style

- Match the conventions already established in the surrounding module.
- Prefer small, scoped changes over broad cleanup unless cleanup is part of the task.
- If a change touches class loading, the order of starting and stopping, or what is removed when an extension stops, be especially careful: a regression there leaks class loaders or lets an extension see classes it should not.
