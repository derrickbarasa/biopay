<script setup lang="ts">
const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:7730/biopay'

const faceBuild = {
  file: 'biopay-agent-morpho642.apk',
  minAndroid: 'Android 7.0 (Nougat) or newer',
}

const builds = [
  {
    key: 'morpho642',
    title: 'Morpho MSO 1350 / 6.42',
    subtitle: 'For field devices using the Morpho 6.42 fingerprint scanner SDK',
    file: 'biopay-agent-morpho642.apk',
    minAndroid: 'Android 7.0 (Nougat) or newer',
    verification: 'Fingerprint and face verification',
    note: null,
    deviceImage: '/devices/android7-morpho642-cutout.png',
    deviceAlt: 'IDEMIA MSO 1350 handheld fingerprint scanner device',
  },
  {
    key: 'morpho615',
    title: 'Morpho 6.15',
    subtitle: 'For field devices using the Morpho 6.15 fingerprint scanner SDK',
    file: 'biopay-agent-morpho615.apk',
    minAndroid: 'Android 5.0 (Lollipop) or newer',
    verification: 'Fingerprint and face verification',
    note: null,
    deviceImage: '/devices/android5-morpho615-cutout.png',
    deviceAlt: 'Safran Morpho tablet with fingerprint scanner',
  },
]

function downloadUrl(file: string) {
  return `${baseUrl}/downloads/${file}`
}

const installSteps = [
  'On the Android device, open this page (or tap the link you shared). Choose Face capture & verification for a device with a back camera, or select the biometric build that matches its fingerprint scanner and Android version.',
  'When the download finishes, tap the file. Android will warn that installing from this source is not usually allowed — tap Settings, then turn on "Allow from this source" for your browser, and go back.',
  'Tap Install, then Open once installation finishes.',
  'Sign in with the field officer account created for this device under Field Officers.',
  'On first sign-in, let the app finish its initial sync before going to the field — this pulls the households, payments and vouchers that officer can see.',
]

const usageNotes = [
  { title: 'Register households', body: 'Capture household details, GPS location, and biometrics (fingerprint and/or face) for enrollment.' },
  { title: 'Verify and pay', body: 'Look up a household or alternate, verify their fingerprint or face, and record a field payment — works offline and syncs once back online.' },
  { title: 'Redeem vouchers', body: 'Search issued vouchers by household, verify identity, and mark them redeemed.' },
  { title: 'Attendance', body: 'Record officer attendance for the day.' },
  { title: 'Offline-first', body: 'All the above works without a live connection; a background sync pushes queued records and pulls the latest data whenever the device is online.' },
]
</script>

