# WebSocket cursor integrity and write-cost audit

## Baseline and scope

This audit was run against `main` at
`f6f1daf1de7014273ed2594147e9ea6fd5003d1f`, after the history-cache fix in
#88 had already landed. It follows the reconnect path:

```text
inbound frame -> event cursor persistence -> reconnect request -> replay
```

The tracking issue is
[#127](https://github.com/yurika0211/luckyagent-android/issues/127).
This document intentionally does not duplicate #123, which covers terminal
frames that fail to settle the UI/run state.

## Findings

### Cursor identity was narrower than connection identity

`LuckyAgentWsClient.ConnectionConfig` distinguishes `apiBase`, `wsUrl`,
`apiKey`, authentication mode, and `sessionId`. The reconnect path, however,
called `eventCursor(sessionId)` and `saveEventCursor(sessionId, cursor)`.
`SettingsRepository` stored one cursor per session id.

That is unsafe when the phone switches between runtime endpoints or accounts
while retaining the common `android-main` session id. A cursor observed on
runtime A could be sent as `last_message_id` to runtime B. Depending on the
server's cursor namespace, B may reject it, treat it as a later cursor, or
skip replayable events. The result is a missing delta, tool result, or final
message during reconnect.

The same collision occurs after changing the API key on one endpoint. This is
not merely a stale UI value: the cursor is the input that controls which
server events are replayed.

### Cursor persistence rewrote unrelated state for every frame

For every decoded inbound frame, the baseline decoded the entire
`sessionId -> cursor` JSON map, replaced one entry, encoded the complete map,
and called encrypted `SharedPreferences.apply()`. A long stream can contain
hundreds or thousands of chunks. With `S` sessions represented in the map,
each chunk paid `O(S)` JSON work and rewrote unrelated cursor data, in addition
to the encrypted preference write.

The map also made the cost grow as the user visited more sessions, even when
the current stream belonged to one session.

## Fix

- Derive a SHA-256 cursor namespace from the normalized API base, explicit
  WebSocket URL, API key, auth mode, and session id.
- Cache that digest on `ManagedConnection`, so frame handling does not hash
  connection details repeatedly.
- Store each cursor under its own `event_cursor_v2_<digest>` preference key.
- Do not persist raw endpoint or API-key material in the preference key.
- Treat the old session-only JSON map as non-authoritative instead of guessing
  which runtime it belonged to. This may cause one safe replay after upgrade;
  it cannot cause one account's cursor to suppress another account's events.

The per-frame storage lookup/update is now independent of the number of other
sessions. It still intentionally persists each received cursor so a process
death can resume the current stream; debouncing that write would be a separate
durability trade-off.

## Regression evidence

- `InboundFrameTest` verifies stable normalization, endpoint/account/session
  separation, and that the digest does not contain the API key.
- `WebSocketSendReadinessTest` sends an event to runtime A, switches to runtime
  B with the same session id, and verifies B's reconnect request carries an
  empty B cursor rather than A's cursor.
- The existing readiness, authentication, endpoint-switch, and reconnect tests
  remain green.

The full test and build commands for this change are:

```text
./gradlew :app:testDebugUnitTest --offline --console=plain --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m
./gradlew :app:assembleDebug --offline --console=plain --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m
```

## Adjacent risks not folded into this fix

The event flow still uses a bounded `MutableSharedFlow` with
`DROP_OLDEST`; #123 separately tracks state convergence when a terminal event
cannot be associated with a live run. Full reliable event delivery needs an
acknowledged queue/replay design, not a cursor namespace change. The present
fix ensures that whatever cursor is persisted belongs to the correct runtime
and account.
