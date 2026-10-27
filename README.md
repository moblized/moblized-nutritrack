# NutriTrack

A Cronometer-style nutrition tracker for Android that works offline. Built with Kotlin and Jetpack Compose.

## Features

- **Detailed nutrition**: 70+ nutrients per food, including every vitamin and mineral with a US Dietary Reference Intake, omega-3/omega-6, and all amino acids.
- **Offline food database**: ~15,000 foods from USDA FoodData Central (Foundation Foods, SR Legacy, FNDDS) are bundled in the app. Search works with no connection.
- **Personal targets**: calorie target from your profile (Mifflin-St Jeor × activity, adjusted for your goal), macro split, and DRI targets by age and sex. Any target can be overridden. Upper limits are flagged.
- **Barcode scanner**: packaged foods via Open Food Facts. Products that aren't found can be entered from the label and are remembered by barcode.
- **Recipes**: combine foods, set servings, log a serving.
- **Health Connect**: optional sync of every logged food (calories, macros, vitamins, minerals) so Samsung Health and other apps can see it.
- **Home-screen widget**: today's calories, macros and the three lowest key nutrients.
- **Dark mode** with optional pure black, and the launcher icon switches to a dark version along with it.
- Day, 7-day and 30-day nutrient reports.

## Install

Every push to `main` builds a signed APK. Download it from the [Releases](../../releases) page on your phone and install it (allow "Install unknown apps" for your browser the first time). Updates install over the previous version because every build uses the same signing key in `keystore/`.

## How the build works

`.github/workflows/build.yml`:

1. `tools/build_food_db.py` downloads the USDA CSV releases, keeps the nutrients the app tracks, normalizes units and writes `app/src/main/assets/usda.db` (SQLite with FTS4 search).
2. Gradle builds and signs the release APK.
3. The APK is attached to a GitHub release.

To build locally you need JDK 17 and the Android SDK:

```sh
python3 tools/build_food_db.py --out app/src/main/assets/usda.db
./gradlew assembleRelease
```

## Data sources

- USDA FoodData Central: public domain.
- Open Food Facts: Open Database License (ODbL). Barcode lookups are the only network requests the app makes.