<template>
  <div>
    <div class="mb-4">
      <h1 class="page-title">Android App</h1>
      <p class="text-caption text-medium-emphasis mb-0">
        Download the BioPay field agent app to as many devices as you need, and see how to install and use it.
      </p>
    </div>

    <v-card variant="flat" border class="mb-4">
      <v-card-text>
        <h2 class="text-subtitle-1 font-weight-bold mb-3">Download</h2>

        <section aria-labelledby="face-download-heading">
          <h3 id="face-download-heading" class="text-body-2 font-weight-bold mb-2">Face capture &amp; verification</h3>
          <v-card variant="tonal" color="secondary" class="face-build-card">
            <div class="face-media">
              <img
                src="/devices/face-verification-android-cutout.png"
                alt="Android phone displaying a successful back-camera face verification"
                class="face-device-image"
              />
            </div>
            <div class="face-build-copy">
              <div class="d-flex align-center flex-wrap ga-2 mb-1">
                <div class="text-subtitle-2 font-weight-bold">Any Android phone or tablet with a back camera</div>
                <v-chip color="success" size="x-small" variant="tonal">Back camera</v-chip>
              </div>
              <div class="text-caption mb-3">
                Capture and verify another person's face using the Android device's back camera. No fingerprint scanner is required.
              </div>
              <div class="d-flex align-start text-caption mb-3">
                <v-icon icon="mdi-android" color="success" size="17" class="mr-1 mt-1" />
                <span><strong>Requires:</strong> {{ faceBuild.minAndroid }}</span>
              </div>
              <v-btn
                color="secondary"
                variant="flat"
                prepend-icon="mdi-download"
                :href="downloadUrl(faceBuild.file)"
                target="_blank"
                rel="noopener"
              >
                Download APK
              </v-btn>
            </div>
          </v-card>
        </section>

        <section aria-labelledby="biometric-download-heading" class="mt-5">
          <h3 id="biometric-download-heading" class="text-body-2 font-weight-bold mb-1">Biometrics</h3>
          <p class="text-caption text-medium-emphasis mb-2">
            Choose the build that matches the device's fingerprint scanner.
          </p>
          <v-row dense>
            <v-col v-for="b in builds" :key="b.key" cols="12" md="6">
            <v-card variant="tonal" color="secondary" class="build-card h-100">
              <div class="device-media">
                <img :src="b.deviceImage" :alt="b.deviceAlt" class="device-glyph" />
              </div>
              <div class="build-copy">
                <div class="d-flex align-center mb-1">
                    <v-icon icon="mdi-android" size="24" class="mr-2" />
                    <div class="text-subtitle-2 font-weight-bold">{{ b.title }}</div>
                </div>
                <div class="text-caption mb-3">{{ b.subtitle }}</div>
                <div class="d-flex align-start text-caption mb-1">
                  <v-icon icon="mdi-android" color="success" size="17" class="mr-1 mt-1" />
                  <span><strong>Requires:</strong> {{ b.minAndroid }}</span>
                </div>
                <div class="d-flex align-start text-caption mb-3 flex-grow-1">
                  <v-icon icon="mdi-fingerprint" size="16" class="mr-1 mt-1" />
                  <span><strong>Verification:</strong> {{ b.verification }}</span>
                </div>
                <v-alert v-if="b.note" type="warning" variant="tonal" density="compact" class="mb-3 text-caption">
                  {{ b.note }}
                </v-alert>
                <v-btn color="secondary" variant="flat" prepend-icon="mdi-download" :href="downloadUrl(b.file)" target="_blank" rel="noopener" block>
                  Download APK
                </v-btn>
              </div>
            </v-card>
            </v-col>
          </v-row>
        </section>
        <v-alert type="info" variant="tonal" density="compact" class="mt-3">
          Not sure which one your device needs? Check the fingerprint scanner attached to the device (or ask
          whoever provisioned it) — installing the wrong build will fail to detect the scanner. If the device is
          an older tablet on Android 5 or 6, it can only take the Morpho 6.15 build; Android will refuse to
          install the 6.42 build at all on those devices.
        </v-alert>
        <v-alert type="info" variant="tonal" density="compact" class="mt-3">
          These APKs are large downloads (roughly 80MB for Morpho 6.15, 100MB for Morpho 6.42) — the on-device
          face and fingerprint recognition models, bundled so verification works fully offline in the field,
          account for a good part of that. Make sure the device has a good connection and enough free storage
          before installing.
        </v-alert>
      </v-card-text>
    </v-card>

    <v-card variant="flat" border class="mb-4">
      <v-card-text>
        <h2 class="text-subtitle-1 font-weight-bold mb-3">How to install</h2>
        <v-list density="compact">
          <v-list-item v-for="(step, i) in installSteps" :key="i" class="px-0">
            <template #prepend>
              <v-avatar size="24" color="secondary" class="mr-2"><span class="text-caption text-white">{{ i + 1 }}</span></v-avatar>
            </template>
            <v-list-item-title class="text-body-2 text-wrap">{{ step }}</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-card-text>
    </v-card>

    <v-card variant="flat" border>
      <v-card-text>
        <h2 class="text-subtitle-1 font-weight-bold mb-3">What the app does</h2>
        <v-row dense>
          <v-col v-for="n in usageNotes" :key="n.title" cols="12" md="6">
            <div class="pa-3 usage-note">
              <div class="text-body-2 font-weight-bold mb-1">{{ n.title }}</div>
              <div class="text-caption text-medium-emphasis">{{ n.body }}</div>
            </div>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>
  </div>
</template>

<style scoped>
.usage-note { border: 1px solid rgba(0, 0, 0, .08); border-radius: 8px; height: 100%; }
.face-build-card {
  display: grid;
  grid-template-columns: minmax(148px, 24%) minmax(0, 1fr);
  min-height: 188px;
  overflow: hidden;
}
.face-media {
  display: grid;
  place-items: center;
  min-width: 0;
  overflow: hidden;
  background: rgba(var(--v-theme-secondary), .08);
}
.face-device-image {
  display: block;
  width: 230px;
  max-width: none;
  height: 230px;
  object-fit: contain;
  filter: drop-shadow(0 5px 10px rgba(15, 23, 42, .24));
}
.face-build-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  min-width: 0;
  padding: 20px;
}
.build-card {
  display: grid;
  grid-template-columns: minmax(132px, 34%) minmax(0, 1fr);
  min-height: 232px;
  overflow: hidden;
}
.device-media {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  padding: 16px 8px 16px 16px;
}
.device-glyph {
  display: block;
  width: 100%;
  max-width: 220px;
  height: 200px;
  object-fit: contain;
  filter: drop-shadow(0 4px 8px rgba(15, 23, 42, .22));
}
.build-copy {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 16px 16px 16px 8px;
}

@media (max-width: 599px) {
  .face-build-card { grid-template-columns: 104px minmax(0, 1fr); }
  .face-device-image { width: 170px; height: 170px; }
  .face-build-copy { padding: 16px 12px; }
  .build-card { grid-template-columns: 120px minmax(0, 1fr); }
  .device-media { padding: 12px 8px 12px 12px; }
  .device-glyph { height: 176px; }
  .build-copy { padding-left: 4px; }
}
</style>
