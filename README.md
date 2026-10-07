# flagser. — native Android

This is a from-scratch Kotlin + Jetpack Compose Android port using the supplied HTML as the wireframe and gameplay reference. It does not use WebView or embed the HTML.

Included natively:
- menu, modes, unlocks, study, settings, game and result screens
- 249-country flag pool
- classic, reverse, capital and capital-mixed modes
- standard / medium / hardcore
- international, domestic and mixed phone-code modes
- points, checkpoints, cash-out and persistent unlocks
- Helpers (shield, heart, double down, skip, peek, anchor, gamble, insurance, rush, longshot, perfect)
- study guides and flashcards
- dark mode, accent unlocks and slider selection
- custom domestic phone codes
- local persistent save data

## Easiest way on Windows
1. Install/open Android Studio once so the Android SDK is installed.
2. Open this `flagser-android` folder in Android Studio.
3. Let Gradle sync/download dependencies.
4. Press the green Run button with your Nothing Phone 2 connected, **or** double-click `build-apk.bat`.
5. `build-apk.bat` creates `flagser-debug.apk` in the project folder.

The project targets Android API 35 and supports Android 8.0+ (minSdk 26).

### Notes
The original HTML loaded flag PNGs from FlagCDN. This native port renders ISO flags with Android's system flag emoji, so gameplay doesn't need FlagCDN/network image loading.
