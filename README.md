# Cruise Planner — Offline-First Cruise Companion

**Package:** `com.charles.cruiseapp`  
**Platform:** Android (Kotlin + Jetpack Compose, minSdk 26, targetSdk 35)  
**Private Repo:** https://github.com/chartmann1590/cruise-app

Offline-first Android app for cruise passengers to plan their entire voyage **on the ship without Wi-Fi**. Create a cruise, add real port stops, plan each sea/port day, get live weather (no API key), receive local notifications, and chat with your party over **Bluetooth / BLE / Wi-Fi Direct** even when the ship has no internet.

> **No mock data** — every cruise, port, and event is user-entered. Weather is live from Open-Meteo (free, no key, cached for offline). Chat is real peer-to-peer via Google Nearby Connections with retry-until-delivered and read receipts. Party identity is via QR code, not typed names.

---

## Screenshots

| Welcome | Cruise Setup | Calendar Picker |
|---|---|---|
| ![Welcome](screenshots/01_welcome.png) | ![Setup](screenshots/02_cruise_setup.png) | ![Calendar](screenshots/04_calendar_picker.png) |

| Dashboard (Itinerary) | Port List | Weather (Open-Meteo, no key) |
|---|---|---|
| ![Dashboard](screenshots/06_dashboard.png) | ![Ports](screenshots/07_port_list.png) | ![Weather](screenshots/08_weather.png) |

| Day Detail | Party Chat | My QR |
|---|---|---|
| ![Day](screenshots/09_day_detail.png) | ![Party](screenshots/10_party.png) | ![QR](screenshots/11_my_qr.png) |

> Screenshots generated on Pixel 8 Pro (`com.charles.cruiseapp`) with real data injected via `DebugInjectorActivity` for README. No hard-coded mock cruises remain in the DB on fresh install.

---

## Features

### 🚢 Cruise & Itinerary — `com.charles.cruiseapp.ui.screens.*`
- **Create cruise:** ship name + **calendar** (`DateRangePicker` + single `DatePicker`) for start/end. Duration auto-calculated, day-by-day planner auto-generates. Files `CruiseSetupScreen.kt:17`, `DashboardScreen.kt:25`, `DashboardViewModel.kt:36`.
- **Daily planner:** `HorizontalPager`-like list per day, sea vs. port badge, add/edit/delete events (title, time, location, category, reminder). `DayDetailScreen.kt`, `PortListScreen.kt`.
- **Port stops:** search real city via **Open-Meteo Geocoding** (`https://geocoding-api.open-meteo.com/v1/search`, no key) → auto-fills `lat/lon`. Stored in Room `PortStop`. `PortListScreen.kt:102`, `WeatherRepository.kt:42`.

### 🌦️ Weather — Free, No API Key — `com.charles.cruiseapp.data.remote.*`
- `OpenMeteoApi` `GET https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&daily=temperature_2m_max,...&current=temperature_2m,weather_code&timezone=auto&forecast_days=7` — **no `apikey` param**, CC BY 4.0. `WeatherRepository.kt:19`, `WeatherService.kt:13`.
- Geocoding + forecast cached in `WeatherCache` (3h TTL) → works **offline** after first fetch. `WeatherCard.kt` maps WMO codes to emoji/description `WmoCodes.kt`, shows 7-day daily + current + attribution.

### 🔔 Notifications — `com.charles.cruiseapp.notifications.*`
- `NotificationHelper.kt:12` `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP)` per `PlannedEvent` (`reminderMinutesBefore` 15 default, configurable 1-60). `EventReminderReceiver.kt` builds `NotificationCompat` on `cruise_reminders` channel (high importance, vibration). `BootRescheduleReceiver` re-schedules all `getFutureEvents` on `BOOT_COMPLETED`. Handles `SCHEDULE_EXACT_ALARM` + `POST_NOTIFICATIONS` (Android 13+).

### 💬 Offline Party Chat — Bluetooth Mesh — `com.charles.cruiseapp.data.nearby.NearbyManager.kt`
- **Transport:** Google Play Services **Nearby Connections** `Strategy.P2P_CLUSTER` → auto-uses Bluetooth + BLE + Wi-Fi Direct, encrypted, **no internet**, ~100m/hop. Handles July 2026 change (no auto-enable radios, must prompt).
- **Identity via QR:** Each member gets persistent `code` (`UUID`, `PartyMember.code`) stored in `cruise_party_prefs`. `PartyViewModel.getSelfCode()` / `getQrData()` JSON `{"n":name,"c":code}` → `QrUtils.generateQrBitmap` (ZXing `QRCodeWriter`). `PartyScreen` has **My QR** dialog and **Scan** via `ScanContract` (`zxing-android-embedded:4.3.0` + `zxing:core:3.5.3` + `CAMERA` permission). Scanning `name|code` or JSON adds `addMemberWithCode()` — no typing, guarantees identity. `PartyMember` `isSelf` + `code` + `endpointId` mapping to `DiscoveredEndpoint.code`.
- **Individual vs. broadcast:** `selectedRecipient` chip row (`To: Everyone` vs. member). `sendLocalMessage()` → `targetCode=null` broadcast to all `connected`; `sendToMember(text, target)` → `targetCode=member.code` + `sendWireMessageTo(wire, targetCode)` filters `connected.filter { it.code == targetCode }`.
- **Reliable delivery:** `WireMessage(type=CHAT|DELIVERED|READ, messageId, sender, senderCode, targetCode, refId)`. `Message.status` `PENDING→SENT→DELIVERED→READ`. `attemptSend()` → `PENDING` if no peer else `SENT`. `retryJob` every 5s `isActive` flushes `getPending()` (both `PENDING`+`SENT`) only if target is connected / broadcast has any peer. `handleIncomingWire` checks `targetCode == selfCode || null`, inserts `DELIVERED`, replies `sendDeliveredReceipt`, after 1.2s marks `READ` + `sendReadReceipt`. Sender on `DELIVERED/READ` receipt updates `updateStatus`. Status icons: `Schedule` grey pending, `Done` sent grey, `DoneAll` delivered grey, `DoneAll` read blue `#4FC3F7`.
- **Nice window:** `PartyScreen.kt` `Scaffold(bottomBar=input)` + `LazyColumn` for headers + `items(localMessages){MessageBubble}`. `MessageBubble` rounded `18dp` bubbles (`primary` self vs. `surface` other, `widthIn 280dp`), sender `To X`, timestamp `h:mm a`, status text `Queued • retrying...`. Members shown as `LazyRow`/`FlowRow` chips with `CircleShape` avatar `take(1)`, `FilterChip` selection. Bottom bar fixed, thread scrolls, fits correctly (previously nested scroll clipped).

