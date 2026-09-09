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

There is no upload endpoint or version history — replacing the file here is the
whole release process. The directory itself (and its path) is configurable via
the `APP_RELEASES_PATH` backend `.env` variable, default `app-releases`.
