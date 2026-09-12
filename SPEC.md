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

- **Nap** — the second record type, added after v1:
  - `id` — UUID, generated client-side, as for Feed
  - `baby_id` — FK to Baby
  - `start_time` — required
  - `end_time` — **nullable**: a nap with no end time is *in progress*
  - `created_by`, `created_at`, `updated_at`

  No side and no amount. A nap is a separate entity, not a third `Feed.type`, because no feed
  query carries a type filter — `findForBaby` and the Google Home state finders among them — so
  a nap sharing that table would enter all of them silently. A missed filter gives a wrong
  number in a baby tracker rather than an error.

One feed is one side. A side-switch mid-session is logged as two feeds. Duration is always derived from `end_time - start_time`, never stored.

**At most one event — feed or nap — is in progress at a time.** Starting either ends whatever
else is running, at the new event's start time, clamped so nothing ends before it began. Only a
*start* enforces this: a create that is already complete (every bottle, and any retrospective
log) ends nothing, an idempotent replay ends nothing, and an edit never does — correcting
history must not be refused. The rule lives on both sides, because the client must apply it
offline (every "what is happening now" surface reads Room directly) and the server is the only
place that sees both phones. The duplication is safe: the rule is a pure function of the new
`start_time`, which travels in the create body, so both sides compute the same end time.

Any group member can edit or delete any feed (two-person trust circle; `created_by` is display-only).

## Android client

Native **Kotlin + Jetpack Compose**. Both phones are Android (verified). Chosen over the meals Expo stack because the quick-entry requirement leans on OS surfaces (widget, Quick Settings tile) that Expo supports poorly.

### Entry screen

Time-first, one-thumb, usable eyes-half-closed at 3am (dark theme by default, see Theme below):

- The **editable feed time is the hero element**, not a live wall clock and not a running
  stopwatch (revised after dev testing: a parent is usually mid-feed by the time they reach
  the phone, so the value they set matters more than catching the live moment). Before a feed
  it is the **start** time; during one it is the **finish** time. Both default to **now** and
  track that until scrubbed. No elapsed-timer counter on the entry surface. (The earlier
  now-minus-1-minute backdate was reverted after daily use — in practice a parent hits the
  button as the feed starts, so the shown time should be the current moment.)
- One large start/finish button drives the in-progress lifecycle: START creates an in-progress
  feed at the shown start time; FINISH sets the end time to the shown finish time. In-progress
  feeds are retained (widget/tile still start/stop via the shared use case, defaulting to now
  since they have no time UI).
- The status card **always shows two blocks**: whatever is running (or the last feed) above, and
  the other kind's last completed record below. "How long since the last feed" and "how long
  since the last nap" are separate concerns — due to feed, versus overtired — and a parent needs
  both at once at 3am without changing mode. The feed block keeps the prominent reading (how long
  ago, side, start–end range) and the side stays welded to it; a nap has no side. Deliberate
  trade against the hero-time rule below: the card is about 96dp taller, and the hero clock gives
  up that space. Two columns were ruled out by width — the card's inner width is about 280dp, and
  "2h 10m ago · L" needs about 200dp at headlineMedium.
- The nap block is anchored on the nap's **end**, not its start: the useful number is how long
  the baby has been awake. Feeds stay start-to-start, which is how feeding frequency works.
- Side pre-selected to the **opposite of the last feed's side**; L/R also settable by **swiping
  left/right** anywhere on the entry surface, with tap-on-toggle as the discoverable fallback.
- Time adjustment is a **horizontal scrub gesture with haptic detents** at 1-minute steps — no
  fiddly time pickers. Tap the time to type it as a fallback. Retrospective edits of any feed
  (both start and end, crossing days) live in the tap-a-feed edit sheet.
- Recent feeds visible below/behind the entry surface.

### Quick entry surfaces

- **Home-screen widget** (Glance): shows the last feed's clock time + last side; one tap starts a feed with the side prepopulated; when a feed is in progress it shows the start clock time + stop. The glance-value is an absolute clock time (not a relative "2h ago"/"12m" duration) so it never goes stale between the widget's infrequent background refreshes. Reads the local Room cache so it works offline and without app launch.
- The widget's default size is unchanged at 2x1, where the running event owns the single
  glance-value and the chip ends it. Two chips plus the reading need roughly 190dp and 2 cells
  give about 110–140dp, so **starting** a nap from the widget appears only when the user stretches
  it to 3 cells or more.
- **Quick Settings tile**: starts a feed from the pull-down shade and stops whatever is running,
  naps included. Its label always names which of the two a tap will do.
- App shortcuts (long-press icon) are not planned; the widget and tile cover the use case.

### Offline-first

Feeds write to a local **Room** database instantly and sync in the background (**WorkManager** with retry). Entry never blocks on network. Client-generated UUIDs make sync idempotent; conflict resolution is last-write-wins on `updated_at` (acceptable for a two-user, append-mostly workload). History and the widget work fully offline.

### Sync / "close to realtime"

Foreground polling: refetch on app open/resume plus a ~15 second poll while the app is foregrounded. No push, no websockets, no Supabase Realtime in v1 — meals ships with less than this and for two users logging ~8–12 feeds/day polling satisfies "see it without refreshing" at near-zero complexity. An in-progress feed synced to the server means the other phone shows "feed in progress, R, 12 min". Upgrade path to FCM stays open if foreground polling ever feels slow.