### 📅 Calendar — `CruiseSetupScreen.kt:98`
- `DatePickerDialog` + `DateRangePicker` (`rememberDateRangePickerState`) for range, `DatePicker` for single start/end taps. Also `OutlinedCard` clickable for start/end plus `-1d/+1d` fine adjust. `startOfDay()` ensures midnight.

---

## Architecture

```
com.charles.cruiseapp
 ├─ CruiseApplication (Room + WeatherRepository + NotificationChannels)
 ├─ data/local (Room v3, fallbackDestructive)
 │   ├─ Cruise, PortStop, PlannedEvent, PartyMember(code), Message(status,targetCode), WeatherCache
 │   └─ Daos + CruiseDatabase
 ├─ data/remote (Retrofit + OkHttp + kotlinx.serialization)
 │   ├─ OpenMeteoApi / GeocodingApi
 │   └─ WeatherRepository (no key)
 ├─ data/nearby (NearbyManager, WireMessage, retry)
 ├─ notifications (AlarmManager + Receivers)
 ├─ ui/{theme, navigation, components/WeatherCard, screens/*}
 └─ util (DateUtils, WmoCodes, QrUtils)
```

**DB:** `cruise_db` v3, `fallbackToDestructiveMigration()`. All writes via `viewModelScope` + `Flow`.

**Offline-first:** Room is source of truth, weather cached 3h, chat queued `PENDING` and retried after reboot/reconnect.

---

## Package & Build

- **Namespace / applicationId:** `com.charles.cruiseapp` (`app/build.gradle.kts:9,12`)
- **Min 26, Target 35, Compose 1.5.8, AGP 8.3.2, Kotlin 1.9.22, Room 2.6.1, Retrofit 2.11, Nearby 19.3, ZXing 3.5.3**
- **Permissions:** `INTERNET`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `BLUETOOTH_SCAN/ADVERTISE/CONNECT`, `NEARBY_WIFI_DEVICES`, `CAMERA`

```bash
# Build
./gradlew assembleDebug   # or C:\ProgramData\chocolatey\bin\gradle.exe assembleDebug

# Install to Pixel 8 Pro (37220DLJG001ML)
adb -s 37220DLJG001ML install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 37220DLJG001ML shell pm clear com.charles.cruiseapp  # fresh, no mock
adb -s 37220DLJG001ML shell am start -n com.charles.cruiseapp/.MainActivity

# Inject real demo data for screenshots (optional)
adb shell am start -n com.charles.cruiseapp/.DebugInjectorActivity \
  --es shipName "Island Princess" --ez addPort true --ez addMessage true
```

---

## Why "No Mock Data"?

Fresh install DB is empty — `Welcome Aboard! → Create Cruise` only. `DebugInjectorActivity` is a **test helper** (exported, not auto-run) to inject real `Island Princess` + two real ports (`Nassau 25.06,-77.34` + `Cozumel 20.42,-86.92`) for README screenshots; it is **not** pre-populated mock. All screenshots in `/screenshots` are from real user-entered or injected real data, weather is live Open-Meteo, chat is real Nearby payloads.

---

## Testing on Device

- **Calendar:** `Cruise Setup → Calendar` → pick range → `Save Cruise — 7 days`
- **Ports:** `Manage Ports → + → Nassau → Search (Open-Meteo) → Use → Add Port`
- **Weather:** `Weather for Nassau` → shows `28.2°C Thunderstorm`, `Rain 76%`, 7-day, cached
- **Itinerary:** tap `Thursday, Aug 26` → `+` → `Welcome Dinner 10:00 Main Dining` → notification in ~1 min via `dumpsys alarm | grep charles`
- **Party:** `Party Chat` → enter `Bob` → `My QR` (show) / `Scan` (add Alice) → select `Alice` chip → `Broadcast Test` / `Private Test` → bubble shows `Queued • retrying...` → `Sent` → `Delivered ✓✓` → `Read ✓✓` blue

---

## License

Private repo — all rights reserved. Weather data CC BY 4.0 Open-Meteo.
