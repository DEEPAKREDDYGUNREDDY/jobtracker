# Job Tracker — Backend

Spring Boot 3 + Postgres backend for the job tracker app. Handles user auth (JWT),
CRUD for job applications, and now Gmail sync + Claude-based email classification
that auto-updates your applications as ATS emails arrive.

## Stack
- Java 17, Spring Boot 3.3
- Spring Security + JWT (stateless)
- Spring Data JPA + Postgres
- Flyway for schema migrations

## Local setup

1. **Start Postgres** (Docker is easiest):
   ```bash
   docker run --name job-tracker-db -e POSTGRES_PASSWORD=postgres \
     -e POSTGRES_DB=job_tracker -p 5432:5432 -d postgres:16
   ```

2. **Configure environment** — copy `.env.example` to `.env` and adjust as needed,
   or export the variables directly. At minimum, set a real `JWT_SECRET` before
   deploying anywhere public.

3. **Run the app**:
   ```bash
   ./mvnw spring-boot:run
   ```
   Flyway will automatically create the schema on first boot.

4. **Verify it's up**:
   ```bash
   curl http://localhost:8080/actuator/health
   ```

## API overview

### Auth (public)
| Method | Path                | Body                                    |
|--------|----------------------|------------------------------------------|
| POST   | `/api/auth/register` | `{ email, password, fullName }`         |
| POST   | `/api/auth/login`    | `{ email, password }`                   |

Both return `{ token, email, fullName }`. Send the token on every other
request as `Authorization: Bearer <token>`.

### Applications (requires auth)
| Method | Path                        | Notes                          |
|--------|------------------------------|----------------------------------|
| GET    | `/api/applications`          | optional `?status=INTERVIEW`   |
| GET    | `/api/applications/{id}`     |                                  |
| POST   | `/api/applications`          | create                          |
| PUT    | `/api/applications/{id}`     | update                          |
| DELETE | `/api/applications/{id}`     |                                  |

Application body:
```json
{
  "companyName": "Acme Corp",
  "roleTitle": "Backend Engineer",
  "status": "APPLIED",
  "jobUrl": "https://...",
  "appliedDate": "2026-07-01",
  "notes": "Referred by Jane"
}
```

Valid `status` values: `APPLIED`, `SCREENING`, `INTERVIEW`, `OFFER`,
`REJECTED`, `WITHDRAWN`, `ACCEPTED`.

### Gmail + Claude (requires auth)
| Method | Path                  | Notes                                              |
|--------|------------------------|-----------------------------------------------------|
| GET    | `/api/gmail/connect`   | Returns `{ authUrl }` — redirect the user there      |
| GET    | `/api/gmail/callback`  | Google redirects here after consent (public, state-verified) |
| GET    | `/api/gmail/status`    | `{ connected, googleEmail, lastSyncedAt }`           |
| POST   | `/api/gmail/sync`      | Trigger a sync immediately, returns a summary        |

The scheduler also runs this automatically every `GMAIL_SYNC_INTERVAL_MS`
(default 5 min) for every connected account.

## Setting up Gmail + Claude locally

1. **Google Cloud project** (~10 min):
   - Create a project at [console.cloud.google.com](https://console.cloud.google.com).
   - Enable the **Gmail API** (APIs & Services -> Library).
   - Configure the **OAuth consent screen** (External, add your own Google account as a test user while in testing mode).
   - Create an **OAuth client ID** (Web application) under Credentials. Add
     `http://localhost:8080/api/gmail/callback` as an authorized redirect URI.
   - Copy the client ID/secret into `.env` as `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`.

2. **Anthropic API key**: grab one from [console.anthropic.com](https://console.anthropic.com)
   and set `ANTHROPIC_API_KEY` in `.env`.

3. **Run the app**, then connect your Gmail:
   ```bash
   # Register + login as usual, then:
   curl localhost:8080/api/gmail/connect -H "Authorization: Bearer <token>"
   # Open the returned authUrl in a browser, sign in, grant access.
   # Google redirects to /api/gmail/callback, which stores your tokens.
   ```

4. **Trigger a sync manually** instead of waiting for the scheduler:
   ```bash
   curl -X POST localhost:8080/api/gmail/sync -H "Authorization: Bearer <token>"
   ```
   Response looks like:
   ```json
   { "fetched": 4, "skippedAlreadyProcessed": 0, "classified": 2, "applicationsMatched": 1, "applicationsCreated": 1 }
   ```

5. **Check the results**:
   ```bash
   curl localhost:8080/api/applications -H "Authorization: Bearer <token>"
   ```

### How it works
1. `GET /api/gmail/connect` issues a signed, 10-minute `state` token (HMAC over
   your user id) and returns Google's consent URL — this is how the public
   `/callback` endpoint knows which user is connecting, since Google's redirect
   carries no auth header of ours.
2. On callback, we exchange the auth code for an access + refresh token and
   store them in `gmail_accounts`, keyed to your user id.
3. Every sync (scheduled or manual) lists Gmail messages received after the
   last sync cursor (`q=after:<epoch>`), skipping anything already recorded
   in `email_events`.
4. Each new message passes through a free keyword/sender **pre-filter**
   (skips newsletters, socials, etc. without spending an API call).
5. Survivors go to **Claude** (`ClaudeClassificationService`) with the
   subject/sender/snippet, asking for strict JSON: is it job-related, what
   company/role/status, and how confident. Model and threshold are
   configurable (`ANTHROPIC_MODEL`, `GMAIL_CONFIDENCE_THRESHOLD`).
6. If confidence clears the threshold, we match an existing `job_applications`
   row by company name (case-insensitive) and update its status, or create a
   new one with `source: EMAIL`.
7. Every processed message gets a row in `email_events` regardless of
   outcome — an audit trail for "why did this move to Interview?" and a
   dedupe guard against reprocessing.

### Notes / known limitations
- Only the message **subject, sender, and snippet** are classified (not the
  full MIME body) — keeps the Gmail API calls and Claude prompt simple.
  Good enough for most ATS emails; multi-paragraph nuance may be missed.
- One Gmail account per user for now.
- Access/refresh tokens are stored in plaintext in `gmail_accounts` — fine
  for local development, but encrypt them at rest before deploying anywhere
  public.
- `GET /api/gmail/callback` is intentionally public (no JWT) since Google's
  redirect can't carry one; the signed `state` param is what ties it back to
  a specific user, and it expires after 10 minutes.

## Quick smoke test

```bash
# Register
curl -X POST localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"password123","fullName":"You"}'

# Copy the returned token, then:
curl -X POST localhost:8080/api/applications \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"companyName":"Acme Corp","roleTitle":"Backend Engineer","status":"APPLIED"}'

curl localhost:8080/api/applications -H "Authorization: Bearer <token>"
```

## What's next
- Outlook/Microsoft Graph support (currently Gmail-only)
- Encrypt `access_token`/`refresh_token` at rest (currently plaintext columns)
- Frontend dashboard wired up to `/api/applications` and `/api/gmail/*`
- Low-confidence classifications currently get skipped silently — consider
  surfacing them to the user for manual confirmation instead
- Deployment: Railway/Render for the app, Neon/Supabase for Postgres
