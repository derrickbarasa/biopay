# Android agent releases

Drop freshly built agent APKs here with these exact filenames — the dashboard's
"Android App" page and the public `/biopay/downloads/:filename` route both link
to these names directly:

- `biopay-agent-morpho642.apk` — `assembleMorphoSmart642Release` (or debug) build
- `biopay-agent-morpho615.apk` — `assembleMorphoSmart615Release` (or debug) build

Build from `mobile/agent/`, e.g.:

```
./gradlew assembleMorphoSmart642Release
cp app/build/outputs/apk/morphoSmart642/release/app-morphoSmart642-release.apk \
   ../../backend/app-releases/biopay-agent-morpho642.apk
```

There is no upload endpoint — replacing the file here is the whole release
process. The directory itself (and its path) is configurable via the
`APP_RELEASES_PATH` backend `.env` variable, default `app-releases`.

## Publishing an update the installed app will notice

The app polls `/biopay/downloads/version/:filename` on its own (see
`AppUpdateManager` on the mobile side) to learn a newer build exists. To make
a drop-in APK show up as an update, also drop a `<filename>.apk.version.json`
sidecar next to it:

```
biopay-agent-morpho642.apk.version.json
```
```json
{
  "versionCode": 3,
  "versionName": "1.2",
  "notes": "Fixes payment sync retry bug"
}
```

`versionCode` must match (or exceed) the `versionCode` set in
`mobile/agent/app/build.gradle.kts` for that build — the app only offers the
update when the published `versionCode` is greater than its own. Skipping
this file is fine; it just means installed apps won't be told about the new
build (they'll only get it if reinstalled from the dashboard's Android App
page).
