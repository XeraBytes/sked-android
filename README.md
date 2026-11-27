# Sked

A clean, offline-first class timetable for Android.

Sked is built around three everyday questions for students: *when is my next class*, *where does an unscheduled course fit*, and *how do I get my timetable in without typing everything by hand*. Everything stays on your device, with a warm paper-style schedule that works in both light and dark mode.

## What You Can Do

- **See your day or your whole week** — Switch between a focused daily timeline and a full weekly grid that keeps overlapping classes legible side by side.
- **Keep an unscheduled course pool** — Park courses you haven't finalized yet in a pool, place them into open slots with conflict checks, or let Sked arrange free times for you.
- **Import instead of retyping** — Bring in classes from an image, PDF, or pasted text (using your own Gemini API key), keep an offline reference photo of your timetable, or sync directly from CCC e-GURO.
- **Export when you need a copy** — Save your schedule as a PDF or image to share or print.
- **Stay up to date on your terms** — Built-in GitHub Releases checks let you install updates right in the app, remind you later, or skip a version.

## Download

Grab the latest APK from [Releases](https://github.com/XeraBytes/sked-android/releases).

## Building from Source

Open the project in Android Studio (JDK 21) and run on an emulator or device, or build with Gradle:

```bash
./gradlew :app:assembleDebug
```

If you want to bundle a default Gemini key at build time for timetable extraction, copy `.env.example` to `.env` and add `GEMINI_API_KEY=...` (otherwise you can add one anytime inside the app's settings).
