# Feeds

Shared baby feed tracker for two parents, replacing the current notes-app workflow (start / end / duration / side per feed). Uses `../meals` as the architectural reference, deviating only where recorded below.

Design decisions in this spec were resolved in a grill session on 2026-07-17. Key deviations from meals: native Kotlin/Compose client (meals is Expo/React Native), offline-first local cache, no Google offline-access grant, monorepo instead of multi-repo.

## Domain model

Entities, mirroring the meals `FamilyGroup` pattern (all data scoped to the group):

- **AppUser** — Google identity (email claim), belongs to one FamilyGroup.
- **FamilyGroup** — UUID id, members joined via invite code (see Group joining).
- **Baby** — belongs to a FamilyGroup. Name, date of birth. v1 UI assumes exactly one baby per group; the entity exists so twins or a second child are a data change, not a schema migration.
- **Feed** — the core record:
  - `id` — UUID, **generated client-side** (makes offline sync retries idempotent)
  - `baby_id` — FK to Baby
  - `type` — `BREAST | BOTTLE`. v1 UI is breast-only; the column exists to avoid a later migration if mixed feeding starts.
  - `side` — `L | R`, nullable (null for bottle)
  - `amount_ml` — nullable, bottle only
  - `start_time` — required
  - `end_time` — **nullable**: a feed with no end time is *in progress*. Tap start when the feed begins, tap end when it finishes.
  - `created_by`, `created_at`, `updated_at`

One feed is one side. A side-switch mid-session is logged as two feeds. Duration is always derived from `end_time - start_time`, never stored.

Any group member can edit or delete any feed (two-person trust circle; `created_by` is display-only).

## Android client

Native **Kotlin + Jetpack Compose**. Both phones are Android (verified). Chosen over the meals Expo stack because the quick-entry requirement leans on OS surfaces (widget, Quick Settings tile) that Expo supports poorly.

### Entry screen

Time-first, one-thumb, usable eyes-half-closed at 3am (dark theme default):

- The **editable feed time is the hero element**, not a live wall clock and not a running
  stopwatch (revised after dev testing: a parent is usually mid-feed by the time they reach
  the phone, so the value they set matters more than catching the live moment). Before a feed
  it is the **start** time; during one it is the **finish** time. Both default to **now minus
  1 minute** and track that until scrubbed. No elapsed-timer counter on the entry surface.
- One large start/finish button drives the in-progress lifecycle: START creates an in-progress
  feed at the shown start time; FINISH sets the end time to the shown finish time. In-progress
  feeds are retained (widget/tile still start/stop via the shared use case, defaulting to
  now minus 1 minute since they have no time UI).
- The **last completed feed is shown prominently** (how long ago, side, start–end range).
- Side pre-selected to the **opposite of the last feed's side**; L/R also settable by **swiping
  left/right** anywhere on the entry surface, with tap-on-toggle as the discoverable fallback.
- Time adjustment is a **horizontal scrub gesture with haptic detents** at 1-minute steps — no
  fiddly time pickers. Tap the time to type it as a fallback. Retrospective edits of any feed
  (both start and end, crossing days) live in the tap-a-feed edit sheet.
- Recent feeds visible below/behind the entry surface.

### Quick entry surfaces

- **Home-screen widget** (Glance): shows time-since-last-feed + last side; one tap starts a feed with the side prepopulated; shows elapsed time + stop when a feed is in progress. Reads the local Room cache so it works offline and without app launch.
- **Quick Settings tile**: starts/stops a feed from the pull-down shade, same intent as the widget.
- App shortcuts (long-press icon) are not planned; the widget and tile cover the use case.

### Offline-first

Feeds write to a local **Room** database instantly and sync in the background (**WorkManager** with retry). Entry never blocks on network. Client-generated UUIDs make sync idempotent; conflict resolution is last-write-wins on `updated_at` (acceptable for a two-user, append-mostly workload). History and the widget work fully offline.

### Sync / "close to realtime"

Foreground polling: refetch on app open/resume plus a ~15 second poll while the app is foregrounded. No push, no websockets, no Supabase Realtime in v1 — meals ships with less than this and for two users logging ~8–12 feeds/day polling satisfies "see it without refreshing" at near-zero complexity. An in-progress feed synced to the server means the other phone shows "feed in progress, R, 12 min". Upgrade path to FCM stays open if foreground polling ever feels slow.

### History and charts

- Chronological feed list grouped by day; tap to edit or delete (full replacement for the notes app).
- Charts, v1: **interval pattern** (gap between feeds / time-of-day view) and **duration trend** (feed minutes per day). Feeds-per-day count and L/R balance are deferred.

## Server

Mirror of the meals server: **Spring Boot 3.4, Java 21, Gradle, JPA/Hibernate**, REST/JSON under `/api`. Schema managed by `ddl-auto=update` (entities are the source of truth, as in meals — no Flyway at this scale).

### Auth

Android Credential Manager Google sign-in → client sends the Google ID token → server verifies it, upserts the AppUser, and issues its own JWTs, exactly the meals pattern **minus** the offline-access machinery: no `serverAuthCode`, no stored Google refresh tokens (those exist in meals only for Gemini/Calendar access, which feeds does not have). Refresh token lifetime set long (90 days, rotation on) so a 3am forced re-login effectively never happens.

### Group joining

Invite code, not the meals `/join/:uuid` deep link (feeds has no web client to catch the URL and App Links verification isn't worth it for a link used once). Creator generates a short code in the app; the second user signs in with Google and enters it.

The code is **stable**: `GET /familyGroup/inviteCode` returns the group's current code (minting one on first request), so viewing it never changes it. `POST /familyGroup/inviteCode` explicitly regenerates it (invalidating the old one) behind a "Regenerate" action. The code is copyable and shareable (system share sheet) from both the post-creation screen and the in-app invite dialog.

## API contract

Contract-first like meals: `model/openapi.yaml` is the source of truth; openapi-generator produces Java server DTOs and a Kotlin/Retrofit client consumed by the app (and its widget/tile code). Surface is small: auth/login, feeds CRUD (including starting/ending an in-progress feed), baby, group + invite code.

## Repo layout and deployment

Monorepo (this repo): `android/`, `server/`, `model/`, `k8s/`. Meals' multi-repo split serves publishing and deploy cadences this project doesn't have.

- Server image built with `bootBuildImage`, pushed to the existing self-hosted registry, deployed to the **existing k8s cluster via ArgoCD** (new application, manifests in `k8s/`), following the meals DEPLOY.md playbook.
- Database is a **new Supabase project** (isolation from meals; free tier is ample), connected over plain JDBC via Supabase's connection pooler — Supabase is hosted Postgres only, no Supabase SDK/auth/realtime anywhere.

## Historical data import

One-off import of the existing notes-app history: export/paste the notes text, parse it with a throwaway script (parse output eyeball-verified before load), insert via SQL or an admin endpoint. Not an in-app feature.

## Out of scope for v1

- FCM push / background realtime
- Bottle feed entry UI (schema supports it; UI deferred)
- Multiple-baby UI (schema supports it)
- Feeds-per-day and L/R balance charts, CSV export
- iOS and web clients
