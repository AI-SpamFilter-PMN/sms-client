# SpamGuard SMS Client

A self-contained Java SE web application: it starts its own embedded Tomcat
(no external app server needed), serves a login-protected web UI, and lets a
user compose and send SMS from any phone number they own. Every message is
submitted over **SMPP** to a separate, teammate-owned SMPP server, which
classifies it (spam/ham), forwards it to Osmocom or blocks it, and records
the result in a shared **Neon (Postgres)** database. This app reads that
database back to show message history — it never writes to the
classification tables itself.

This is one component of a larger private-mobile-network spam-filtering
project. The other pieces (Osmocom core network, the AI classifier, the SMPP
server, a voice/SIP client, and a possible admin client) are separate,
independently owned codebases that this app integrates with only over SMPP
and a shared database — never by direct code dependency.

## Contents

- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Getting started](#getting-started)
- [Configuration reference](#configuration-reference)
- [Web UI](#web-ui)
- [REST API](#rest-api)
- [Database](#database)
- [Authentication & security model](#authentication--security-model)
- [Known gaps / things to confirm with the team](#known-gaps--things-to-confirm-with-the-team)
- [Troubleshooting](#troubleshooting)

## Architecture

```
 Browser                 This app (sms-client)              Teammate's SMPP server        Neon (Postgres)
 ───────                 ──────────────────────              ──────────────────────        ───────────────
   │  HTML pages / fetch()      │                                     │                            │
   ├────────────────────────────▶  Servlets (embedded Tomcat)         │                            │
   │                             │   - auth, numbers, send, history   │                            │
   │                             │                                     │                            │
   │                             │──── SMPP submit_sm ────────────────▶  classifies (spam/ham),     │
   │                             │◀─── submit_sm_resp (msg id) ────────  forwards to Osmocom or      │
   │                             │                                     │  blocks, then WRITES a row  │
   │                             │                                     ├───────────────────────────▶│ messages
   │                             │                                     │                            │
   │                             │──── SELECT (read-only) ────────────────────────────────────────▶│ messages,
   │                             │◀─── rows (this user's own numbers) ─────────────────────────────│ users,
   │                             │                                     │                            │ phone_numbers
```

Two separate concerns, two separate protocols:

1. **Sending** — this app is an SMPP client (an "ESME" in SMPP terminology). It binds to the SMPP server as a `TRANSCEIVER` at startup and keeps retrying every 15s if the bind fails, so it never crashes just because the SMPP server isn't up yet.
2. **Reading history** — this app is a plain read-only Postgres client. It queries Neon directly for any `messages` row whose `source` or `destination` matches one of the logged-in user's own registered numbers. It **never** writes to `messages` — that's entirely the SMPP server's responsibility, including the actual spam/ham classification.

Because of that split, a successful "message sent" response only means the
SMPP handoff succeeded — not that the SMPP server has finished classifying
and writing it to Neon yet. History can legitimately lag behind, or (if the
SMPP server has a bug) never show up at all. See
[Known gaps](#known-gaps--things-to-confirm-with-the-team).

## Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 17, plain Java SE (no Spring) |
| HTTP server | Embedded Apache Tomcat (`tomcat-embed-core`) — the whole app is one runnable jar |
| Web layer | Raw `HttpServlet`s, server-rendered HTML (Java text blocks), vanilla JS (`fetch`) for the two JSON-backed widgets — no frontend framework, no build step |
| SMPP client | [jSMPP](https://github.com/opentelecoms-org/jsmpp) |
| Database access | Plain JDBC + [HikariCP](https://github.com/brettwooldridge/HikariCP) connection pool, driver `org.postgresql` |
| Database | Neon (serverless Postgres) |
| JSON | Jackson (`jackson-databind`) |
| Logging | SLF4J + `slf4j-simple` |
| Build | Maven, `maven-shade-plugin` producing a single runnable `sms-client.jar` |

## Project structure

```
sms-client/
├── pom.xml
├── db/
│   └── schema.sql                  # full Neon schema (see Database section)
├── src/main/resources/
│   ├── application.properties      # defaults; secrets are NOT stored here
│   └── simplelogger.properties
└── src/main/java/com/spamfilter/smsclient/
    ├── Main.java                   # entry point: wires everything, starts Tomcat
    ├── config/
    │   └── AppConfig.java          # reads application.properties, system props, env vars
    ├── auth/
    │   └── SessionUtil.java        # reads/writes the logged-in user on HttpSession
    ├── db/
    │   ├── Database.java           # builds the pooled Neon DataSource
    │   ├── UserRepository.java     # accounts, phone numbers, message history (this app's data)
    │   └── SubscriberRepository.java  # network subscriber CRUD - NOT currently used anywhere (see Known gaps)
    ├── model/
    │   ├── User.java, PhoneNumber.java        # this app's own accounts
    │   ├── SmsMessage.java                    # in-flight message being submitted
    │   ├── MessageRecord.java                 # a row read back from Neon's `messages` table
    │   └── Subscriber.java                    # network subscriber - NOT currently used anywhere
    ├── smpp/
    │   └── SmppService.java        # SMPP transceiver session: bind/retry/submit
    └── servlet/
        ├── WebPage.java            # shared HTML shell, CSS design system, login guard
        ├── IndexServlet.java       # GET /            - send form + recent messages
        ├── HistoryServlet.java     # GET /history      - full detailed history page
        ├── NumbersServlet.java     # GET/POST /numbers - manage owned phone numbers
        ├── RegisterServlet.java    # GET/POST /register
        ├── LoginServlet.java       # GET/POST /login
        ├── LogoutServlet.java      # GET /logout
        ├── SendSmsServlet.java     # POST /api/sms/send
        ├── MessageHistoryServlet.java  # GET /api/sms/history
        └── HealthServlet.java      # GET /api/health
```

## Getting started

### Prerequisites

- JDK 17+
- Maven 3.6+
- A Neon connection string (ask the team — required for accounts/login/history; the app still starts without it, but auth features report "unavailable")

### Build

```bash
cd sms-client
mvn clean package
```
Produces `target/sms-client.jar` (a fat/shaded jar — no external dependencies needed to run it).

### Configure & run

```bash
export DB_URL='postgresql://<user>:<password>@<host>/<db>?sslmode=require&channel_binding=require'
java -jar target/sms-client.jar
```

`DB_URL` must be set as an environment variable (or `-Ddb.url=...`) — **never** commit it into `application.properties`. See [Configuration reference](#configuration-reference) for every other overridable key (SMPP host/port, server port, etc.).

On startup you should see something like:
```
Connecting to Neon at <host>/<db>
HikariPool-1 - Start completed.
SMPP bind to 127.0.0.1:2076 failed (Connection refused); retrying in 15s
SMS client listening on http://localhost:8080
```
The SMPP warning is expected whenever the teammate's SMPP server isn't running — it retries automatically and doesn't block the rest of the app.

### First run walkthrough

1. Open `http://localhost:8080/` → redirected to `/login` (no session yet).
2. Register an account → auto-logged-in, redirected to `/`.
3. `/numbers` → add a phone number you want to send from.
4. Back on `/` → that number now appears in the **Source** dropdown.
5. Send a message → if the SMPP server isn't running, expect a clean error banner (`SMPP session is not bound to the SMPP server`), not a crash.
6. `/history` → full history for your numbers, read from Neon.

### Stopping

`Ctrl+C` (graceful — unbinds SMPP and stops Tomcat via a shutdown hook), or `pkill -f "sms-client.jar"` if backgrounded. Kill any stray previous instance before starting a new one — two processes racing for port 8080 has caused confusing "stale server" symptoms during development.

## Configuration reference

Every key in `application.properties` can be overridden by a `-D<key>=<value>` system property **or** an environment variable (dots → underscores, uppercased — e.g. `db.url` → `DB_URL`). System property wins over env var, which wins over the file default. See `AppConfig.java`.

| Key | Env var | Default | Meaning |
|---|---|---|---|
| `server.port` | `SERVER_PORT` | `8080` | Embedded Tomcat's HTTP port |
| `smpp.enabled` | `SMPP_ENABLED` | `true` | If `false`, never attempts to bind SMPP at all |
| `smpp.host` | `SMPP_HOST` | `127.0.0.1` | SMPP server host |
| `smpp.port` | `SMPP_PORT` | `2076` | SMPP server port |
| `smpp.systemId` | `SMPP_SYSTEMID` | `smsclient` | SMPP bind `system_id` |
| `smpp.password` | `SMPP_PASSWORD` | `password` | SMPP bind password |
| `smpp.systemType` | `SMPP_SYSTEMTYPE` | *(empty)* | SMPP bind `system_type` |
| `smpp.transactionTimerMillis` | `SMPP_TRANSACTIONTIMERMILLIS` | `10000` | How long to wait for a `submit_sm_resp` before timing out. jSMPP's own default is 2s, which is too short once the SMPP server does classification/routing before acknowledging |
| `db.url` | `DB_URL` | *(empty — must be set)* | Neon connection string, `postgresql://user:pass@host/db?sslmode=require...`. **Never put a real value in the properties file** |

## Web UI

All pages except `/login` and `/register` require a session (`WebPage.requireLogin` redirects to `/login` otherwise). Visual design: a dark, glassmorphic "Signal Guard" theme — gradient accents, glowing spam/ham badges — implemented as one shared inline CSS block in `WebPage.java` (no external stylesheet, no build step).

| Route | Method | Auth | Purpose |
|---|---|---|---|
| `/register` | GET/POST | — | Create an account (email, password, optional display name) |
| `/login` | GET/POST | — | Authenticate |
| `/logout` | GET | session | Invalidate session, redirect to `/login` |
| `/` | GET | session | Send-SMS form (source is a dropdown of *your* numbers, not free text) + last 5 messages |
| `/history` | GET | session | Full history (up to 1000 rows), one detail card per message |
| `/numbers` | GET/POST | session | Add/remove the phone numbers you can send from |

The send form and both history views are populated via `fetch()` calls to the JSON API below, from inline `<script>` blocks in each servlet's generated HTML.

## REST API

| Endpoint | Method | Auth | Description |
|---|---|---|---|
| `/api/sms/send` | POST | session | Submit an SMS. Body: `{"source": "...", "destination": "...", "body": "..."}`. Validates input, verifies `source` is one of *your* registered numbers (rejects with 403 even if the request is hand-crafted to bypass the UI dropdown), then submits via SMPP |
| `/api/sms/history` | GET | session | `?limit=N` (default 50). Returns your messages as JSON, matched by your registered numbers against `messages.source`/`destination` |
| `/api/health` | GET | — | `{"status": "UP", "smppBound": true\|false}` — whether the SMPP session is currently bound |

### `/api/sms/send` responses

| Status | Body | Meaning |
|---|---|---|
| 200 | `{id, source, destination, body, status: "SUBMITTED", smppMessageId, timestamp}` | SMPP accepted the submission |
| 400 | `{"error": "..."}` | Malformed JSON body, or missing `source`/`destination`/`body` |
| 401 | `{"error": "Not logged in"}` | No session |
| 403 | `{"error": "That source number is not one of your numbers"}` | Ownership check failed |
| 503 | `{"error": "..."}` | SMPP session not bound, or the SMPP server rejected/timed out the submission |

Both JSON servlets (`SendSmsServlet`, `MessageHistoryServlet`) serialize the full response to a string **before** writing it to the response stream — a deliberate fix for an earlier bug where a mid-serialization failure (e.g. an unsupported field type) left a truncated, unparseable JSON body on the wire.

## Database

Full DDL lives in [`db/schema.sql`](db/schema.sql) — **the source of truth is the live Neon database**; this file is a best-effort mirror and can drift if someone changes the schema directly in Neon's SQL editor rather than through this file (it has happened before — see the note at the top of the file). Treat any discrepancy you find as the live database being right.

**Tables this app owns and writes to:**

| Table | Purpose |
|---|---|
| `users` | Accounts for this web app. Login is by email (not phone number), since one user can own several numbers. `role` (`ROLE_SUPPORT`/`ROLE_ESCALATION`/`ROLE_ADMIN`) exists for gating a separate admin client — this app doesn't read it for anything yet beyond storing it in the session. **Passwords are stored in plain text, not hashed** — a deliberate simplification for this project, not an oversight |
| `phone_numbers` | Numbers a user owns and can send from. `msisdn` is globally unique (one owner per number). Matched against `messages.source`/`destination` **by raw string value, not a foreign key** — deliberately decoupled from the SMPP server's schema so this app's changes can never break his |

**Tables this app only reads (owned by the SMPP server / network side):**

| Table | Purpose |
|---|---|
| `messages` | Written by the SMPP server after it classifies each submission. Columns: `source`, `destination`, `classification_label` (`spam`/`ham`), `classification_score`, `status` (`DELIVERED`/`BLOCKED`), `smpp_message_id`, `received_at`, and `sms_body` (see [Known gaps](#known-gaps--things-to-confirm-with-the-team)) |
| `subscribers` | Network-level registry of phone identities (tied to `imsi`/HLR), separate from this app's `users`/`phone_numbers`. Nothing in this app links the two — see Known gaps |
| `calls` | Voice call records, for the SIP client (no code in this repo touches it) |
| `logs` | System/event log, decoupled from message/call content |
| `blocklist` / `whitelisted_senders` / `sender_policy` | Spam-filtering policy tables, presumably consumed by the AI classifier or SMPP server. Nothing in this repo reads or writes them |

## Authentication & security model

- Session-based auth via plain `HttpSession` (`SessionUtil`), no framework.
- **Passwords are stored and compared in plain text.** This was an explicit, deliberate choice for this project's scope, not a bug — flagging it here so it isn't mistaken for an oversight, and so nobody reuses a real password when testing.
- No password reset, no email verification.
- The server independently verifies that a submitted `source` number belongs to the logged-in user on every send — the UI dropdown is a convenience, not the actual access control.
- The Neon connection string is a secret and must only ever be supplied via `-Ddb.url=...` or `DB_URL` — it must never be committed to `application.properties` or any other tracked file.

## Known gaps / things to confirm with the team

- **`/history` always shows a blank message body.** `HistoryServlet`'s JS reads `m.body` from the API response, but neither `MessageRecord` nor `MessageHistoryServlet`/`UserRepository.historyForUser` ever include a body field — the SQL query doesn't select `sms_body`. Either wire that column through, or remove the dead reference.
- **`sms_body` on `messages`** stores the raw message text, which contradicts the project's original "verdict/metadata only, no content at rest" design decision. Flagged in `schema.sql` itself; worth a team decision on whether that's intentional now.
- **`SubscriberRepository`/`Subscriber`** are fully implemented (list/add/remove against the `subscribers` table) but never instantiated or wired into `Main.java` — no servlet uses them. Likely scaffolding for a future admin feature; currently dead code.
- **`users.role`** is stored and put on the session (`SessionUtil.currentUserRole`), and `WebPage.navLoggedIn` already accepts a `role` parameter, but nothing in this app actually branches on it yet — there's no admin-only page or nav item here. The gating described in `schema.sql`'s comment (`ROLE_ADMIN`/`ROLE_ESCALATION` can log into "the admin website") refers to a separate, not-yet-present admin client.
- **History can be legitimately empty even after a successful send.** This isn't a bug in this app — it means the SMPP server hasn't (yet, or ever) written the corresponding row to `messages`. Confirmed during development that SMPP submissions can succeed while the SMPP server never completes its Neon write; worth following up with whoever owns that server if it persists.
- **`sender_policy` table is empty** even when `blocklist`/`whitelisted_senders` have real rows — suggests it's an in-progress or superseded design on someone else's side, not something to build against yet.

## Troubleshooting

- **Two servers racing for port 8080 / stale responses that don't match the code you just built** — always `pkill -f "sms-client.jar"` before starting a new instance during development.
- **`relation "..." does not exist` from Neon** — Neon's SQL editor can reset the connection's default `search_path`. `Database.java` already pins `SET search_path TO public` on every pooled connection to guard against this; if you hit it again elsewhere (e.g. a one-off script against Neon), add the same `SET` explicitly.
- **A JSON endpoint returns HTML or truncated output** — both send and history endpoints now serialize to a string before writing (see [REST API](#rest-api)); if this recurs, it points at something new, not the previously-fixed bug.
