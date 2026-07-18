# model - API contract

`openapi.yaml` (OpenAPI 3.0.3) is the single source of truth for the Feeds API. Both the
server and the Android app generate their API code from it at build time, consuming the file
directly by monorepo path reference - there is no published contract artifact and no
publishing step (deviation from meals, which publishes `meals_model` to GitHub Packages;
recorded in SPEC.md under repo layout).

Any contract change is made here first, then both consumers regenerate on their next build.

## Toolchain

Both builds use the same Gradle plugin:

- Plugin id: `org.openapi.generator`
- Version: `7.2.0` - the version in production use in the meals contract repo, applied here
  under Gradle 8.14 (the wrapper version used by meals_model; the plugin works on both 8.13
  and 8.14)

Pin the same version in `server/` and `android/` so both sides generate from identical
generator behaviour.

## Server generation (server/build.gradle)

Generator: `spring`, **interface-only pattern** (not the delegate pattern): the generator
emits one Java interface per tag plus the DTO models, and the server's `@RestController`
classes implement those interfaces directly. `skipDefaultInterface=true` keeps the
interfaces free of default 501 implementations so a missing method is a compile error, not
a runtime surprise.

```groovy
plugins {
    id 'org.openapi.generator' version '7.2.0'
}

openApiGenerate {
    generatorName = "spring"
    inputSpec = "$rootDir/../model/openapi.yaml"
    outputDir = layout.buildDirectory.dir("generated/openapi").get().asFile.path
    apiPackage = "com.harding.feeds.api"
    modelPackage = "com.harding.feeds.dto"
    configOptions = [
        useSpringBoot3          : "true",
        useJakartaEe            : "true",
        interfaceOnly           : "true",
        skipDefaultInterface    : "true",
        useTags                 : "true",
        dateLibrary             : "java8",
        documentationProvider   : "none",
        openApiNullable         : "false",
        hideGenerationTimestamp : "true"
    ]
}

sourceSets.main.java.srcDir layout.buildDirectory.dir("generated/openapi/src/main/java")
compileJava.dependsOn tasks.openApiGenerate
```

These configOptions match the meals `spring` generation settings verbatim, except meals
generates models only (`apis=false`) because its controllers are hand-written against a
published DTO jar; here the server also generates and implements the API interfaces, which
is what contract-first buys us in a monorepo.

## Android generation (android/app/build.gradle.kts)

Generator: `kotlin`, library **`jvm-retrofit2`**, serialization **`gson`**.

Why these picks: SPEC.md commits to a Kotlin/Retrofit client. The kotlin generator's
default serialization library is `moshi`; we explicitly pick `gson` because it pairs with
the standard `converter-gson` Retrofit converter plus generator-emitted `java.time` type
adapters - no extra serialization plugin or converter wiring, which keeps the generated
client drop-in. (`kotlinx_serialization` is more idiomatic Kotlin but needs the kotlinx
compiler plugin and a third-party Retrofit converter; not worth it for a generated-only
source set.)

```kotlin
plugins {
    id("org.openapi.generator") version "7.2.0"
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/../model/openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    packageName.set("com.harding.feeds.client")
    library.set("jvm-retrofit2")
    configOptions.set(mapOf(
        "serializationLibrary" to "gson",
        "dateLibrary" to "java8",
        "useCoroutines" to "true"
    ))
}

android {
    sourceSets["main"].kotlin.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
}

tasks.named("preBuild") { dependsOn("openApiGenerate") }
```

The generated client compiles against libraries the generator does not add for you; the
app module must declare all six:

```kotlin
dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.retrofit2:converter-scalars:2.9.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}
```

`converter-scalars` and `logging-interceptor` are easy to miss - they are imported by the
generated code itself (`ApiClient.kt`), not just by hand-written wiring.

Notes:

- `useCoroutines=true` generates suspend functions on the Retrofit interfaces, which is
  what Room/WorkManager-based sync code wants.
- `dateLibrary=java8` maps `date-time` to `java.time.OffsetDateTime`; with minSdk below 26
  enable core library desugaring in the Android build.
- The generated client is consumed by the app, the Glance widget, and the Quick Settings
  tile alike - it lives in one generated source set in the app module.

## Contract conventions (follow when editing openapi.yaml)

Carried over from the meals contract so the workflow feels familiar:

- Schema names PascalCase with `Dto` suffix for domain models, `Request`/`Response` for
  wrappers; enums plain PascalCase with UPPERCASE values.
- Property names camelCase. `operationId` camelCase verb-first, required on every
  operation. Tags TitleCase, one generated API interface per tag.
- `format: uuid` only for client-generated / shareable identifiers (Feed id, FamilyGroup
  uuid); `integer format: int64` for server-assigned DB ids (Baby, AppUser).
- Error responses are bare descriptions - no error body schema.
- No duration field on Feed, ever: duration is derived client-side from
  `endTime - startTime`.

## Sync & deletes

`updatedSince` on `GET /feeds` is an optimisation for incremental pulls of creates and
updates only. Deletes are hard deletes with no tombstone, so they are invisible to an
`updatedSince` filter. The client's 15s foreground poll must refetch its visible window
(e.g. the last 48h) by `from`/`to` range, which reconciles deletes by replacing that
window's contents. Do not build a poll that relies on `updatedSince` alone.

## Validating a spec change

The generator parses the spec on every build via `openApiGenerate`, so a broken spec fails
both builds. (The plugin's standalone `openApiValidate` task is not wired up - its
`inputSpec` is a separate extension that neither snippet above configures, so running it
as-is fails.) For a faster standalone parse check:

```
python3 -c "import yaml; yaml.safe_load(open('model/openapi.yaml'))"
```
