# CruiseLoom Play Console release checklist

## Store identity

- App name: **CruiseLoom**
- Package/application ID: **com.charles.cruiseapp**
- Default language: **English (United States)**
- App or game: **App**
- Category: **Travel & Local**
- Contains ads: **Yes**
- Target audience: **13 and older**; the app is not designed for children
- Privacy policy: `https://cruise-app-2026.web.app/privacy`
- Website: `https://cruise-app-2026.web.app`

## Upload-ready local artifacts

- Signed bundle output: `app/build/outputs/bundle/release/app-release.aab`
- Upload certificate: `play-store/upload_certificate.pem`
- Play icon: `fastlane/metadata/android/en-US/images/icon.png` (512×512, RGB PNG)
- Feature graphic: `fastlane/metadata/android/en-US/images/featureGraphic.png` (1024×500, RGB PNG)
- Phone screenshots: `fastlane/metadata/android/en-US/images/phoneScreenshots/` (8 images, 1262×2244, RGB PNG)
- Promo master: `public/promo.mp4` (1920×1080 H.264/AAC, voiceover, burned-in captions)
- Captions sidecar: `promo/subs.srt`
- Store copy: `fastlane/metadata/android/en-US/`
- Data safety draft: `play-store/DATA_SAFETY.md`

## Console-only steps that require the owner account

1. Create the app in Play Console with package `com.charles.cruiseapp` and accept Play App Signing.
2. Enter a monitored support email and developer contact details.
3. Complete Data safety using `DATA_SAFETY.md` and declare **Contains ads**.
4. Complete App access (all core features are accessible without an account), content rating, target audience, and advertising-ID declarations.
5. In AdMob Privacy & messaging, publish the GDPR and applicable US-state messages used by the integrated UMP SDK, and enable consent mode as appropriate.
6. Upload `public/promo.mp4` to a public or unlisted, monetization-disabled YouTube video, then paste its standard YouTube URL into the store listing. Google Play does not accept a direct MP4 upload for preview video.
7. Upload the AAB to Internal testing first, review the pre-launch report, then promote after testing.

Never upload a bundle built with Google test ad IDs. The release build intentionally fails if production ad configuration is missing.

## Upload-key custody

- Back up `app/upload-keystore.jks` and `app/keystore.properties` together in a secure password manager or encrypted vault. Both are intentionally git-ignored.
- Upload certificate SHA-1: `43:9A:84:B8:E2:DF:BF:2A:F0:58:98:A9:ED:F4:40:7C:6B:D8:06:5F`
- Upload certificate SHA-256: `34:D2:E8:5A:56:B5:75:6D:DE:DC:B2:4D:B2:07:E4:7E:C5:3E:09:EA:C8:5D:65:13:63:71:29:59:38:47:6F:25`
