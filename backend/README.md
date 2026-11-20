# LockedIn backend (Supabase)

Server side of LockedIn: Plus entitlements that only an admin can grant, daily AI request quotas
with configurable limits, and the Gemini proxy. The Gemini key lives only in the Edge Function
environment. It is never in the Android, iOS or web apps.

## What is here

| Path | Purpose |
| --- | --- |
| `supabase/migrations/20261120090000_foundation.sql` | Tables, functions, RLS and grants for entitlements and AI quota |
| `supabase/functions/ai-assist/handler.ts` | Request validation, quota reservation, Gemini call, refund on failure |
| `supabase/functions/ai-assist/index.ts` | Deno entry point; reads environment variables |
| `supabase/tests/local/` | Test-only stub of Supabase's `auth` schema and roles, plus SQL tests |
| `supabase/functions/ai-assist/test/` | Node tests of the handler with a mocked network |
| `scripts/test-db.sh` | Runs the SQL tests on a throwaway local Postgres database |
| `scripts/test-functions.sh` | Strict type-check and Node tests for the function logic |

## Verified so far

- `scripts/test-db.sh` passes against local PostgreSQL 16.15: 8 behaviour groups in the main test file
  and 3 checks as the PostgREST API role (`authenticator`). The migration applies with `ON_ERROR_STOP`.
- `scripts/test-functions.sh` passes: strict `tsc` type-check and 14 Node tests covering auth, input
  limits, quota denial (503 for the kill switch, 429 for limits), the Gemini call shape, and refunds on
  every upstream failure.

## Not verified

- Nothing has run against a real Supabase project. The local stub reproduces the parts of Supabase the
  migration uses (roles, `auth.users`, `auth.uid()`), but Supabase's own defaults may differ.
- The Edge Function has not run under Deno. `index.ts` is only type-checked by eye.
- Gemini has not been called. The default model name `gemini-2.5-flash` is a placeholder that must be
  confirmed against the current Gemini model list before deploy. Set `GEMINI_MODEL` to override it.
- Supabase's free tier limits, and whether a card is required to sign up, are not confirmed.
- The migration's `create policy` statements are not idempotent. Apply it to a fresh database, or drop the
  policies first when re-running.

## Limits and switches (table `ai_settings`)

All values are changed by an admin function, not by a deploy:

| Key | Default | Meaning |
| --- | --- | --- |
| `ai_enabled` | 1 | Kill switch. 0 turns all AI off |
| `free_daily_requests` | 5 | AI requests per user per day, free users |
| `plus_daily_requests` | 100 | AI requests per user per day, Plus users |
| `global_daily_requests` | 2000 | Requests per day across all users. This is the spend ceiling |
| `max_output_tokens` | 1024 | Output cap sent to Gemini on every request |

Days reset at Africa/Kampala midnight. A request that fails upstream is refunded, so failures are not charged.

## Approvals needed before anything is created or paid

1. **Supabase project.** Creating one is needed to deploy. Confirm the free tier and whether it asks for a
   card before creating it. Stop and report if a payment is required (amount, purpose, deadline).
2. **Gemini API key.** Creating a key in Google AI Studio is needed. Confirm whether the free tier covers
   the models chosen, and whether billing must be enabled. Set a hard spending cap before enabling billing.
3. **Deployment.** Running `supabase functions deploy ai-assist` and `supabase db push` changes a live
   project. Do this only after the two items above are approved.

## Deploy steps (after approval)

```
supabase db push                          # applies migrations/ to the linked project
supabase secrets set GEMINI_API_KEY=...   # never commit this value
supabase secrets set GEMINI_MODEL=...     # optional; confirm the model name first
supabase functions deploy ai-assist
```

Environment that Supabase provides to the function: `SUPABASE_URL`, `SUPABASE_ANON_KEY`,
`SUPABASE_SERVICE_ROLE_KEY`. The service role key must never reach a client.

## Running the tests

```
runuser -u postgres -- env PGHOST=<socket dir> PGPORT=<port> PGUSER=postgres backend/scripts/test-db.sh
backend/scripts/test-functions.sh
```
