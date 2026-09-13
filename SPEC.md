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
- The sheet has **two modes**, chosen by a pill in the bottom-right corner. See below.
- Charts, v1: **interval pattern** (gap between feeds / time-of-day view) and **duration trend** (feed minutes per day). Feeds-per-day count and L/R balance are deferred. Naps and a configurable window landed in v2, below.

#### History, v2: the blocks mode

The sheet draws a day in one of two ways. A pill in the bottom-right corner switches between
them, and it shows the mode it switches **to**. It toggles rather than segments because of the
widths: the centred filter pill reaches about 326dp of a 411dp screen, and a 44dp round pill inset
14dp from the right edge starts at 353dp. A two-segment pill needs about 98dp and lands on the
filter. The filter's 240dp is estimated from label metrics, not measured.

- **The compact list** is the original mode. It answers "how long, and how often".
- **The blocks mode** puts the same records on a time-of-day axis. It answers "when". The empty
  space between two blocks is the gap.

The scale is **0.5dp per minute**, so a day is 720dp. The expanded list viewport is about 737dp
on a Pixel 10, and one screen therefore holds one day. Two finer scales were measured too: 0.75dp
per minute shows about 16 hours, and 1.0dp shows about 12.

**Two lanes: feeds against the hour rail, sleep beside them.** The layout follows from what the
screen is asked for, in this order:

1. The shape of the day at a glance — a picture, one-handed, no numbers.
2. Two numbers that decide the next hour: how long she has been awake, and how long since the
   last feed.
3. Finding a record to correct, which the compact list does better anyway.

A single column served none of them, and the first build proved it on real data: nap bands ran the
full plot width, so **six of the nine interval values on screen were drawn underneath one**; the two
measures sat on two rails at the same height with nothing tying either to its event; and a feed and
a nap minutes apart collapsed into one clump. Lanes fix all three by construction. A band cannot
cover a feed's interval because it is not in that column, and each interval prints in the lane of
the thing it measures, so no second rail is needed. The lanes also show the cycle the records
actually follow — 13 Sep ran 15:23–15:34 fed, then 15:34–16:35 slept.

The split is computed from the panel's width, not hard-coded, and under a Feeds or Naps filter the
visible lane takes the whole plot: half an empty screen is not a reading, and the filter has
already said which kind is wanted. The lanes carry no headings — the block shapes and the filter's
own vocabulary identify them, and a heading would repeat on all 30 day panels.

**The clock time is the quietest text on the screen**, below the blocks in weight. The blocks carry
the reading the mode exists for; a time is what you drop to when a block is not enough.

**The block is the session, not the feed.** Over 21 days of real records, two consecutive feeds
came as close as 0.7 minutes apart, and 29 of 281 pairs fell under 15 minutes. At this scale those
blocks would overlap. Two consecutive sessions were never closer than 26 minutes, because the
20-minute session rule puts a floor under the interval. That floor is what keeps the clock labels
legible.

**A session's feeds take equal stripes down its block**, oldest at the top. 45 of 222 sessions ran
both sides, so a single-colour block would misreport a fifth of them. The stripes are equal rather
than proportional because 199 of those 222 sessions spanned under 24 minutes, which is under the
block floor — a stripe's height already cannot be a duration, so it states which sides, in which
order.

**Later runs downward, and the newest day is at the bottom.** The time-of-day chart already puts
midnight at the top. `reverseLayout` gives that order and still opens on the newest records, so
the sheet peek shows what it showed before.

**A block has a 12dp floor, and the geometry keeps the gap open.** 12dp is the largest floor at
which no session pair in the measured 221 runs into the next, and the worst pair keeps 2 minutes of
air. A 21-day sample cannot rule out a tighter pair later, so a block grows towards the floor only
as far as the next block allows and always leaves a 2dp gutter. A block is never shorter than its
true length, so a long record that a hand-edited time makes overlap still draws whole. Lengths near
the floor are not comparable, and duration comparison stays the compact list's job.

