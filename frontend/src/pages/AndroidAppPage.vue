<script setup lang="ts">
const baseUrl = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? 'http://localhost:7730/biopay'

const builds = [
  {
    key: 'morpho642',
    title: 'Morpho MSO 1350 / 6.42',
    subtitle: 'For field devices using the Morpho 6.42 fingerprint scanner SDK',
    file: 'biopay-agent-morpho642.apk',
    minAndroid: 'Android 7.0 (Nougat) or newer',
    verification: 'Fingerprint and face verification',
    note: null,
  },
  {
    key: 'morpho615',
    title: 'Morpho 6.15',
    subtitle: 'For field devices using the Morpho 6.15 fingerprint scanner SDK',
    file: 'biopay-agent-morpho615.apk',
    minAndroid: 'Android 5.0 (Lollipop) or newer',
    verification: 'Fingerprint and face verification',
    note: null,
  },
]

function downloadUrl(file: string) {
  return `${baseUrl}/downloads/${file}`
}

const installSteps = [
  'On the Android device, open this page (or tap the link you shared) and choose the download that matches its fingerprint scanner and Android version — see the "Requires" line on each card above.',
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
      <h1 class="text-h5 font-weight-bold">Android App</h1>
      <p class="text-caption text-medium-emphasis mb-0">
        Download the BioPay field agent app to as many devices as you need, and see how to install and use it.
      </p>
    </div>

    <v-card variant="flat" border class="mb-4">
      <v-card-text>
        <h2 class="text-subtitle-1 font-weight-bold mb-3">Download</h2>
        <v-row dense>
          <v-col v-for="b in builds" :key="b.key" cols="12" sm="6">
            <v-card variant="tonal" color="secondary" class="pa-4 h-100 d-flex flex-column">
              <div class="d-flex align-center mb-2">
                <v-icon icon="mdi-android" size="28" class="mr-2" />
                <div class="text-subtitle-2 font-weight-bold">{{ b.title }}</div>
              </div>
              <div class="text-caption mb-3">{{ b.subtitle }}</div>
              <div class="d-flex align-center text-caption mb-1">
                <v-icon icon="mdi-cellphone-cog" size="16" class="mr-1" />
                <span><strong>Requires:</strong> {{ b.minAndroid }}</span>
              </div>
              <div class="d-flex align-center text-caption mb-3 flex-grow-1">
                <v-icon icon="mdi-fingerprint" size="16" class="mr-1" />
                <span><strong>Verification:</strong> {{ b.verification }}</span>
              </div>
              <v-alert v-if="b.note" type="warning" variant="tonal" density="compact" class="mb-3 text-caption">
                {{ b.note }}
              </v-alert>
              <v-btn color="secondary" variant="flat" prepend-icon="mdi-download" :href="downloadUrl(b.file)" target="_blank" rel="noopener">
                Download APK
              </v-btn>
            </v-card>
          </v-col>
        </v-row>
        <v-alert type="info" variant="tonal" density="compact" class="mt-3">
          Not sure which one your device needs? Check the fingerprint scanner attached to the device (or ask
          whoever provisioned it) — installing the wrong build will fail to detect the scanner. If the device is
          an older tablet on Android 5 or 6, it can only take the Morpho 6.15 build; Android will refuse to
          install the 6.42 build at all on those devices.
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
</style>
