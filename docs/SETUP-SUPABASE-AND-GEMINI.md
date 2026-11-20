# Live test runbook: Supabase, Gemini and the LockedIn study session

Goal: one successful registration, sign-in, restart persistence, sign-out, token refresh and AI
request, using **invented study material only**.

Code this runbook matches: branch `feature/cross-platform-foundation`, commit `0126d55` or later.
Supabase facts were checked against Supabase's documentation on 2026-11-21. Items marked
**(check on screen)** are menu labels that may differ slightly.

Do not create accounts, enable billing or spend money for this test. The steps below assume a
free Supabase project and no Gemini billing.

---

## 1. Values: what to copy, where it goes, what stays secret

| Value | Public or secret | Where it goes | Never |
|---|---|---|---|
| Project URL `https://<ref>.supabase.co` | Public | GitHub repository **variable** `LOCKEDIN_SUPABASE_URL` | Secrets, source files |
| Project ref (the `<ref>` part of the URL) | Public | Terminal: `supabase link --project-ref` | — |
| Publishable key `sb_publishable_…` | Public | GitHub repository **variable** `LOCKEDIN_SUPABASE_ANON_KEY`, and function secret `LOCKEDIN_PUBLISHABLE_KEY` | A JWT field, a source file |
| Secret key `sb_secret_…` | **SECRET** | Function secret `LOCKEDIN_SECRET_KEY` only | The app, variables, GitHub, chat |
| Gemini API key | **SECRET** | Function secret `GEMINI_API_KEY` only | The app, variables, GitHub, chat |
| Database password | **SECRET** | Typed at the CLI prompt only | Files, chat |
| Test user password | **SECRET** | Typed into the app only | Files, chat |

Rules: repository variables are public and unmasked, so they hold only the two public rows.
Never paste a secret into a file, a GitHub variable or a chat. If one leaks, rotate it in the
Supabase dashboard or Google AI Studio immediately.

---

## 2. Before the test: setup checklist

- [ ] Supabase project created on the free plan. If the sign-up page asks for a card, **stop and ask**.
- [ ] Gemini key created in Google AI Studio with **no billing account attached**.
- [ ] Supabase: **Allow new users to sign up** is **off** (step 3B).
- [ ] Supabase: test user created (step 3C).
- [ ] GitHub: repository variables `LOCKEDIN_SUPABASE_URL` and `LOCKEDIN_SUPABASE_ANON_KEY` set (step 4).
- [ ] Terminal steps done on a computer (step 5): migration applied, three function secrets set, `ai-assist` deployed.
- [ ] A **new** Verify run started **after** the variables were set (step 4). An APK built before this does not contain the URL and key and will show the setup message.

---

## 3. Supabase dashboard steps (phone or computer)

### A. Create the project
1. Go to supabase.com, sign up, and open **New project**. Choose a name (for example `lockedin-test`) and a region.
2. Save the database password in your password manager.
3. Wait for provisioning to finish.
4. Open **Project Settings → API Keys** (**check on screen**). Copy the **Project URL** (public) and the **publishable key** (public). Note where the **secret key** is shown; copy it only in step 5.

