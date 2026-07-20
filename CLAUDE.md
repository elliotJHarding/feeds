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
- `feeds.apiBaseUrl` → `BuildConfig.API_BASE_URL` for the **debug** build (`10.0.2.2:8080` reaches
  the host from the emulator; a physical device needs the Mac's LAN IP). The **release** build
  overrides this to `https://feeds.grubplanner.co.uk/api/` in `build.gradle.kts`.
- `feeds.googleWebClientId` → `BuildConfig.GOOGLE_WEB_CLIENT_ID` (the shared Google **Web** client
  id, used as the sign-in `serverClientId` and the server's token audience — both builds).

## Local development & on-device testing

Debug and release install **side by side on one device** — debug has a distinct applicationId
(`applicationIdSuffix ".debug"` → `com.harding.feeds.debug`, label "Feeds (dev)", `versionName`
suffix `-debug`) and its own debug signing key. This is the best-practice setup for real-device UX
testing (emulator misses the true feel). Never install a debug APK over the release one under the
same id — the signing-cert clash gives a misleading "App not installed"
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); the distinct id avoids it.

Google sign-in is gated on `(package + signing-cert SHA-1)` registered as **Android OAuth clients**
in GCP project `feeds-502719` (Android clients carry no secret; the Web client id is the shared
`serverClientId`/audience). Two clients are needed:
- `com.harding.feeds` + release SHA-1 `23:B4:D9:EB:B1:4D:1B:8C:05:45:FD:A8:7D:1C:4B:E0:4D:E4:30:38`
- `com.harding.feeds.debug` + debug SHA-1 `74:3F:E3:85:F7:48:FD:10:0A:6B:F9:78:2F:05:45:DE:A5:BB:3C:86`
  (debug keystore `~/.android/debug.keystore`, alias `androiddebugkey`, storepass `android`)

Dev loop:
1. `SPRING_PROFILES_ACTIVE=localdev ./gradlew bootRun` from `server/` (in-memory H2 — keeps dev off
   the prod Supabase feed history).
2. Set `feeds.apiBaseUrl` in `android/gradle.properties` to reach it (`10.0.2.2:8080` emulator; Mac
   LAN IP for a physical device — the LAN IP drifts, so re-check it; test reachability from the
   phone, not the Mac, which can't curl its own LAN IP).
3. `./gradlew installDebug` (or Android Studio Run). The release build stays untouched.

Release signing keystore: `~/keystores/feeds/feeds-release.jks` (creds in `~/.gradle/gradle.properties`
as `FEEDS_RELEASE_*`); back it up offline — losing it means the app can never be updated.

## Deployment (server) & release (app)

Both build in **GitHub Actions**, not locally — a local Apple-Silicon build makes an arm64 image
the amd64 cluster can't run (`exec format error`), and release signing lives in CI secrets. The repo
is `elliotjharding/feeds` (public); git ops use the personal account via the `github-personal` SSH
alias (the machine's default `git@github.com` is a work account). `main` is the trunk.

- **Server**: push to `main` → `.github/workflows/build-image.yml` runs `bootBuildImage` on an amd64
  runner and pushes `grubplanner.co.uk:32000/feeds:latest`. ArgoCD (Application `feeds`, tracks
  `main`) syncs `k8s/`. To roll a new image: `kubectl rollout restart deployment/feeds -n default`
  using the cluster kubeconfig at repo root `./kubeconfig` (gitignored; API on `:16443`).
- **App release**: bump `versionName`/`versionCode`, then `git tag vX && git push origin vX` →
  `.github/workflows/release-apk.yml` builds+signs the APK and publishes it to GitHub Releases.
  Stable install link: `https://github.com/elliotjharding/feeds/releases/latest/download/feeds.apk`.

DB is the Supabase project `blxmbbclztmspsazezog`, reached via the **session pooler**
(`aws-0-eu-central-1.pooler.supabase.com:5432`, user `postgres.blxmbbclztmspsazezog`) — the direct
`db.*.supabase.co` host is IPv6-only and unreachable from the cluster. Registry TLS, secrets, and
the fuller playbook are in `k8s/README.md`.

## Working conventions

- Evidence over assumption: this codebase leans on `../meals` and on `SPEC.md`, both of which
  can be checked directly — verify a claimed pattern against the actual meals code or the spec
  rather than trusting a comment or memory.
- Follow the global instructions in `~/CLAUDE.md` (concise/descriptive prose, no numbered
  lists, no emojis, no "Generated by Claude" trailers on commits/PRs).
