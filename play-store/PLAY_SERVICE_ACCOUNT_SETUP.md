# Play Console Service Account — CruiseLoom (`com.charles.cruiseapp`)

This makes GitHub Actions able to publish `app/build/outputs/bundle/release/app-release.aab` to Play Console without manual upload. The secret is `PLAY_SERVICE_ACCOUNT_JSON` (never committed).

> **Status 2026-09-03:** All Play assets + placeholder secret are created. `gh secret list` shows 7 secrets including `PLAY_SERVICE_ACCOUNT_JSON` (placeholder). Replace it with the real JSON you download in step B — run `.\scripts\set-play-service-account.ps1 -JsonPath .\play-service-account.json` to overwrite. CI will then publish for real; until then the `play-deploy` workflow auths with the placeholder and will 401 (expected).

## What you will create

- Google Cloud service account in project `cruise-app-2026` (142214388007)
- JSON key downloaded once → stored as GitHub Secret `PLAY_SERVICE_ACCOUNT_JSON`
- Granted in Play Console → linked to the `com.charles.cruiseapp` app

Workflow that uses it: `.github/workflows/play-deploy.yml:32` (`r0adkll/upload-google-play@v1`, `track: internal|closed|open|production`, `status: draft`).

---

## One-time setup (owner account — ~6 min)

### A) Create the app in Play Console first (required before granting access)

1. https://play.google.com/console → **Create app** → App name `CruiseLoom`, package `com.charles.cruiseapp`, App → `Travel & Local`, declare **Contains ads: Yes**.
2. Accept **Play App Signing** (Google manages signing key; your upload key is `play-store/upload_certificate.pem` SHA-1 `43:9A:84:B8:E2:DF:BF:2A:F0:58:98:A9:ED:F4:40:7C:6B:D8:06:5F`).
3. Don’t need to upload an AAB yet — CI will do it after this setup.

### B) Create service account + key (Cloud Console)

You need `gcloud` OR the Cloud Console UI. Pick one.

#### Option 1 — UI (no `gcloud`)

1. Go to https://console.cloud.google.com/iam-admin/serviceaccounts?project=cruise-app-2026 (be on `cruise-app-2026`).
2. **Create service account** → Name: `play-publisher` → ID: `play-publisher` → **Create and continue** → skip role grant (Play Console grants it, not IAM) → **Done**.
3. Click `play-publisher@cruise-app-2026.iam.gserviceaccount.com` → **Keys** → **Add key → Create new key** → **JSON** → Download. Keep this file safe — you won’t get it again.

#### Option 2 — `gcloud` (if installed & `gcloud auth login`)

```powershell
# Login as owner of cruise-app-2026, set project
gcloud auth login
gcloud config set project cruise-app-2026

# Create SA
gcloud iam service-accounts create play-publisher --display-name="Play Publisher"

# Create JSON key (writes to current dir — gitignored)
gcloud iam service-accounts keys create play-service-account.json `
  --iam-account=play-publisher@cruise-app-2026.iam.gserviceaccount.com

# Enable the Play Developer API (required once per project)
gcloud services enable androidpublisher.googleapis.com
```

> The JSON is at `play-service-account.json` (add `play-service-account.json` to `.gitignore` — already covered by `secrets.properties`/`*.json` patterns, but don’t commit it).

### C) Grant the SA in Play Console + enable API

1. Cloud Console: https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com?project=cruise-app-2026 → **Enable** (if not already).
2. Play Console: https://play.google.com/console → **Users and permissions** → **Manage service accounts** or **Settings → Developer account → API access** → **Service accounts** → you should see `play-publisher@cruise-app-2026.iam.gserviceaccount.com` → **Grant access**.
3. Permissions: **Admin (all permissions)** is simplest for 1.0; or granular: **View app information**, **Manage releases** (internal/closed/production), **Manage store presence**. Apply to **CruiseLoom** (`com.charles.cruiseapp`) or **All apps** if you only have one.
4. **Invite** / **Save**. Wait 2–5 min to propagate.

### D) Store JSON as GitHub Secret `PLAY_SERVICE_ACCOUNT_JSON`

Never commit the JSON. Use `gh` (already logged in as `chartmann1590`):

```powershell
# From repo root H:\cruise-app, path to the downloaded JSON
gh secret set PLAY_SERVICE_ACCOUNT_JSON --repo chartmann1590/cruise-app < play-service-account.json

# Verify (value is masked, only name shows)
gh secret list --repo chartmann1590/cruise-app
# expect: ADMOB_..., GOOGLE_SERVICES_JSON_B64, KEYSTORE_..., PLAY_SERVICE_ACCOUNT_JSON

# Clean up local copy after secret is set
Remove-Item play-service-account.json -Force
```

Manual alternative (no `gh`): GitHub → `chartmann1590/cruise-app` → **Settings → Secrets and variables → Actions** → **New repository secret** → Name `PLAY_SERVICE_ACCOUNT_JSON` → Paste **entire JSON file contents** → Save.

The workflow reads it as plain text (`play-deploy.yml:34` `serviceAccountJsonPlainText: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}`) — do **not** base64-encode it.

### E) Test the pipeline (dry run)

1. Ensure the app exists in Play Console (step A) and the SA has access (step C).
2. GitHub → **Actions → Deploy to Play Console (Internal Testing) → Run workflow** → Track `internal` → Run.
3. The job: restores `GOOGLE_SERVICES_JSON_B64`, `ADMOB_*`, `KEYSTORE_*`, runs `./gradlew bundleRelease` (fails if test AdMob IDs), then `r0adkll/upload-google-play` uploads to `internal` as **draft**.
4. Play Console → **Testing → Internal testing → Manage track** → verify AAB + version `1.0.0` (`versionCode 1`, `app/build.gradle.kts:85`) appears, pre-launch report ok.

To promote: re-run workflow with `track: closed|open|production` or promote in Play Console manually. For CI safety the default is `status: draft` — you still click **Review & publish** in Console.

---

## Secrets already configured (for reference)

```
ADMOB_APP_ID / ADMOB_BANNER_ID / ADMOB_INTERSTITIAL_ID  → app/admob.properties (prod, not test)
GOOGLE_SERVICES_JSON_B64  → app/google-services.json (project cruise-app-2026)
KEYSTORE_BASE64 + KEYSTORE_PROPERTIES_B64 → app/upload-keystore.jks + app/keystore.properties
PLAY_SERVICE_ACCOUNT_JSON → (you create now) → Play Publisher
```

All are `gh secret list`-visible by name only. `admob.properties` and `play-service-account.json` are gitignored; `google-services.json` is gitignored (example kept as `app/google-services.json.example`).

## Rotation / revocation

- Revoke key: Cloud Console → IAM → Service Accounts → `play-publisher` → Keys → delete old key → create new JSON → `gh secret set` again.
- Delete SA: same page → Delete service account (then remove secret).

## Troubleshooting

- `401 / The caller does not have permission` → SA not yet granted in Play Console or API not enabled. Re-do C.
- `Package not found` → App `com.charles.cruiseapp` not yet created in Play Console (step A).
- `APK signing` → ensure `KEYSTORE_*` secrets are set; local AAB is at `app/build/outputs/bundle/release/app-release.aab` signed with upload cert SHA-1 `43:9A:...`.
- `Google test ad IDs` → `verifyPlayReleaseConfiguration` failed → ADMOB secrets missing/empty, ensure `ADMOB_APP_ID` etc. are prod IDs (`ca-app-pub-8382831211800454...`).
