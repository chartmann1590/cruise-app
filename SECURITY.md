# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | ✅                 |
| < 1.0   | ❌                 |

We take security seriously — this app handles offline P2P chat, notifications, and Firebase telemetry.

## What we consider in-scope

* Remote code execution, data exfiltration, or privilege escalation via the app
* Bypass of Nearby Connections encryption / identity spoofing beyond documented scope
* Exposure of secrets in the public repository (API keys, keystores, service accounts)
* Misconfigured Firebase Hosting headers, caching, or privacy page inaccuracies

Out of scope: Open-Meteo availability, Google Play Services bugs, device-specific Bluetooth quirks that require physical proximity.

## Reporting a Vulnerability — Please Do Not Open a Public Issue

Email via GitHub profile or open a **private** security advisory:

1. Go to **Security → Report a vulnerability** on https://github.com/chartmann1590/cruise-app
2. Or email the maintainer via the GitHub profile with subject `[SECURITY] cruise-app`
3. Include: affected version/commit, reproduction steps, impact, and suggested fix if any

We aim to acknowledge within **48 hours** and provide a fix/mitigation within **7 days** for high severity.

## Handling of Secrets

`app/google-services.json` is **not committed** — a template is at `app/google-services.json.example`. If you accidentally commit a key:

* Rotate it immediately in https://console.firebase.google.com → Project Settings → General / API keys
* Restrict keys under https://console.cloud.google.com/apis/credentials (Android app + package `com.charles.cruiseapp` + SHA-1/SHA-256)
* Purge history with `git filter-repo` or BFG, then force-push and notify us

Current project reference `cruise-app-2026` in docs is the official build only. Forkers must use their own Firebase project.

## Secure Development Notes

* Keystores (`*.jks`, `*.keystore`, `*.p12`) are gitignored. Never commit `release.keystore` or `local.properties` with signing secrets.
* Debug helpers `DebugInjectorActivity` / `PartyTestActivity` are `exported=true` for local testing — do not ship them enabled in a store release without gating by `BuildConfig.DEBUG`.
* All Room data is app-private storage. No Firestore is used for user cruises — Room is source of truth.

## Disclosure

Once fixed, we will publish a brief advisory in Releases and credit the reporter (unless they prefer anonymity). Thank you for keeping travelers safe.
