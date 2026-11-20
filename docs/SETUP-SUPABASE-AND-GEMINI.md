# Setup: Supabase and Gemini for the LockedIn study assistant

This guide takes you from nothing to a working sign-in and AI study request.
It is for a first-time setup. Nothing here is done for you automatically: you
create the accounts, you copy the values, and you run the commands yourself.

**Read the cost and account notes in section 0 first.**

---

## 0. Before you start: accounts, cards and costs

| Service | What you create | Card needed? | Cost | Notes |
|---|---|---|---|---|
| Supabase | A free project | Check at sign-up. We have not verified whether a card is required. | Free tier. Pro starts at about $25/month. | Free projects **pause after one week of inactivity**. Unpausing is a click in the dashboard. |
| Google AI Studio (Gemini) | An API key | Do **not** enable billing. | Free tier with limits. | Read Google's current free-tier data-use terms before using real student notes. |
| GitHub | Already in use for CI | No | Free | Used only to build the APK. |

**Do not enable billing on Google Cloud or upgrade Supabase** during this
setup. If a step asks for a card or a payment, stop and check with the project
owner first. Limits, prices and card rules change; check the live pages.

---

## 1. Know which values are public and which are secret

This is the most important table in the guide. Read it before copying anything.

| Value | Public or secret | Where it goes | Never put in |
|---|---|---|---|
| Project URL (`https://xxxx.supabase.co`) | Public | Android build settings | — |
| Publishable key (`sb_publishable_…`) or legacy `anon` key | Public (designed for apps) | Android build settings, and the function setting `LOCKEDIN_PUBLISHABLE_KEY` | — |
| Secret key (`sb_secret_…`) or legacy `service_role` key | **SECRET** | Function setting `LOCKEDIN_SECRET_KEY` only | The Android app, source files, GitHub, chat |
| Gemini API key | **SECRET** | Function setting `GEMINI_API_KEY` only | The Android app, source files, GitHub, chat |
| Database password | **SECRET** | Your password manager | Source files, chat |
| Release keystore file and passwords | **SECRET** | Kept by you, outside the repository | GitHub, chat |

Rules that follow from the table:

- The Android app may contain the project URL and the publishable/anon key only.
- A key starting with `sb_secret_` or a JWT whose role is `service_role` must never reach the phone. The app refuses both.
- Do not paste any **SECRET** value into a file in this repository, into a GitHub issue, or into a chat.
- If a secret is pasted somewhere by mistake, rotate it in the provider's dashboard straight away.

---

## 2. Create the Supabase free project

1. Open https://supabase.com and sign up. Check the page for any card requirement and stop if one is asked for.
2. Choose **New project**. Pick a name such as `lockedin-dev` and a region near you.
3. Set a strong **database password** and save it in your password manager (secret).
4. Wait for the project to finish provisioning.

## 3. Find the Project URL and the publishable key

1. In the dashboard, open **Project Settings → API Keys** (the label may differ slightly).
2. Copy the **Project URL**. It looks like `https://abcdwxyz.supabase.co`. This is **public**.
3. Copy the **publishable key** (`sb_publishable_…`). This is **public**.
   - If your project only shows the older **anon** key, copy that instead. It is a long JWT and also **public**.
4. Find the **secret key** (`sb_secret_…`) or the **service_role** key. Do not copy it into any file. You will enter it only into the terminal in section 6, and only as a function secret. It is **SECRET**.

## 4. Create the Gemini key in Google AI Studio

1. Open https://aistudio.google.com and sign in with a Google account.
2. Go to **Get API key** and create a key. Choose a project when asked, and do **not** attach a billing account.
3. Copy the key once. It is **SECRET**. Do not paste it into any file.
4. Check the current free-tier limits and data-use terms on Google's pricing page before using real study material.

Note: the model name is set by `GEMINI_MODEL` and defaults to `gemini-2.5-flash`. Confirm that model is available to your key in AI Studio. If it is not, set `GEMINI_MODEL` to a model your key can use.

