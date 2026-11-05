# Cycle Clock

A light, offline sleep-cycle calculator for iPhone, iPad and Android, in English and فارسی.

- **Wake up at**: bedtimes that end on a full sleep cycle
- **Sleep now**: wake-up times if you go to bed now (or in 10 to 30 minutes)
- **Sleep for**: a wake-up time for any sleep length, with a warning if it lands mid-cycle
- **One-tap alarms** with optional reminders (how many, how far apart), each saying how much time is left
- Two simple settings: minutes to fall asleep (or none) and sleep cycle length

**iPhone / iPad**: open https://naamimaalek.github.io/cycle-clock/ in Safari, then Share → Add to Home Screen. Open it once from the Home Screen while online and it works offline after that. Alarms use a small Shortcuts shortcut; the app shows the setup steps.

**Android**: install https://github.com/naamimaalek/cycle-clock/releases/latest/download/cycle-clock.apk. Alarms go straight into the phone's Clock app; the app confirms the next alarm, raises a too-low alarm volume and warns about Do Not Disturb on total silence.

## Changing the app

Edit `src/app.html`, then run `python3 build.py`. That rebuilds `index.html`, the offline files and the icons. Bump `VERSION` in `build.py` for every release so installed copies update. Pushing to `main` publishes the site and GitHub Actions rebuilds the Android app (`.github/workflows/android.yml`).
