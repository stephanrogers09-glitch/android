# Setup: Supabase and Gemini for the LockedIn study assistant

Goal: one successful sign-in and one successful Gemini study request, from a fresh start.
Nothing here is automatic. You create the accounts, copy the values, and run the commands.

Last checked against Supabase's published documentation on 2026-11-21. Section 7 lists
what could not be verified; read it before you start.

---

## 0. Before you start: costs, cards and privacy

| Item | What you do | Card? | Cost | Notes |
|---|---|---|---|---|
| Supabase | Create a project | **Unverified.** Read the sign-up and billing pages. If a card is requested, stop and ask. | Free plan. Current limits and pause rules: check the pricing page. | Pro is a paid upgrade. Do not upgrade. |
| Google AI Studio (Gemini) | Create an API key | **Do not enable billing.** | Free tier, with limits. Limits change. | Free-tier data terms: see privacy below. |
| GitHub | Run CI, download APK | No | Free for this repo | Already in use. |

**Privacy, before you send any real material:**

- **Supabase** stores the test user's email and a password hash, and a per-user count of AI requests. The project region is chosen when you create it.
- **Gemini:** each study request you send is transmitted to Google. Under Google's **unpaid (free) tier**, Google's terms have historically allowed submitted content to be used to improve its products, and human reviewers may read it. **We could not retrieve the current terms, so this is unverified.** Read the current Gemini API terms before using anything personal, and use only invented or public study text for this test.
- The app sends a study request only when you press the button, and nothing is sent at sign-in beyond the email and password you type into Supabase.

---

## 1. Public and secret values

| Value | Public or secret | Where it goes | Never put in |
|---|---|---|---|
| Project URL (`https://<ref>.supabase.co`) | Public | Android build settings | — |
| Publishable key (`sb_publishable_…`) | Public. Safe in apps, and the docs say it is not a JWT. | Android build settings; function secret `LOCKEDIN_PUBLISHABLE_KEY` | — |
| Legacy `anon` key (long JWT starting `eyJ`) | Public. Supabase is deprecating it by end of 2026. | Only if you have no publishable key | — |
| Secret key (`sb_secret_…`) | **SECRET** | Function secret `LOCKEDIN_SECRET_KEY` only | The app, source files, GitHub, chat |
| Legacy `service_role` key | **SECRET** | Avoid; use the secret key | Same as above |
| Gemini API key | **SECRET** | Function secret `GEMINI_API_KEY` only | The app, source files, GitHub, chat |
| Database password | **SECRET** | Password manager | Source files, chat |
| Release keystore and passwords | **SECRET** | Kept by you, outside the repo | GitHub, chat |

Rules:

- The app contains the project URL and the publishable key, and nothing else from this table.
- Supabase's guidance is to send keys in the `apikey` header. Never send a key as `Authorization: Bearer`. Only a signed-in user's session token belongs in `Authorization`.
- Supabase injects `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEYS` and `SUPABASE_SECRET_KEYS` into functions automatically. You do not set these yourself.
- If a secret is pasted anywhere by mistake, rotate it in the Supabase dashboard or Google AI Studio right away.

---

## 2. Phone or computer?

| Step | Phone browser | Computer needed? |
|---|---|---|
| Create Supabase project, copy URL and publishable key | Yes | No |
| Create Gemini key in AI Studio | Yes | No |
| Create test user in the Supabase dashboard | Yes | No |
| Set GitHub repository variables for the app | Yes, on github.com | No |
| Download CI APK and install it | Yes, if your phone allows installing from this source | No |
| `supabase login`, `link`, `db push`, `secrets set`, `functions deploy` | **No** | **Yes.** The Supabase CLI runs in a terminal. |
| Local build with your own Gradle properties (optional alternative to CI) | No | Yes |

So a phone can do everything except the terminal steps. Those need a computer with the Supabase CLI installed.

---

## 3. Minimum configuration for one sign-in and one AI request

1. Supabase project exists.
2. Database migration applied with `supabase db push`.
3. Function secrets set: `LOCKEDIN_PUBLISHABLE_KEY`, `LOCKEDIN_SECRET_KEY`, `GEMINI_API_KEY`.
4. Function `ai-assist` deployed with JWT verification **on** (the default). Do not use `--no-verify-jwt`.
5. App build has `SUPABASE_URL` and `SUPABASE_ANON_KEY` (see step 8). Blank values show a setup message.
6. One test user in Supabase Auth.

**Not needed for this test:** release keystore, `GEMINI_MODEL` (the default is used), legacy keys, billing of any kind.

---

## 4. Step by step

### A. Supabase (phone or computer)

1. Go to supabase.com and sign up. Read any card or billing prompt. **If a card is requested, stop and ask the project owner.**
2. Choose **New project**. Give it a name and choose a region. Save the database password in your password manager.
3. Wait for provisioning to finish.
4. Open the project's **API Keys** page (under Project Settings). Copy the **Project URL** and the **publishable key**. Both are public. Keep the **secret key** in the dashboard for now; you will use it in step C3.

### B. Gemini key (phone or computer)

1. Go to aistudio.google.com and sign in.
2. Choose **Get API key** and create a key. Do **not** attach a billing account.
3. Store the key in your password manager. Do not paste it anywhere else.
4. Read the current free-tier limits and data terms (see section 0).

### C. Terminal steps (computer)

