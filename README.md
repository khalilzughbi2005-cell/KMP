# Mounjaro Log

Android-first personal logger for weight and Mounjaro injection history.

## Current first milestone

- Date-only weight and injection records
- Quick entry with optional weight and optional dose
- Supported dose steps: 2.5, 5, 7.5, 10, 12.5 and 15 mg
- Seven-calendar-day dose context
- 2.5 mg and out-of-window measurements rendered grey
- Dose-coloured graph windows with injection/end posts
- Dotted diagonal graph segments across unmeasured intervals
- Dotted horizontal continuation after the latest measurement
- Progress ranges: 1M, 2M, 3M, 6M, 1Y, All and custom start date
- Weight, change, weekly-rate and dose graph modes
- Calendar, history and stats screens
- Persistent SQLite database in Android app data
- Non-destructive schema-upgrade policy
- Rotating local JSON snapshots after data mutations

Normal Android app updates preserve the private database. An uninstall can remove app-private data, so explicit export/restore is planned as a follow-up.

## Branch

Active development currently lives on `feature/mounjaro-logger`.
