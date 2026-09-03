# 🚢 CruiseLoom — Offline-First Cruise Companion

[![Google Play](https://img.shields.io/badge/Google_Play-Draft_Ready-4285F4?logo=googleplay&logoColor=white)](https://play.google.com/store/apps/details?id=com.charles.cruiseapp)
[![Android](https://img.shields.io/badge/Platform-Android%208%2B-3DDC84?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin&logoColor=white)](#)
[![Jetpack Compose](https://img.shields.io/badge/Compose-1.5.8-4285F4?logo=jetpackcompose&logoColor=white)](#)
[![ML Kit Translation](https://img.shields.io/badge/Google_ML_Kit-33_Languages-00C853?logo=google&logoColor=white)](#)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Website](https://img.shields.io/badge/Website-cruise--app--2026.web.app-0B1E3B)](https://cruise-app-2026.web.app)
[![Privacy](https://img.shields.io/badge/Privacy-Policy-0EA5A3)](https://cruise-app-2026.web.app/privacy)

**Plan your entire cruise without Wi-Fi — with 100% free on-device translation in 33 languages.**

CruiseLoom is an offline-first Android app for cruise passengers. Create your voyage, add real port stops, plan each sea day, get live weather (no API key), translate the entire app offline via Google ML Kit, explore interactive ship decks, receive local reminders, and chat with your party over **Bluetooth / BLE / Wi-Fi Direct** and **Guest Wi-Fi Hotspot** — even in the middle of the ocean.

> **🌐 Website:** [https://cruise-app-2026.web.app](https://cruise-app-2026.web.app) · **▶️ Google Play:** [com.charles.cruiseapp](https://play.google.com/store/apps/details?id=com.charles.cruiseapp) · **🎬 Promo:** [youtu.be/IcxRiCjYRSk](https://youtu.be/IcxRiCjYRSk) · **📄 Privacy Policy:** [https://cruise-app-2026.web.app/privacy](https://cruise-app-2026.web.app/privacy) · **📦 Package:** `com.charles.cruiseapp`

No mock data. No cloud sync for your plans. Your cruises, ports, events and chats stay on your device.

---

## ✨ Why CruiseLoom?

* **Built for the ship, not the shore.** Room is the source of truth. Weather is cached for 3 hours, messages queue until a peer is in range.
* **No accounts, no roaming surprises.** Party identity via QR code — scan, don't type. Chat is encrypted P2P via Google Nearby Connections (`P2P_CLUSTER`).
* **Private by design.** No server sees your itinerary. Crash reports are anonymized.

---

## 📸 Screenshots — Pixel 8 Pro

| Welcome | Cruise Setup | Calendar |
|---|---|---|
| ![Welcome](screenshots/01_welcome.png) | ![Setup](screenshots/02_cruise_setup.png) | ![Calendar](screenshots/04_calendar_picker.png) |

| Dashboard | Port List | Weather |
|---|---|---|
| ![Dashboard](screenshots/06_dashboard.png) | ![Ports](screenshots/07_port_list.png) | ![Weather](screenshots/08_weather.png) |

| Day Detail | Party Chat | My QR |
|---|---|---|
| ![Day](screenshots/09_day_detail.png) | ![Party](screenshots/10_party.png) | ![QR](screenshots/11_my_qr.png) |

> Fresh install shows **Welcome Aboard → Create Cruise** only. Screenshots use real demo data injected via a local Debug activity — the app ships with an empty database.

---

## 🎯 Features

### 🗓️ Cruise & Daily Planner
* Create a cruise by ship name + calendar date range. Duration is auto-calculated and every day is generated automatically.
* Per-day view with sea/port badges, add/edit/delete events (title, time, location, category, reminder 1–60 min).

### 📍 Real Port Stops
* Search any city via **Open-Meteo Geocoding** (free, no API key) — lat/lon auto-filled, stored locally.
* Arrival/departure dates, country and order preserved.

### 🌤️ Weather — Free, No API Key
* `api.open-meteo.com/v1/forecast` + geocoding — 7-day daily + current, mapped to emoji, cached 3 h for offline use.
* Pull once on port Wi-Fi, view on sea days with no signal. Attribution CC BY 4.0.

### ⏳ Cruise Countdown + Daily Reminders
* If your cruise is in the future, Dashboard shows a bold **"X days to go!"** card with ship name, sail date, progress bar, and weather snippet for your departure port (from cached `WeatherCache` or 7-day forecast).
* Get a **daily 9 AM system notification** (`cruise_countdown` channel, `IMPORTANCE_LOW`) — enriched with weather when available, `Bon voyage!` on sail day, then auto-cancels. Rescheduled after reboot via `BootRescheduleReceiver`. No API key, fully local `AlarmManager`.

### 🗺️ Port Map — Free, No API Key (NEW)
* OpenStreetMap via `osmdroid 6.1.20` (`TileSourceFactory.MAPNIK`) — no Google key, no billing, OSM tiles cached in `filesDir/osmdroid/tiles` for offline sea days.
* See your full itinerary polyline + port pins (ordered via `orderIndex`), filter chips per port, tap pin for weather/navigate, **My Location** dot (optional `ACCESS_FINE_LOCATION` grant), **Download offline** to cache `z9-15` tiles on port Wi-Fi.
* Attribution `© OpenStreetMap contributors (ODbL)` on map. Works offline after first pull — same pattern as `WeatherCache` 3h TTL.

### 🚢 Ship Deck Maps — On-Demand, Offline After Download (NEW)
* **25 ships, 211 CC0 deck schematics** hosted **inside this repo** (`public/decks/` → Firebase Hosting `cruise-app-2026.web.app/decks/` + raw GitHub fallback) — **no API key**.
* Tap **Ship Decks** on Dashboard → searchable catalog (fuzzy match against your `Cruise.shipName` + `aliases`), per-ship `View decks` or `Download` (~300 KB/deck, ~1–3 MB/ship). **Pinch-to-zoom, drag, swipe** between decks, bottom `ScrollableTabRow` + zoom buttons, offline badge.
* After `Download`, images saved to `filesDir/decks/<shipId>/` — view at sea with no signal. Link-only entries open official cruise line site. All images are **CC0 original vectors** (own expression, not cruise line PDFs).

### 🔔 Local Notifications
* Battery-aware local reminders via `AlarmManager.setAndAllowWhileIdle`, rescheduled after reboot without restricted exact-alarm permission.
* Supports Android 13+ `POST_NOTIFICATIONS` and `SCHEDULE_EXACT_ALARM`.

### 💬 Offline Party Chat — The Headliner (now with Guest Wi-Fi)
* **Transport:** Google Play Services Nearby Connections `P2P_CLUSTER` → Bluetooth + BLE + Wi-Fi Direct, encrypted, no internet, ~100 m per hop.
* **Guest Wi-Fi Chat — no app needed (NEW):** Tap **Start Guest Chat** in Party → your phone creates a *local-only* hotspot (no internet uplink) + tiny web server on `8085`. Guests join the Wi-Fi via QR (`WIFI:T:WPA;S:...`) or manual password, then open `http://<host-ip>:8085/` in any browser. OS captive-portal check (`generate_204` / `captive.apple.com`) auto-opens the chat like hotel Wi-Fi — or they can type the URL later. Text-only, 2000 chars, one host at a time.
* **One shared thread:** App peers ↔ host ↔ browser guests share **one** `messages` table. Host sends `PartyChatRepository.sendLocalMessage` → `onMessagePersisted` → `ChatWebServer` WebSocket broadcast; guest `ws://<host>/ws` `join`+`chat` → `receiveFromWebGuest` → Nearby relay.
* **QR identity:** Each member gets a persistent UUID. **My QR** shows it via ZXing; **Scan** adds them instantly — no typing, no mix-ups. Browser guests get a `localStorage` UUID + name (40 chars) → `PartyMember` `code=guestId`, `colorHex` from palette.
* **Broadcast or private:** **Everyone** or a specific shipmate (`targetCode`). Guests broadcast only in this pass.
* **Live, no refresh:** WebSocket `ws://<host>/ws` stays open (`timeout=0`, 25s `ping` keepalive, debounced `Offline` dot). Guest messages show instantly; host messages fan-out via `onMessagePersisted`. History backfill on `join`.
* **Reliable:** `PENDING → SENT → DELIVERED → READ` (app peers) with receipts, retry every 5 s; guest messages `DELIVERED` once broadcast (no `READ` for guests). Works after reboot for alarms; hotspot is explicit foreground `connectedDevice` service, not auto-resumed.
* **Organized UI:** Party screen now collapsible — *Connections & members* summary, *Bluetooth Nearby* (collapsed), *Guest Wi-Fi Chat*, *Party Members*, *Tools* + *How it works* above the chat, chat thread at bottom above composer.

### 📆 Calendar
* Material 3 `DatePickerDialog` + `DateRangePicker` with fine −1 d/+1 d nudge, midnight-normalized.

---

## 🚀 How It Works

1. **Create your cruise** — Name your ship, pick start/end on the calendar. We generate every day.
2. **Add ports & plans** — Search “Nassau” → Use → Add Port. Tap any day to add “Welcome Dinner 10:00 Main Dining”.
3. **Stay in sync offline** — Share your QR with your party. Chat over Nearby — no ship Wi-Fi needed. **Or** host **Guest Wi-Fi Chat**: tap *Start Guest Chat* → guests scan the `WIFI:` QR to join your hotspot, then the URL QR (or auto-open via captive portal `generate_204`) to chat in any browser — no app, no internet, live. Weather pulls once, then cached.

---

## 🔒 Privacy at a Glance

* **On-device only:** Cruises, ports, events, messages → Room DB `cruise_db` on your phone. We run no server for your data.
* **Firebase Crashlytics & Performance:** Anonymized crash traces + speed traces (enabled). User ID is a random UUID, not your email. See [Privacy Policy](public/privacy.html) for full details and opt-out.
* **Open-Meteo:** Weather queries go directly from your device to Open-Meteo (lat/lon + IP per their policy). Cached locally.
* **OpenStreetMap:** Port map tiles fetched directly from `tile.openstreetmap.org` (IP visible to OSM CDN). Cached offline. © OSM ODbL. Optional My Location stays on device.
* **Ship deck maps (optional):** Fleet catalog (`public/decks/manifest.json` + CC0 `*.webp`) served via Firebase Hosting / raw GitHub fallback. Download is optional; only the CDN sees your IP + shipId. Saved to `filesDir/decks/` for offline use.
* **Nearby:** P2P encrypted, no relay. Your QR contains only your display name + random code.

Full policy: [`/privacy`](https://cruise-app-2026.web.app/privacy) — also shipped as `public/privacy.html` for Firebase Hosting.

---

## 📲 Download

* **Play bundle:** `app/build/outputs/bundle/release/app-release.aab` — v1.0.0, Android 8+ (minSdk 26, targetSdk 36).
* **🌐 Website:** [https://cruise-app-2026.web.app](https://cruise-app-2026.web.app)
* **📄 Privacy Policy:** [https://cruise-app-2026.web.app/privacy](https://cruise-app-2026.web.app/privacy)

---

## 🛠️ Build from Source

> Requires your own Firebase project. The real `google-services.json` is **not** committed (see `.gitignore`). A template is provided.

```bash
# 1. Clone
git clone https://github.com/chartmann1590/cruise-app.git
cd cruise-app

# 2. Firebase setup
# Create a Firebase project, add an Android app with package com.charles.cruiseapp,
# download google-services.json and place it at:
#   app/google-services.json
# Or copy the template:
cp app/google-services.json.example app/google-services.json
# then fill in YOUR_PROJECT_ID / YOUR_API_KEY

# 3. Build
./gradlew assembleDebug

# 4. Install (generic device — replace with your device)
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.charles.cruiseapp/.MainActivity
```

<details>
<summary>Tech stack</summary>

 * **Language/UI:** Kotlin 1.9.22, Compose 1.5.8 (BOM 2024.10.00), Material 3, Navigation Compose 2.8.4, AGP 8.3.2
  * **Data:** Room 2.6.1, DataStore/SharedPreferences, `fallbackToDestructiveMigration()` (v3 → v1 deck catalog counts as file-cache, no DB bump needed)
  * **Network:** Retrofit 2.11, OkHttp 4.12, kotlinx.serialization 1.6.3, Open-Meteo (no key), **NanoHTTPD-WebSocket 2.3.1** for embedded `ChatWebServer` (`ws://<host>:8085/ws`, `http://<host>:8085/` static `hotspot_chat/` + `302` captive-portal for `generate_204`/`captive.apple.com`), **osmdroid 6.1.20** (OSM tiles, no key) + **Coil 2.6.0** for deck images
  * **Maps/Decks:** `public/decks/manifest.json` + `ships/<id>/deck-*.webp` (211 CC0 webp, ~7 MB total, 25 ships) served via Firebase Hosting + raw GitHub fallback + `assets/decks/manifest.json` offline fallback; `filesDir/decks/` + `filesDir/osmdroid/` for offline sea days; `DeckRepository` + `CountdownReceiver` (9 AM `AlarmManager`)
  * **Nearby/QR:** Play Services Nearby 19.3 (`P2P_CLUSTER`), ZXing 3.5.3 + zxing-android-embedded 4.3.0 (`WIFI:` QR + URL QR)
  * **Firebase:** BOM 32.7.4, Analytics + Crashlytics + Performance (restricted API key)
  * **Permissions:** INTERNET, POST_NOTIFICATIONS, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED, BLUETOOTH_SCAN/ADVERTISE/CONNECT, NEARBY_WIFI_DEVICES, ACCESS_WIFI_STATE/CHANGE_WIFI_STATE, FOREGROUND_SERVICE + `FOREGROUND_SERVICE_CONNECTED_DEVICE` (Android 14), **ACCESS_FINE/COARSE_LOCATION** (Port Map My Location, optional), CAMERA

Verify no secrets before pushing:

```bash
git diff --cached --stat
# should NOT show app/google-services.json
```

</details>

<details>
<summary>Project structure</summary>

```
com.charles.cruiseapp
 ├─ CruiseApplication (Room + WeatherRepository + PartyChatRepository + HotspotController + OSM init + 4 NotificationChannels incl. COUNTDOWN)
 ├─ data/local (Room v3: Cruise, PortStop, PlannedEvent, PartyMember, Message, WeatherCache)
 ├─ data/remote (OpenMeteoApi / GeocodingApi via Retrofit)
 ├─ data/decks (DeckRepository + DeckModels: manifest v1, 25 ships, CC0 webp, GitHub/Hosting fetch + filesDir/decks cache + assets/decks/manifest.json fallback)
 ├─ data/nearby (NearbyManager, WireMessage, retry)
 ├─ data/party (PartyChatRepository — app-scoped, Nearby relay + onMessagePersisted fan-out, guest upsert)
 ├─ data/hotspot (HotspotController LocalOnlyHotspot + ChatWebServer NanoWSD + HotspotChatService foreground connectedDevice)
 │   └─ assets/hotspot_chat/{index.html, styles.css, chat.js} (system-font, prefers-color-scheme, ws://<host>/ws)
 │   └─ assets/decks/manifest.json (offline fallback) + public/decks/ships/<id>/deck-*.webp (CC0, Firebase Hosting)
 ├─ notifications (AlarmManager + Receivers + HOTSPOT_CHANNEL_ID + COUNTDOWN_CHANNEL_ID + CountdownReceiver 9AM daily with weather snippet)
 ├─ ui/theme, navigation (Dashboard, CruiseSetup, DayDetail, PortList, PortMap, ShipCatalog, ShipDeck, Weather, Party), components/WeatherCard, screens/* (Dashboard countdown card, PortMapScreen osmdroid, ShipCatalogScreen, ShipDeckScreen pinch-zoom pager)
 └─ util (DateUtils, WmoCodes, QrUtils, FirebaseUtils)
```

</details>

---

## 🤝 Contributing

Issues and PRs welcome! Please:

1. Open an issue describing the change.
2. Fork → branch → ` ./gradlew assembleDebug` should pass.
3. Do **not** commit `app/google-services.json`, keystores (`*.jks`, `*.keystore`) or `.env` files.

See [SECURITY.md](SECURITY.md) for reporting vulnerabilities.

---

## 📄 License

MIT — see [LICENSE](LICENSE). Weather data © Open-Meteo CC BY 4.0. Icons via Material, fonts Inter + Plus Jakarta Sans.

Firebase project `cruise-app-2026` and hosting `cruise-app-2026.web.app` are for the official build only — forkers should use their own Firebase project.

---

## 📮 Contact

* **🌐 Website:** [https://cruise-app-2026.web.app](https://cruise-app-2026.web.app)
* **📄 Privacy Policy:** [https://cruise-app-2026.web.app/privacy](https://cruise-app-2026.web.app/privacy)
* **GitHub Issues:** [chartmann1590/cruise-app/issues](https://github.com/chartmann1590/cruise-app/issues)

© 2026 CruiseLoom • `com.charles.cruiseapp` • Built offline, for offline.