### History and charts

- Chronological list grouped by day; tap to edit or delete (full replacement for the notes app).
- **Feeds and naps share one timeline**, in time order. Feeds group into sessions; a nap stands
  alone, because only one event runs at a time so a nap is never part of a feeding session. A nap
  draws no duration bar — the feed bar cap is 20 minutes, so every nap would clamp to full width
  and a 35-minute nap would look like a three-hour one. Naps use the dot row the list already
  reserves for hours-scale magnitudes.
- Session gaps stay feed-only and a nap never replaces one: the gap measures feeding frequency,
  and whether the baby slept through it does not change that number.
- A sticky **Feeds / Both / Naps** filter sits at the sheet's bottom edge, in the thumb arc when
  the sheet is expanded. The day header drops terms it can no longer count under a filter.
- Charts, v1: **interval pattern** (gap between feeds / time-of-day view) and **duration trend** (feed minutes per day). Feeds-per-day count and L/R balance are deferred.

#### Charts, agreed direction (not built)

Two changes come before any new chart. Both exist to stop the time-of-day chart becoming
unreadable once naps are on it.

- **The window becomes configurable.** It is fixed at 14 days (`ChartsViewModel.WINDOW_DAYS`).
  There are 69 days of real data, so the charts show under a quarter of it, which is too short to
  see any trend.
- **The Feeds / Both / Naps filter is reused on the charts screen.** `HistoryFilter` is a bare
  enum with no dependencies, and `HistoryFilterPill` already takes only `selected`, `onSelect` and
  `modifier`. It is private to `HomeScreen.kt`, so reuse means moving one composable to
  `ui/components/`. Nothing else has to change.

Only then is naps-on-the-time-of-day-chart worth building, and it needs weeks of nap data first.
There was exactly **one** nap row on 12 Sep 2026, the day naps shipped.

**Measured against 1,102 real feeds over 69 days**, to decide what a chart would actually say:

- The night is where the signal is. The mean gap between night feeds roughly doubled over ten
  weeks, 72 to 111 minutes, and night feeds fell from 55 a week to 33. Nothing in the app shows
  this.
- Chart the **mean** night gap, not the longest stretch. The weekly maximum reads 386, 279, 266,
  429 - noise that would suggest reversals that did not happen. The mean does not.
- Feeds per day peaked at 19.3 in late July and now sits at 13.5. Real, and currently invisible.
- **L/R balance stays out of scope, and the data is the reason.** Weekly counts run 50/54, 58/57,
  65/64, 51/51, 43/44. The chart would report "even" forever.

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

### Theme

A parent picks the app's colours on a theme screen, reached from a third icon in the home top
bar. Nine presets, six dark and three light, plus one hand-made theme.

- A theme is **seven colours**: the background, the chrome accent, the four event accents (L, R,
  bottle, nap) and the stop colour. A preset and a hand-made theme are the same type, so one code
  path serves both.
- **Text is never picked.** The app derives every foreground by WCAG contrast ratio from the
  ground it sits on, and picks whichever of the theme's two tones reads better. This is what
  makes free choice safe: no six colours a parent picks can hide the words. It also removed a
  latent defect — the old single fixed near-black glyph colour held only because every accent the
  app shipped was light.
- **The default is unchanged.** Candlelight holds exactly the colours the app shipped with, and
  the derivation is calibrated against the hand-tuned scheme it replaces. Measured over the 15
  Material slots the app reads, 7 land exactly and the worst drifts by 5/255.
- **The theme stays on one device**, in `SharedPreferences`. It is a display preference, not
  family data, so the two parents can differ. No contract change, no server field, nothing syncs.
- The **dark-only commitment is revised, not dropped.** The nocturnal default stands, and the
  reason still holds: warm dark light preserves night vision at 3am. A parent who reads the
  history in daylight now has an answer as well.
- The widget follows the theme through the same refresh hook a new feed uses. The Quick Settings
  tile carries no colour and is unaffected.
- A light theme forced a fix: the app draws edge to edge and nothing set the system-bar
  appearance, so white status-bar icons would vanish on a light ground.
- The editor **warns and does not refuse.** Two event accents that converge in both hue and
  lightness raise a line of text. Telling a nap row from a side row at 3am is what those colours
  are for, but the choice stays the parent's.

## Out of scope for v1

- FCM push / background realtime
- Multiple-baby UI (schema supports it)
- Feeds-per-day and L/R balance charts, CSV export
- Nap charts — the charts screen stays feed-only
- **Google Home voice for naps.** `GOOGLEHOME.md` records that Gemini does not surface custom
  mode state, so a `last_nap` mode would land in that same gap. The integration already has the
  precedent that a type can stay app-only: bottles are deliberately app-only too. Voice start and
  stop still end a running nap, so the server cannot contradict the one-event rule.
- **A shared theme.** The theme is per device by decision, not by omission. Making it family-wide
  would need a contract change, a server field and sync, and it would let one parent's choice
  override the other's with no way to differ.
- **More than one saved custom theme.** One editable slot. A second would need a naming and
  management UI for a preference a parent sets and forgets.
- iOS and web clients

Shipped since v1, so no longer out of scope: bottle feed entry UI (commit `8d77b7b`).