## 5. Put the Android build settings in your own Gradle file

These two values are **public**, so they may live in your personal Gradle file. Keep that file outside the repository.

Edit `~/.gradle/gradle.properties` (create it if it does not exist):

```properties
lockedin.supabaseUrl=https://YOUR-PROJECT.supabase.co
lockedin.supabaseAnonKey=sb_publishable_YOUR_PUBLISHABLE_KEY
```

Alternatively, set environment variables before building:

- `LOCKEDIN_SUPABASE_URL`
- `LOCKEDIN_SUPABASE_ANON_KEY`

If either value is missing, the app shows a clear setup message on the study screen instead of crashing.

Do **not** add the Gemini key or the secret key here. They never belong in the app.

## 6. Store the secrets for the Edge Function

Install the Supabase CLI following its official instructions for your system. Then, in a terminal inside the repository:

```bash
supabase login
supabase link --project-ref YOUR-PROJECT-REF
```

`YOUR-PROJECT-REF` is the part of the Project URL before `.supabase.co`.

Store the function secrets. Run this yourself, and replace the placeholders with your values. **Do not write the real values into a file.**

```bash
supabase secrets set \
  LOCKEDIN_PUBLISHABLE_KEY='sb_publishable_YOUR_PUBLISHABLE_KEY' \
  LOCKEDIN_SECRET_KEY='sb_secret_YOUR_SECRET_KEY' \
  GEMINI_API_KEY='YOUR_GEMINI_KEY'
```

Notes:

- The Supabase URL is provided to the function by Supabase itself. You do not set it.
- Supabase does not accept secret names that start with `SUPABASE_`, which is why the function uses the `LOCKEDIN_*` names.
- To confirm the names are stored (not the values), run `supabase secrets list`.
- If the function is missing a setting, it returns HTTP 503 with `server_not_configured` and the **names** of the missing settings. No values are shown.

## 7. Deploy the database and the function

```bash
supabase db push
supabase functions deploy ai-assist
```

- `db push` applies the migration in `backend/supabase/migrations/`.
- `functions deploy` uploads `backend/supabase/functions/ai-assist/`.
- Deployment is a change to your live project. Only run it if you are happy for that project to go live with this code.

## 8. Test sign-in and an AI request

**Create a test student account.** In the dashboard, open **Authentication → Users → Add user**, and create a test email and password. Use a test address you own. Do not use real student data for this test.

**Sign in from the app.** Build and install the debug APK from the CI artifact (`lockedin-debug-apk`). Open the study screen, sign in with the test account, and check that:

1. Signing in succeeds and the study area appears.
2. Closing and reopening the app keeps you signed in.
3. Signing out returns you to the sign-in form.

**Send one AI request.** Enter a short study question while signed in. Expect a short answer within a few seconds.

If it fails, look at the message shown in the app:

| Message in app | Meaning | What to check |
|---|---|---|
| Setup message about URL or key | Build settings missing or wrong | Section 5 |
| Sign-in required | Session missing or expired | Sign in again |
| Server not configured (lists names) | Function secret missing | Section 6, the named secret |
| Quota or daily limit | Your configured limit was reached | Wait, or raise the limit in the database deliberately |
| AI request failed | Gemini or the upstream call failed | Check the key, the model name, and the function logs in the dashboard |

**Check the function logs** in the Supabase dashboard under **Edge Functions → ai-assist → Logs**. The function never logs request content or keys, so the logs show status only.

---

## 9. Things this guide does not do

- It does not create a Google Cloud billing account, upgrade Supabase, or buy a domain.
- It does not sign the release APK. Release signing needs your own keystore, which you create and keep yourself. The release build refuses to run without it and never falls back to the debug key.
- It does not verify that a card is or is not required by Supabase. Check that page at sign-up.

## 10. If you are stuck

Stop and ask. Do not paste **SECRET** values into the question. Describe the step and the exact message you see, with any secret replaced by `[REDACTED]`.
