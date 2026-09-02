# Google Play data safety draft

Use this as the source of truth when completing Play Console. Confirm the final answers against the production AdMob/Firebase configuration before submitting.

## Collection and sharing

- The app collects data: **Yes**.
- The app shares data with third parties: **Yes** (Google Mobile Ads and service providers described in the privacy policy).
- Data is encrypted in transit: **Yes** for network services used by the app.
- Users can request deletion: **Yes for remotely processed diagnostic data through the support contact; local app data is deleted through Clear storage or uninstall.**
- Account creation: **No account is offered**, so the account-deletion requirement does not apply.

## Data types to declare

| Play data type | Collected | Shared | Purpose | Required/optional | Notes |
|---|---:|---:|---|---|---|
| Approximate location | Yes | Yes | Advertising; app functionality | Advertising automatic; weather/port usage optional | AdMob may derive approximate location from IP. Port coordinates are sent to weather/map providers only when those features are used. |
| Precise location | Yes | Yes | App functionality | Optional | If the user enables My Location on the port map, map tile requests can represent the viewed device location. |
| Device or other IDs | Yes | Yes | Advertising, analytics, fraud prevention, diagnostics | Automatic when applicable | Google Mobile Ads/Firebase installation identifiers. |
| App interactions | Yes | Yes | Advertising, analytics, app functionality | Automatic when applicable | Ad impressions/clicks and Firebase analytics/performance activity. |
| Crash logs | Yes | Yes | Diagnostics | Automatic | Firebase Crashlytics. |
| Diagnostics | Yes | Yes | Diagnostics, analytics | Automatic | Firebase Performance and Mobile Ads diagnostics. |
| Other user-generated content | No remote collection by developer | No | App functionality | Optional/local | Cruise plans, events, party names, and chat text remain local or peer-to-peer and are not sent to the developer's server. |

## Security and handling

- Cruise plans, port stops, events, party membership, and chat history are stored in Android app-private storage.
- Android cloud backup is disabled.
- Nearby messages are peer-to-peer; CruiseLoom does not operate a chat relay server.
- The privacy policy URL is `https://cruise-app-2026.web.app/privacy`.

Do not select “no data collected”: AdMob, Firebase Analytics, Crashlytics, and Performance require disclosures.
