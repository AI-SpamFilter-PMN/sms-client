# SMS Client (SpamGuard)

> A small Java SE webapp that starts an embedded Tomcat to let users send SMS
> via an SMPP gateway. Messages are classified by a backend SMPP server and
> recorded in the messages table; this client reads that table for history.

## Prerequisites

- Java 17+ (JDK)
- Maven 3.6+

## Build

To compile and produce the runnable jar:

```bash
mvn -DskipTests package
```

The shaded JAR will be created at `target/sms-client.jar`.

## Run

Run the application with:

```bash
java -jar target/sms-client.jar
```

Configuration is read from `src/main/resources/application.properties` and
any value may be overridden with a system property (e.g. `-Dserver.port=8080`)
or environment variable (e.g. `SERVER_PORT`). See `com.spamfilter.smsclient.config.AppConfig` for the full list of keys.

## Web UI & API

- Web UI (login required):
  - `/` — Send SMS (composed from one of your owned numbers)
  - `/history` — Full message history (server-rendered detailed view)
  - `/numbers` — Manage your phone numbers
- JSON API:
  - `POST /api/sms/send` — Submit an SMS, body: `{ "source": "...", "destination": "...", "body": "..." }`
  - `GET /api/sms/history?limit=<n>` — Returns the user's messages (read-only)

## History behavior

The index page shows the most recent messages (limited to 5 by default).
The `/history` page requests the JSON API and renders a detailed list (the
page currently requests up to 1000 rows via the `limit` query). If you need
server-side paging or streaming for very large histories I can add pagination to
the API and the page.

## Notes

- Secrets such as the database URL must be supplied via system properties or
  environment variables (see the `AppConfig` class). Do not commit secrets into
  `application.properties`.
- The project uses an embedded Tomcat and starts an SMPP client at startup.

## Files

- Web UI entry: [src/main/java/com/spamfilter/smsclient/Main.java](src/main/java/com/spamfilter/smsclient/Main.java)
- Server-rendered pages: [src/main/java/com/spamfilter/smsclient/servlet](src/main/java/com/spamfilter/smsclient/servlet/)
