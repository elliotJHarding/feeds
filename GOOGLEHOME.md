# Google Home integration — design, status, and operations

Voice control and queries for feeds via a Google Home cloud-to-cloud ("Works with Google
Home") integration: one virtual device per baby backed by the feeds server. This document
is the record of what was built, what is proven to work, what is blocked and why, and how
to operate/diagnose it. Design rationale mirrors the session plan; the code lives in
`server/src/main/java/com/harding/feeds/googlehome/` plus voice methods in `FeedService`.

## Why this shape

Research (July 2026) showed cloud-to-cloud is the only integration path that reaches
Google/Nest speakers for a custom app. The alternatives were gated at the time: Android
AppFunctions -> Gemini was a trusted-tester private preview, and Gemini custom MCP
Connected Apps require Gemini Spark (US-only, explicitly not UK/EEA). The integration runs
permanently in the Developer Console's testing mode - one household, no certification.

## Architecture

- Device model: one `action.devices.types.WASHER` per baby (id = baby id, name
  "<Baby> feed" + nicknames), traits StartStop + Modes. WASHER because StartStop is its
  canonical trait and carries the "start/stop the feed", "is the feed running" grammar.
- EXECUTE start creates a BREAST feed at now; side = zone override ("start the feed on the
  left") or next-side alternation (opposite of latest breast feed, else L - mirrors the
  Android `ActiveEventUseCase.defaultNextSide()`; keep them in step). Stop sets `endTime`
  on the in-progress feed. Bottle feeds are deliberately app-only, and so are naps.
- A voice start also ends a running **nap**, via `InProgressEvents.endAll` - at most one event,
  feed or nap, is in progress at a time, and the Android client relies on that. The existing
  `alreadyStarted` refusal for an in-progress *feed* is unchanged. Without this the server would
  be the one place able to hold two in-progress events. Naps have no device and no mode of their
  own: a `last_nap` mode would land in the same Gemini gap recorded under Status below.
- Query state (side of last breast feed; bucketed time-since-last-feed) rides two
  query-only Modes, computed live per QUERY by `GoogleHomeDeviceStates` - the single
  source shared by QUERY responses, EXECUTE result states, and Report State.
- Report State: every feed mutation (app CRUD and voice) publishes `FeedChangedEvent`;
  `GoogleHomeStateReportListener` pushes fresh state to Home Graph
  (`devices:reportStateAndNotification`), gated on the family holding a `GoogleHomeLink`
  (so it stops after DISCONNECT). Best-effort: async, failures logged never thrown.
- Account linking: OAuth2 auth-code flow we host. The authorize page signs in with the
  standard Google button (existing web client id / `VerifyGoogleJwtService`; linking never
  creates accounts). Access tokens are the app's normal 15-minute JWTs; the refresh token
  Google holds is an opaque random secret, stored SHA-256-hashed in `google_home_link`,
  non-expiring and non-rotating as the protocol requires (deliberately NOT an
  `AppJwtToken`: cleanup/rotation there would break it). Auth codes are in-memory
  (single replica; a restart mid-link means retrying the link).
- `agentUserId` = family group UUID, so both parents linking converge on one device set.
- `/googlehome/**` is the one deliberate exception to contract-first: Google-defined
  protocol shapes, consumed by Google, hand-written controllers outside `openapi.yaml`
  (see CLAUDE.md). The three auth endpoints are on the public security chain; fulfillment
  authenticates as a normal Bearer token on the resource-server chain.

## Status

Verified working (live tests, 29 Jul 2026):
- Account linking end to end via the Google Home app `[test]` integration.
- Voice start/stop including the left/right zone override; "is the feed running".
- Report State lands in Home Graph: `devices:query` returned live
  `currentModeSettings {side, last_feed}` matching reality; zero report failures logged.
- Group scoping: foreign baby ids are `deviceNotFound` (mirrors the 404-not-403 rule).

Blocked (as of 31 Jul 2026): **Gemini does not surface custom mode state.** Asking about
side/recency, Gemini can enumerate the *available* values (proving vocabulary/routing and
SYNC attributes are fine - verified stored via HomeGraph `devices:sync`) but does not read
the *current* value, despite it being verifiably present in Home Graph. This is a gap in
Gemini's device-state reasoning for third-party query-only modes, not in this integration.

Pending diagnostics:
- Fulfillment intent logging deployed (one INFO line per intent). Next voice-test session
  should establish whether Gemini issues QUERY at all for mode questions ("is the feed
  running" is the known-good baseline that must produce a QUERY line).
- History-shaped phrasings untested: "when did <baby>'s feed last run?" - StartStop
  events from Report State may be answerable from device history even where mode state
  is not.

Options if the gap persists, in preference order: report to Google with payload evidence
(if QUERY fires and state is ignored); register for the Android AppFunctions EAP (the
designed long-term answer for phone-side queries; form linked from
https://developer.android.com/ai/appfunctions); wait for Gemini for Home to consume
`currentModeSettings` (data is already in place); pragmatic fallback - encode
minutes-since-feed in a state Gemini does read today (e.g. EnergyStorage battery
percentage), trading natural phrasing for function.

## Configuration record (set up 29 Jul 2026)

- Home Developer Console project: backed by GCP project `feeds-502719` (same project as
  sign-in - one project carries the OAuth clients, HomeGraph API, and service account).
- Cloud-to-cloud integration URLs: fulfillment `/api/googlehome/fulfillment`, auth
  `/api/googlehome/auth`, token `/api/googlehome/auth/token` on
  `https://feeds.grubplanner.co.uk`.
- OAuth client credentials we assigned to Google: id `feeds-google-home` (plain env in
  k8s/deployment.yaml), secret in cluster secret `feeds-app-secret` key
  `googlehome-client-secret`.
- Report State: HomeGraph API enabled on `feeds-502719`; service account
  `feeds-report-state@feeds-502719.iam.gserviceaccount.com`, JSON key in
  `feeds-app-secret` key `googlehome-sa-key` (raw or base64 JSON both accepted). The
  downloaded key file was destroyed after loading; mint a new key in GCP if needed again.
- `https://feeds.grubplanner.co.uk` is an authorized JavaScript origin on the existing
  web OAuth client (required by the GIS sign-in button on the link page).

## Operations

- SYNC attribute changes (names, synonyms, modes) are picked up by saying
  "Hey Google, sync my devices" - no console interaction needed.
- Ground truth of what Google stores: `tools/googlehome/hg_dump.py` (run from repo root;
  needs `./kubeconfig` and psql; pulls the SA key and DB credentials from cluster secrets
  in memory) prints Home Graph's stored device (`devices:sync`) and live state
  (`devices:query`).
- Fulfillment activity: `kubectl logs deployment/feeds` - one line per intent, plus WARNs
  from `GoogleHomeStateReporter` on report failures.
- Only one parent needs to link; devices belong to the Google Home household.
