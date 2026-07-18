# Feeds

Shared baby feed tracker. See SPEC.md for the full design.

Layout:

- `model/` — OpenAPI contract (source of truth for the API) and generation conventions
- `server/` — Spring Boot 3.4 / Java 21 backend (own Gradle build)
- `android/` — Kotlin / Jetpack Compose app (own Gradle build)
- `k8s/` — deployment manifests for the existing cluster (ArgoCD)

`server/` and `android/` are independent Gradle builds; each consumes `model/openapi.yaml` via the openapi-generator Gradle plugin.
