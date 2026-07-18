# Feeds — project guide for Claude Code

Shared baby feed tracker for two parents (start/end/duration/side per feed). Read `SPEC.md`
for the full design and the rationale behind every decision — it is the authoritative record
of what was resolved in the design session and what is deliberately out of scope. This file
is the operational cheat-sheet; when the two disagree, SPEC.md wins on intent and this file
is likely stale.

## Reference architecture

The whole project mirrors the sibling `../meals` app, deviating only where SPEC.md records a
deliberate difference. When a pattern here is unclear, the meals equivalent is the reference —
but confirm against SPEC.md first, because the recorded deviations are the interesting parts
(native Kotlin/Compose client, offline-first Room cache, monorepo, invite-code join, no
Google offline-access machinery, Supabase as plain hosted Postgres over JDBC).

## Monorepo layout

Four independent parts under one repo. `server/` and `android/` are separate Gradle builds;
they do not share a settings.gradle.

- `model/` — `openapi.yaml` is the single source of truth for the API. Read `model/README.md`
  before touching it.
- `server/` — Spring Boot 3.4, Java 21, JPA/Hibernate, REST under `/api`. Root name
  `feeds-server`.
- `android/` — Kotlin + Jetpack Compose, Room, WorkManager, Glance widget, Quick Settings
  tile. Root name `feeds`, single `:app` module.
- `k8s/` — ArgoCD manifests for the existing microk8s cluster. Read `k8s/README.md`.

Java/Kotlin package is `com.harding.feeds` throughout. Android `applicationId` is also
`com.harding.feeds`.

## Contract-first workflow (the load-bearing convention)

`model/openapi.yaml` is generated into code on both sides at build time via the
`org.openapi.generator` Gradle plugin, pinned to the **same version on both sides** (the
version and full generator config are in the two build files and `model/README.md`). There is
no published contract artifact — both builds read the file by relative path
(`$rootDir/../model/openapi.yaml`).

Any API change is made in `openapi.yaml` first, then both consumers regenerate on their next
build. Never hand-edit generated code.

- Server: `spring` generator, interface-only. Generated interfaces land in
  `com.harding.feeds.api`, DTOs in `com.harding.feeds.dto`. `@RestController`s implement the
  interfaces; `skipDefaultInterface=true` makes a missing method a compile error, not a runtime 501.
- Android: `kotlin` generator, `jvm-retrofit2` library, `gson` serialization, coroutines
  (suspend functions). Client package `com.harding.feeds.client`. The generated client needs
  six libraries the generator does not add — retrofit, converter-gson, converter-scalars,
  gson, okhttp, logging-interceptor (see `model/README.md`).

Contract conventions (schema naming, uuid-vs-int64 id rule, no `duration` field ever) are in
`model/README.md` under "Contract conventions". Fast standalone spec parse check:
`python3 -c "import yaml; yaml.safe_load(open('model/openapi.yaml'))"`.

Sync gotcha (from `model/README.md`): `updatedSince` on `GET /feeds` only covers
creates/updates. Deletes are hard deletes with no tombstone, so the client's foreground poll
must refetch its visible window by `from`/`to` range to reconcile deletes — do not build a
poll that relies on `updatedSince` alone.

API surface is small — auth, feeds, babies, and family-group/invite. Read `openapi.yaml` for
the exact operations rather than relying on a list here.

## Server — build, run, test

From `server/`:

- Build: `./gradlew build` (runs `openApiGenerate` first via `compileJava.dependsOn`).
- Run locally on in-memory H2 (no Postgres needed):
  `SPRING_PROFILES_ACTIVE=localdev ./gradlew bootRun`. Serves on `:8080`, context path `/api`.
- Test: `./gradlew test`. Tests use JUnit Platform, boot the full context on H2 via
  `@SpringBootTest @AutoConfigureMockMvc @Transactional` (base class
  `support/IntegrationTest.java`), exercise the real security filter chain through MockMvc,
  and roll back after each test. Follow that base class's fixture-builder style when adding tests.

Production config (`application.properties`): `ddl-auto=update` (entities are the schema, no
Flyway); Postgres via `DB_URL`/`DB_USERNAME`/`DB_PASSWORD` env vars; Google ID-token
verification via `GOOGLE_CLIENT_ID` (used as the token audience); app issues its own JWTs
(access/refresh validities and rotation are set in `application.properties`).

## Android — build

From `android/`: `./gradlew assembleDebug` (or open in Android Studio). `preBuild` depends on
`openApiGenerate`. SDK levels and JVM target live in `android/app/build.gradle.kts`; every
dependency and plugin version is centralised in `android/gradle/libs.versions.toml` — read
those as the source of truth rather than trusting numbers restated elsewhere. The stack:
Compose, Room (via KSP), WorkManager, Glance (home-screen widget), and Credentials + googleid
for Google sign-in.

Build config comes from `android/gradle.properties`:
- `feeds.apiBaseUrl` → `BuildConfig.API_BASE_URL` (default `http://10.0.2.2:8080/api/`,
  which reaches the host machine from the emulator).
- `feeds.googleWebClientId` → `BuildConfig.GOOGLE_WEB_CLIENT_ID`.

## Deployment

Image built with `bootBuildImage` and pushed to the self-hosted registry
`grubplanner.co.uk:32000`, deployed to the existing microk8s cluster via ArgoCD. Full
playbook and placeholders-to-fill are in `k8s/README.md`. Deploy loop:
`./gradlew bootBuildImage` (needs `REGISTRY_USER`/`REGISTRY_PASSWORD`, publishes on build) then
`kubectl rollout restart deployment/feeds`.

## Working conventions

- Evidence over assumption: this codebase leans on `../meals` and on `SPEC.md`, both of which
  can be checked directly — verify a claimed pattern against the actual meals code or the spec
  rather than trusting a comment or memory.
- Follow the global instructions in `~/CLAUDE.md` (concise/descriptive prose, no numbered
  lists, no emojis, no "Generated by Claude" trailers on commits/PRs).
