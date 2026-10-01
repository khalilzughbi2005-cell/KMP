# Mounjaro Log

A local-first Android app for personally logging weight, Mounjaro injection dates and optional daily notes.

## v1.0 feature set

### Fast logging
- Quick entry directly on Home
- Calendar date only — no unnecessary time-of-day precision
- Weight and injection are independent records
- Weight-only, dose-only and combined entries are supported
- Dose choices: 2.5, 5, 7.5, 10, 12.5 and 15 mg
- Optional injection site, appetite self-rating, side-effect/symptom text and notes

### Dose-window logic
- Injection date is day 1
- The dose context covers seven calendar dates: injection date through six days after it
- A new injection replaces the previous context from its own date onward
- 2.5 mg is shown in grey
- Measurements outside an active seven-day window are shown in grey
- Dose context is presented descriptively and is not treated as proof that a dose caused a weight change

### Progress
- Weight graph
- Weight-lost graph
- Percent-lost graph
- Weekly-rate graph
- Step-style dose graph
- Ranges: 1 month, 2 months, 3 months, 6 months, 1 year, all time and custom start/end
- Dose-window background bands fade upward from the x-axis
- Injection and day-7 boundary posts
- Dotted diagonal lines across periods without measurements
- Dotted horizontal continuation after the latest known measurement
- Tap a graph point to inspect its date/value
- Toggle dose bands, injection posts and milestone lines

### Calendar & history
- Month calendar with markers for weights, injections and daily notes
- Tap a day to inspect or edit its records
- Combined history with filters for weights, injections and notes
- Editing is in-place; deleting requires confirmation

### Stats & milestones
- Starting, latest and lowest recorded weight
- Total kg and percentage change
- Average weekly change
- Current dose run
- Per-dose descriptive breakdown
- User-defined target-weight milestones shown on the weight graph

### Data safety & privacy
- Persistent SQLite database in Android app-private storage
- Normal app updates preserve the database
- No Internet permission
- Android automatic cloud backup is disabled
- Five rotating local JSON snapshots are written after data changes
- Full JSON export and restore through Android's document picker
- CSV export for external analysis
- Restore validates the backup and snapshots the current dataset before replacement

> Android uninstallation can remove app-private data. Export a JSON backup before uninstalling or changing phones.

## Build

The project targets Android API 35, minimum API 26, Java 17 and Gradle 8.9.

CI runs:

    gradle :app:testDebugUnitTest :app:assembleDebug

A successful GitHub Actions run uploads **mounjaro-log-debug-apk** as an installable debug APK artifact.

## Development

Active development is on **feature/mounjaro-logger** in draft PR #1 until the v1.0 build and tests are green.