1. Install the Supabase CLI using its official instructions for your system.
2. From the repository folder:
   ```bash
   supabase login
   supabase link --project-ref <your-project-ref>
   ```
   The project ref is the part of the Project URL before `.supabase.co`.
3. Apply the database migration. It asks for the database password:
   ```bash
   supabase db push
   ```
4. Set the function secrets. Enter the values from your password manager. Using `read` keeps them out of your shell history:
   ```bash
   read -rs LOCKEDIN_PUBLISHABLE_KEY && export LOCKEDIN_PUBLISHABLE_KEY
   read -rs LOCKEDIN_SECRET_KEY && export LOCKEDIN_SECRET_KEY
   read -rs GEMINI_API_KEY && export GEMINI_API_KEY
   supabase secrets set LOCKEDIN_PUBLISHABLE_KEY="$LOCKEDIN_PUBLISHABLE_KEY" \
     LOCKEDIN_SECRET_KEY="$LOCKEDIN_SECRET_KEY" GEMINI_API_KEY="$GEMINI_API_KEY"
   ```
   Names starting with `SUPABASE_` are refused by the CLI, which is why the names above begin with `LOCKEDIN_`.
5. Confirm the names are stored. This command shows names only:
   ```bash
   supabase secrets list
   ```
6. Deploy the function. Do not add `--no-verify-jwt`:
   ```bash
   supabase functions deploy ai-assist
   ```

### D. Test user (phone or computer)

1. In the Supabase dashboard, open **Authentication → Users** and choose the option to add a user. Labels may differ slightly.
2. Enter an invented test email and a strong test password. If there is an "auto confirm" option, tick it so you can sign in straight away.
3. **Turn off public sign-ups before going live.** The app has no sign-up screen, and every new account gets its own daily AI quota, so open sign-up lets one person multiply their quota. In the dashboard, open **Authentication** settings and disable new user sign-ups (label may differ; confirm the option is off). Only the accounts you create are then able to sign in.

### E. The app (phone or computer)

Choose one of two routes.

**Route 1, CI build on GitHub (works from a phone):**
1. On github.com, open the repository, then **Settings → Secrets and variables → Actions → Variables**.
2. Add two repository **variables** (not secrets): `LOCKEDIN_SUPABASE_URL` set to the Project URL, and `LOCKEDIN_SUPABASE_ANON_KEY` set to the publishable key. Variables are not masked in logs and are not for sensitive data. Add only these two public values. **Never put a secret key, Gemini key, database password or keystore password in a variable or a secret named like one of these.**
3. Re-run the **Verify** workflow on the feature branch.
4. Download the `lockedin-debug-apk` artifact from that run and install it. Android will ask you to allow installing from this source.

**Route 2, local build (computer):** add these to `~/.gradle/gradle.properties` (outside the repo) and run the debug build:
```properties
lockedin.supabaseUrl=https://<your-project-ref>.supabase.co
lockedin.supabaseAnonKey=sb_publishable_YOUR_PUBLISHABLE_KEY
```

**Test:**
1. Open the study screen and sign in with the test user. Expect the study area to appear.
2. Close the app completely and reopen it. Expect to still be signed in.
3. Sign out. Expect the sign-in form to return.
4. Sign in again, type an invented study paragraph, and ask for a summary. Expect a short answer within a few seconds.

---

## 5. If something fails

| What you see | Meaning | Fix |
|---|---|---|
| Setup message on the study screen | App build has no URL or key | Step E, Route 1 variables, then re-run CI |
| `sign_in_required` / sign-in fails | Wrong email or password, or user not confirmed | Re-check the test user; confirm it |
| `server_not_configured` with names listed | A function secret is missing (names only) | Set the named secret, step C4 |
| HTTP 429 `quota_exceeded` | Configured daily limit reached | Wait, or change the limit in the database |
| `ai_failed` (HTTP 502) | Gemini or an internal call failed | Check the Gemini key and model; check function logs |
| Function logs | Status and error codes only; the function does not log content or keys | Supabase dashboard → Edge Functions → `ai-assist` → Logs (label may differ) |

If you are stuck, stop and ask. Describe the step and the exact message, with any secret replaced by `[REDACTED]`. Never paste a secret.

---

## 6. What this guide does not do

- It does not create billing, upgrade Supabase, or buy anything.
- It does not sign a release APK. Release signing needs your own keystore, which you create and keep. The release build refuses to run without it and never falls back to the debug key.

---

## 7. Unverified items (check before relying on them)

- **Whether a secret key in the `apikey` header alone is accepted** for the quota database calls, with no `Authorization` header. The Supabase docs say keys belong in `apikey` and are not JWTs. This version of the function relies on that. The first live AI request is the test. If it fails, the function returns `ai_failed`, and that is the first thing to investigate.
- **Supabase card requirement, free-plan limits and project pausing:** not verified against the current pricing page.
- **Gemini free-tier data terms and country availability:** not verified; the page could not be retrieved.
- **Model `gemini-2.5-flash`:** the default. Confirm your key can use it; set `GEMINI_MODEL` in the function secrets if not.
- **Dashboard labels and paths** (API Keys, Users, Logs): may differ from this guide.
- **Public sign-up setting** (section 4, step D3): the dashboard option name is unverified.
- **Whether a stolen access token is revoked server-side:** it is valid until it expires (about one hour). Signing out on the phone revokes it through Supabase.
- **Repository variables and fork pull requests:** GitHub's pages we could read did not say whether variables are passed to workflows from forks. This does not matter for secrets, because none are used in CI.