**Both intervals are written in the space they measure, and they are kept apart.** They are
different measures, so one merged number would be false:

- **The feed interval** is session start to session start — how feeding frequency is measured. It
  draws as a dotted spine down the block column with the value on the clock labels' rail, mirroring
  the compact list's spine between cards. Both modes read one shared `gapsBeforeFeed`, so they
  cannot print different numbers for the same pair.
- **The awake stretch** is nap end to next nap start. End-to-start, because a nap band already
  draws its own length and a start-to-start measure would only restate it. It draws in the sleep
  lane in the nap accent, and says "awake". A running nap has not finished, so the stretch after it
  is unknown and nothing is printed — its start cannot stand in, as that would count the nap as
  awake time.

A mark appears only where there is at least 30dp — an hour — of clear space: a short interval does
not read as empty, and the value would crowd the clock labels. Measured session intervals run 66
minutes at the lower quartile and 122 at the median, so most clear the bar.

The blank is otherwise honest, and the missing ground layer is naps, not the design. Blocks cover
18.7% of a measured day. The database holds **1 nap, of 0 minutes**, so nothing fills the rest. A
newborn's 14–16 hours of sleep would be 58–67% of the axis.

**A label slides down when it cannot clear the label above it.** A label line is 15dp and the
worst measured pair sits 13dp apart, so the nudge fires on about one pair in 221. Only the label
moves. Its block stays at its true minute, and the label states the exact clock time.

**A tap opens the session.** A 7dp block is too small to hit, so the tap target is the session and
is never under 24dp tall. A session of one feed opens the edit sheet directly. A session of
several opens the compact card first, and a row there opens its feed. 167 of 222 measured sessions
hold one feed.

**Naps stay bands behind the feed blocks**, the treatment the charts already use, and they deepen
under the Naps filter because nothing sits on top of them.

**The mode persists; the filter still does not.** Both is a filter that hides nothing, so its reset
costs nothing. A mode reset would put a parent who prefers blocks back on the list at every cold
start.

Known cost: a day is 720dp against roughly 190dp in the compact list, so 30 days is a long scroll.
That is inherent to a proportional axis, and the compact list is one tap away.

#### Charts, v2: naps and a window

- **The window is configurable**: 7d / 14d / 30d, in the app bar. 30 is the cap, and the limit is
  measured rather than chosen. The time-of-day chart gives one column per day out of about 337dp
  of plot on a 411dp screen, so 30 days leaves 11dp a column against a 5dp mark. At 60 days a
  column is 5.6dp and the marks touch. A longer window needs that chart to scroll sideways.
- **The Feeds / Both / Naps filter is the timeline's**, not a second control. `HistoryFilter`
  became `EventFilter` and moved with its pill to `ui/components/`. The pill now carries no
  position of its own; the timeline adds the inset that floats it over the sheet.
- **Naps are bands behind the feed marks.** A nap is hours where a feed is minutes, so it reads as
  ground rather than as another event on the same scale. Under Naps the trend charts swap to
  slept minutes per day and average nap length. Under Both all five show, and shortening that is
  what the filter is for.
- The series live in `ui/charts/ChartSeries.kt` as pure functions, for the reason the timeline's
  builders do: splitting an event across midnight is where a silently wrong number would live,
  and a view model taking an `AppContainer` cannot be tested here.
- Both range queries filter on `startTime` alone, so the view model queries **one day wider** than
  the window and lets the day clamp trim it. Without that an overnight nap on the leading edge
  vanished whole.

**Measured against 1,102 real feeds over 69 days**, deciding what is worth charting at all:

- The night is where the signal is. The mean gap between night feeds roughly doubled over ten
  weeks, 72 to 111 minutes, and night feeds fell from 55 a week to 33. **Still not charted.**
- If that chart is built, chart the **mean** night gap, not the longest stretch. The weekly
  maximum reads 386, 279, 266, 429 - noise that would suggest reversals that did not happen.
- Feeds per day peaked at 19.3 in late July and now sits at 13.5. Real, and still invisible.
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