### B. Turn off public sign-ups (do this before any real use)
1. Open **Authentication**, then the **Sign In / Providers** page. The direct address is `https://supabase.com/dashboard/project/<ref>/auth/providers` (**check on screen**).
2. Find the setting named **Allow new users to sign up** and switch it **off**.
3. Effect (from Supabase's documentation): if this is disabled, only existing users can sign in. Accounts you create by hand still work.

### C. Create the test user (registration for this test)
The app has no sign-up screen, so the test user is created in the dashboard.
1. Open **Authentication → Users**, then **Add user** (**check on screen**).
2. Enter an invented email, for example `tester+live1@example.com`, and a strong password. Tick **Auto Confirm User** if it is offered, so you can sign in at once (**check on screen**).
3. Write the password in your password manager. Do not type it anywhere else.

---

## 4. GitHub: repository variables and a fresh APK (phone or computer)

1. On github.com open the repository, then **Settings → Secrets and variables → Actions → Variables** (**check on screen**). Use the **Variables** tab, not Secrets.
2. Add `LOCKEDIN_SUPABASE_URL` = the Project URL from step 3A.
3. Add `LOCKEDIN_SUPABASE_ANON_KEY` = the publishable key from step 3A.
4. Go to **Actions → Verify**, choose branch `feature/cross-platform-foundation`, and **Run workflow**. Wait for it to finish green.

---

## 5. Terminal steps (computer, run by your technical person)

Run these inside the `backend` folder of the repository. The Supabase CLI looks for the `supabase/`
folder from its working directory, and our migrations and function are in `backend/supabase/`.

Install the Supabase CLI with its official instructions for your system first.

```bash
cd backend

# 1. Sign in and link the project (the ref is the part of the Project URL before .supabase.co)
supabase login
supabase link --project-ref <REF>

# 2. Only if the CLI reports a missing config file: create one. It adds config files and
#    leaves the existing migrations and functions alone. Answer no to any editor prompts.
supabase init

# 3. Apply the database migration. Preview first, then apply.
supabase db push --dry-run --remote
supabase db push --remote

# 4. Set the function secrets. Each read prompt hides what you type.
read -rs LOCKEDIN_PUBLISHABLE_KEY && export LOCKEDIN_PUBLISHABLE_KEY
read -rs LOCKEDIN_SECRET_KEY && export LOCKEDIN_SECRET_KEY
read -rs GEMINI_API_KEY && export GEMINI_API_KEY
supabase secrets set LOCKEDIN_PUBLISHABLE_KEY="$LOCKEDIN_PUBLISHABLE_KEY" \
  LOCKEDIN_SECRET_KEY="$LOCKEDIN_SECRET_KEY" GEMINI_API_KEY="$GEMINI_API_KEY"
unset LOCKEDIN_PUBLISHABLE_KEY LOCKEDIN_SECRET_KEY GEMINI_API_KEY

# 5. Confirm the NAMES are stored (values are never shown)
supabase secrets list

# 6. Deploy the function. JWT verification stays ON (the default). Do not add --no-verify-jwt.
supabase functions deploy ai-assist
```

Notes:
- Supabase injects `SUPABASE_URL` itself. Do not set it.
- The CLI refuses secret names starting with `SUPABASE_`, which is why ours start with `LOCKEDIN_`.
- If `secrets list` does not show all three names, stop. Do not deploy until they appear.
- The function returns HTTP 503 with `server_not_configured` and the names of any missing settings. It never shows values.

---

## 6. Download the latest APK from GitHub Actions

**On a phone:** install the GitHub app and sign in. Open the repository, then **Actions**, then
the newest green **Verify** run on `feature/cross-platform-foundation`. Scroll to **Artifacts** and
download `lockedin-debug-apk`. GitHub requires you to be signed in to download artifacts. Open the
downloaded ZIP and install the APK. Android will ask you to allow installing from that app.

**On a computer:**
```bash
gh run list --branch feature/cross-platform-foundation --workflow Verify --status success --limit 1
gh run download <RUN_ID> -n lockedin-debug-apk
```

The artifact is kept for 14 days. This is a **debug** build for testing only, not for distribution.

---

## 7. Live test checklist (invented material only)

Use only this invented study note. Do not paste real notes, personal data or anyone else's material.

> **Fictional note:** The Blue Lantern Theorem says that every quiet garden contains exactly
> seven glass moths. Students learn it by counting the moths in their own imaginary garden.

Record each result as PASS or FAIL. Stop at the first FAIL and report it.

| # | Test | Steps | Expected |
|---|---|---|---|
| 1 | Setup is present | Open the app. Tap **Study session with AI**. | Sign-in form with **Email** and **Password** appears. No setup message. If a message appears, stop: step 4 was not done or the APK is old. |
| 2 | Registration is closed | Look for any sign-up option. | None exists. The test user is the only way in. |
| 3 | Wrong password | Enter the test email and a wrong password, tap **Sign in**. | "That email and password did not match an account." |
| 4 | Sign-in | Enter the test email and correct password, tap **Sign in**. | "Signed in." and a **Sign out** button appear. |
| 5 | Restart persistence | Close the app fully (remove from recent apps), reopen, tap **Study session with AI**. | Still shows "Signed in." without asking for the password again. |
| 6 | AI request | Paste the fictional note into the material box. Tap **Explain**. | "Thinking…", then a short answer about the fictional note appears within about 30 seconds. |
| 7 | Sign-out | Tap **Sign out**. Close and reopen the app. | Sign-in form returns. It stays signed out after restart. |
| 8 | Token refresh | Sign in (test 4). Wait **more than one hour** without signing out. Paste the note and tap **Summarise**. | The request succeeds with no sign-in prompt. The app refreshes the token itself. If "Your session has ended. Sign in again." appears, record it as FAIL. |
| 9 | Daily limit (optional) | After test 8, keep tapping **Explain** until the limit is reached. | After the default 5 requests in one Kampala day (the limit is a database setting), shows "You have used today's AI requests. They reset at midnight (Kampala time)." |

Why test 8 takes an hour: Supabase's default access-token lifetime is one hour, and the app
refreshes a token that is within one minute of expiry. Waiting is the only way to exercise that
path without changing the project's settings.

### After the test
1. In Supabase, **Authentication → Users**, delete the test user (**check on screen**).
2. Record results, the APK run ID, and the time. Do not record passwords or keys.
3. If anything failed, check function logs: **Edge Functions → ai-assist → Logs** (**check on screen**).

### Error messages you may see

| Message on the phone | Meaning | Fix |
|---|---|---|
| Setup message on the study screen | URL or publishable key missing in this APK | Step 4, then a new APK |
| "Enter your email and password." | Form empty | Enter both |
| "That email and password did not match an account." | Wrong credentials, or the user does not exist | Check the test user in the dashboard |
| "Your session has ended. Sign in again." | Refresh token rejected, or signed out elsewhere | Sign in again. If it happens right after sign-in, check step 3C |
| "Too many attempts. Wait a few minutes and try again." | Rate limit | Wait |
| "Could not reach the server…" | Network or Supabase unreachable | Check connection; check the project is not paused |
| "The AI helper is switched off right now." | Kill switch, or the function is unavailable | Check `ai_enabled` in the database |
| "The AI helper is not set up in this build." | Build settings missing | Step 4 |
| "You have used today's AI requests…" | Daily limit reached | Expected in test 9 |
| "Sign-in failed. Try again." | Unexpected server answer | Check function logs |

---

## 8. Not covered by this test

- Release signing. Release builds need your own keystore and are separate.
- Gemini free-tier data terms. Read Google's current terms before any real material is sent.
- Supabase card requirements and free-plan limits. Verify on the pricing page.
- Whether a secret key sent only in the `apikey` header is accepted for the quota calls. The first
  AI request in test 6 is the check. If it fails, the phone shows "Sign-in failed" or "The AI helper…"
  and the function logs show the cause.
