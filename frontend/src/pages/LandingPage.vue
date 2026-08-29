<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'

const mobileNavOpen = ref(false)
const revealRoot = ref<HTMLElement | null>(null)
const siteNav = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null
let featureObserver: IntersectionObserver | null = null

// The hero is the landing page itself, so no section link is active there.
// Once a section reaches the fixed navbar, its matching link becomes active.
const navSections = ['mission', 'how', 'platform', 'showcase']
const activeSection = ref('')
let navSpyRafId: number | undefined
let scrollDir: 'up' | 'down' = 'down'
let lastScrollY = 0
let navRevealTimer: ReturnType<typeof setTimeout> | null = null

function scheduleActiveSectionSync() {
  if (navSpyRafId !== undefined) return
  navSpyRafId = requestAnimationFrame(() => {
    navSpyRafId = undefined
    const activationLine = (siteNav.value?.offsetHeight ?? 0) + 16
    let currentSection = ''

    for (const id of navSections) {
      const section = document.getElementById(id)
      if (section && section.getBoundingClientRect().top <= activationLine) {
        currentSection = id
      }
    }

    activeSection.value = currentSection
  })
}

function trackScrollDir() {
  const y = window.scrollY
  if (y > lastScrollY + 2) scrollDir = 'down'
  else if (y < lastScrollY - 2) scrollDir = 'up'
  lastScrollY = y
}

// Nav hides the instant the page moves and drops back in once scrolling settles.
const navHidden = ref(false)
function handleNavScroll() {
  if (mobileNavOpen.value) {
    navHidden.value = false
    return
  }

  navHidden.value = true
  if (navRevealTimer) clearTimeout(navRevealTimer)
  navRevealTimer = setTimeout(() => {
    navHidden.value = false
    navRevealTimer = null
  }, 140)
}

// Request-a-demo form. With no CRM/email endpoint on the backend, submitting opens
// the visitor's mail client addressed to the demo inbox with their details prefilled
// -- honest and functional without pretending a server received it.
const demo = ref({ name: '', organisation: '', email: '', message: '' })
const demoSubmitted = ref(false)

function submitDemo() {
  const { name, organisation, email, message } = demo.value
  if (!name.trim() || !email.trim()) return
  const subject = encodeURIComponent(`Demo request from ${name}${organisation ? ` (${organisation})` : ''}`)
  const body = encodeURIComponent(
    `Name: ${name}\nOrganisation: ${organisation}\nEmail: ${email}\n\n${message}`,
  )
  window.location.href = `mailto:${demoEmail}?subject=${subject}&body=${body}`
  demoSubmitted.value = true
}

// Hero carousel -- five value props, each with a full-bleed background photo.
// Project-owned carousel images live in frontend/public/hero/. Their complete
// compositions are kept visible in the hero rather than cropped to full bleed.
const heroSlides = [
  {
    icon: 'cash', tone: 'primary', eyebrow: 'Cash transfers',
    title: 'Cash assistance, delivered with certainty.',
    copy: 'Generate a payment cycle, verify each recipient by fingerprint or face, and disburse with a maker-checker approval trail from anchor to organisation.',
    image: '/hero/cash-transfer.webp',
  },
  {
    icon: 'voucher', tone: 'accent', eyebrow: 'Voucher redemption',
    title: 'Real choice at the market, full accountability.',
    copy: 'Issue biometric-verified vouchers households can redeem with confidence, while every issue, redemption and void stays on a traceable ledger.',
    image: '/hero/voucher-redemption.webp',
  },
  {
    icon: 'fingerprint', tone: 'primary', eyebrow: 'Biometric verification',
    title: 'The right person gets paid. Every single time.',
    copy: 'A live fingerprint or face scan checked against the enrolled template at the moment of payment, even with no signal to check it against a server.',
    image: '/hero/biometric-verification.webp',
  },
  {
    icon: 'dedup', tone: 'accent', eyebrow: 'Deduplication',
    title: 'One household, one record. No fraud.',
    copy: 'Every new registration is screened against existing records for the same name, phone number and location before it is ever accepted.',
    image: '/hero/deduplication.webp',
  },
  {
    icon: 'ai', tone: 'primary', eyebrow: 'Continuous monitoring',
    title: 'An AI agent that never stops watching.',
    copy: 'Registrations and disbursements are reviewed as they land, flagging the patterns a person reviewing thousands of rows would miss.',
    image: '/hero/ai-agent.webp',
  },
]
const HERO_INTERVAL_MS = 9000
const activeHero = ref(0)
const heroProgressTick = ref(0)
let heroTimer: ReturnType<typeof setInterval> | null = null
let heroTimerReduceMotion = false

// Each hero photo is a multi-MB PNG -- eagerly setting all five as CSS
// background-images (as before) meant the browser fetched every slide on
// first paint even though only one is ever visible. Only the slide that has
// actually been shown gets its background-image bound; the upcoming one is
// pre-added a beat ahead so the 9s auto-advance never shows a blank flash.
const loadedHero = ref<Set<number>>(new Set([0, 1]))

function restartHeroTimer() {
  if (heroTimer) clearInterval(heroTimer)
  if (!heroTimerReduceMotion) {
    heroTimer = setInterval(() => setHero((activeHero.value + 1) % heroSlides.length), HERO_INTERVAL_MS)
  }
}

function setHero(index: number) {
  activeHero.value = index
  heroProgressTick.value++
  loadedHero.value.add(index)
  loadedHero.value.add((index + 1) % heroSlides.length)
}

function goToHero(index: number) {
  setHero(index)
  restartHeroTimer()
}

function nextHero() {
  goToHero((activeHero.value + 1) % heroSlides.length)
}

function prevHero() {
  goToHero((activeHero.value - 1 + heroSlides.length) % heroSlides.length)
}

function closeMobileNav() {
  mobileNavOpen.value = false
}

// "How it works" -- a flat horizontal carousel that drifts sideways in a
// continuous, seamless loop (no perspective/3D), pausable, nudgeable with
// prev/next by exactly one card width at a time.
const howSteps = [
  {
    title: 'Register in the field', image: '/how-it-works/register-in-the-field.webp',
    copy: 'A field officer captures the household offline: demographics, fingerprints, a photo, and the exact GPS location, down to village level.',
  },
  {
    title: 'Sync when signal returns', image: '/how-it-works/sync-when-signal-returns.webp',
    copy: "Every record stays queued on the device until a connection appears, then uploads on its own. No officer has to remember to press sync.",
  },
  {
    title: 'Verify at disbursement', image: '/how-it-works/verify-at-disbursement.webp',
    copy: "Fingerprint or face match confirms it's the registered person, not whoever's holding their ID card that day.",
  },
  {
    title: 'Reconcile on the dashboard', image: '/how-it-works/reconcile-on-the-dashboard.webp',
    copy: 'Anchors and organisations see every payment, time- and location-stamped, ready for donor reporting.',
  },
]

const howViewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1280)
const howCardWidth = computed(() => {
  if (howViewportWidth.value < 390) return 240
  if (howViewportWidth.value < 820) return 280
  return 340
})
const howCardGap = 24
const howSlot = computed(() => howCardWidth.value + howCardGap)
const howSetWidth = computed(() => howSlot.value * howSteps.length)
// Three copies keep the belt visually full through a run of manual
// prev/next clicks, not just the passive auto-drift.
const howTrackItems = computed(() => [...howSteps, ...howSteps, ...howSteps])

const howPaused = ref(false)
const howSnapping = ref(false)
const howOffset = ref(howSetWidth.value)

function onHowResize() {
  howViewportWidth.value = window.innerWidth
}

let howRafId: number | undefined
let howLastTime: number | undefined
const howPxPerMs = 0.035

function howTick(time: number) {
  if (howLastTime === undefined) howLastTime = time
  const delta = time - howLastTime
  howLastTime = time
  if (!howPaused.value) {
    howOffset.value += delta * howPxPerMs
  }
  // Wrap the offset back by exactly one set-width once it drifts past the
  // middle copy -- only while nothing is mid-transition, so the reset is an
  // untransitioned (and therefore invisible) jump between identical frames.
  if (!howSnapping.value) {
    if (howOffset.value >= howSetWidth.value * 2) howOffset.value -= howSetWidth.value
    if (howOffset.value <= 0) howOffset.value += howSetWidth.value
  }
  howRafId = requestAnimationFrame(howTick)
}

function howStep(direction: number) {
  howPaused.value = true
  howSnapping.value = true
  howOffset.value += direction * howSlot.value
  window.setTimeout(() => { howSnapping.value = false }, 550)
}

const howTrackStyle = computed(() => ({
  transform: `translateX(${-howOffset.value}px)`,
  transition: howSnapping.value ? 'transform 0.55s cubic-bezier(0.16, 1, 0.3, 1)' : 'none',
}))

// Contact email is configurable via VITE_CONTACT_EMAIL so it can be swapped
// for a real inbox without a code change.
const contactEmail = import.meta.env.VITE_CONTACT_EMAIL || 'info@biopay.com'
const demoEmail = import.meta.env.VITE_DEMO_EMAIL || contactEmail

onMounted(() => {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  const items = revealRoot.value?.querySelectorAll('.reveal') ?? []

  if (reduceMotion || !('IntersectionObserver' in window)) {
    items.forEach((el) => el.classList.add('in'))
  } else {
    observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('in')
            observer?.unobserve(entry.target)
          }
        })
      },
      { threshold: 0.15 },
    )
    items.forEach((el) => observer?.observe(el))
  }

  // Platform cards slide in from whichever side matches the live scroll
  // direction -- from below while scrolling down, from above while scrolling
  // back up -- and re-trigger every time a card re-enters the viewport.
  const featureCards = revealRoot.value?.querySelectorAll('.feature-card-reveal') ?? []
  lastScrollY = window.scrollY
  window.addEventListener('scroll', trackScrollDir, { passive: true })
  window.addEventListener('scroll', handleNavScroll, { passive: true })
  window.addEventListener('scroll', scheduleActiveSectionSync, { passive: true })

  if (reduceMotion || !('IntersectionObserver' in window)) {
    featureCards.forEach((el) => el.classList.add('in'))
  } else {
    featureObserver = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          const el = entry.target as HTMLElement
          if (entry.isIntersecting) {
            el.classList.remove('enter-from-below', 'enter-from-above', 'in')
            el.classList.add(scrollDir === 'down' ? 'enter-from-below' : 'enter-from-above')
            void el.offsetWidth
            requestAnimationFrame(() => el.classList.add('in'))
          } else {
            el.classList.remove('in')
          }
        })
      },
      { threshold: 0.2 },
    )
    featureCards.forEach((el) => featureObserver?.observe(el))
  }

  scheduleActiveSectionSync()

  heroTimerReduceMotion = reduceMotion
  restartHeroTimer()

  howPaused.value = reduceMotion
  if (!reduceMotion) howRafId = requestAnimationFrame(howTick)
  window.addEventListener('resize', onHowResize, { passive: true })
  window.addEventListener('resize', scheduleActiveSectionSync, { passive: true })
})

onBeforeUnmount(() => {
  observer?.disconnect()
  featureObserver?.disconnect()
  window.removeEventListener('scroll', trackScrollDir)
  window.removeEventListener('scroll', handleNavScroll)
  window.removeEventListener('scroll', scheduleActiveSectionSync)
  window.removeEventListener('resize', onHowResize)
  window.removeEventListener('resize', scheduleActiveSectionSync)
  if (navRevealTimer) clearTimeout(navRevealTimer)
  if (heroTimer) clearInterval(heroTimer)
  if (howRafId) cancelAnimationFrame(howRafId)
  if (navSpyRafId !== undefined) cancelAnimationFrame(navSpyRafId)
})
</script>

<template>
  <div class="landing-root" ref="revealRoot">
    <a class="skip-link" href="#main-content">Skip to main content</a>
    <header ref="siteNav" class="site-nav" :class="{ 'nav-hidden': navHidden && !mobileNavOpen }">
      <div class="wrap nav-row">
        <a class="brand" href="#top">
          <img src="/biopay_logo_horizontal.svg" alt="BioPay" class="brand-logo" />
        </a>
        <nav class="nav-links">
          <a href="#mission" :class="{ active: activeSection === 'mission' }">Mission</a>
          <a href="#how" :class="{ active: activeSection === 'how' }">How it works</a>
          <a href="#platform" :class="{ active: activeSection === 'platform' }">Platform</a>
          <a href="#showcase" :class="{ active: activeSection === 'showcase' }">Product</a>
        </nav>
        <div class="nav-actions">
          <router-link class="nav-login" to="/login">Log in</router-link>
          <a class="btn btn-primary btn-nav" href="#demo">Request a demo</a>
          <button
            class="nav-toggle" aria-label="Toggle navigation" aria-controls="mobile-navigation"
            :aria-expanded="mobileNavOpen" @click="mobileNavOpen = !mobileNavOpen"
          >
            <svg v-if="!mobileNavOpen" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M4 7h16M4 12h16M4 17h16" /></svg>
            <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18" /></svg>
          </button>
        </div>
      </div>
      <nav v-if="mobileNavOpen" id="mobile-navigation" class="mobile-nav" aria-label="Mobile navigation">
        <a href="#mission" :class="{ active: activeSection === 'mission' }" @click="closeMobileNav">Mission</a>
        <a href="#how" :class="{ active: activeSection === 'how' }" @click="closeMobileNav">How it works</a>
        <a href="#platform" :class="{ active: activeSection === 'platform' }" @click="closeMobileNav">Platform</a>
        <a href="#showcase" :class="{ active: activeSection === 'showcase' }" @click="closeMobileNav">Product</a>
        <a href="#demo" @click="closeMobileNav">Request a demo</a>
        <router-link to="/login" @click="closeMobileNav">Log in</router-link>
      </nav>
    </header>

    <main id="main-content">
      <div id="top"></div>
      <!-- HERO -->
      <section class="hero hero-visual">
        <div
          v-for="(slide, index) in heroSlides" :key="`${slide.eyebrow}-colour`"
          class="hero-colorwash" :class="{ active: index === activeHero }" aria-hidden="true"
          :style="loadedHero.has(index) ? { backgroundImage: `url(${slide.image})` } : {}"
        />
        <div
          v-for="(slide, index) in heroSlides" :key="slide.eyebrow"
          class="hero-image" :class="{ active: index === activeHero }"
          :style="loadedHero.has(index) ? { backgroundImage: `url(${slide.image})` } : {}"
        />
        <div class="hero-scrim"></div>

        <button class="hero-arrow prev" aria-label="Previous slide" @click="prevHero">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6" /></svg>
        </button>
        <button class="hero-arrow next" aria-label="Next slide" @click="nextHero">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18l6-6-6-6" /></svg>
        </button>

        <div class="wrap hero-grid">
          <div class="hero-copy">
            <Transition name="hero-fade" mode="out-in">
            <div class="hero-slide" :key="activeHero">
              <div class="hero-slide-icon" :class="heroSlides[activeHero].tone">
                <svg v-if="heroSlides[activeHero].icon === 'cash'" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="2" y="6" width="20" height="12" rx="2.4" /><circle cx="12" cy="12" r="3" />
                  <path d="M6 9v.01M18 9v.01M6 15v.01M18 15v.01" />
                </svg>
                <svg v-else-if="heroSlides[activeHero].icon === 'voucher'" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M4 8a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v1.5a2 2 0 0 0 0 5V16a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-1.5a2 2 0 0 0 0-5V8z" />
                  <path d="M14 6.5v11" stroke-dasharray="2 2.4" />
                </svg>
                <svg v-else-if="heroSlides[activeHero].icon === 'fingerprint'" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round">
                  <path d="M12 3a7 7 0 0 1 7 7c0 3.5-1 6-1 8.5" />
                  <path d="M12 3a7 7 0 0 0-7 7c0 2 .3 3.6.8 5" />
                  <path d="M9 20c1.2-2 1.5-4.5 1.5-7A4.5 4.5 0 0 1 15 8.5c1 0 2 .3 2.7 1" />
                  <path d="M6.5 17c.8-1.7 1-3.8 1-6a4.5 4.5 0 0 1 4-4.5" />
                </svg>
                <svg v-else-if="heroSlides[activeHero].icon === 'dedup'" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="9.5" cy="12" r="6.5" /><circle cx="14.5" cy="12" r="6.5" />
                </svg>
                <svg v-else width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 3v2.4M12 18.6V21M3 12h2.4M18.6 12H21M5.6 5.6l1.7 1.7M16.7 16.7l1.7 1.7M5.6 18.4l1.7-1.7M16.7 7.3l1.7-1.7" />
                  <circle cx="12" cy="12" r="3.6" />
                </svg>
              </div>
              <span class="eyebrow">{{ heroSlides[activeHero].eyebrow }}</span>
              <h1>{{ heroSlides[activeHero].title }}</h1>
              <p class="lede">{{ heroSlides[activeHero].copy }}</p>
            </div>
            </Transition>
            <div class="hero-ctas">
              <a class="btn btn-primary" href="#demo">Request a demo</a>
              <a class="btn btn-ghost" href="#how">How it works</a>
            </div>
            <div class="hero-dots" aria-label="Hero slides">
              <button
                v-for="(slide, index) in heroSlides" :key="slide.eyebrow"
                :class="{ active: index === activeHero }"
                :aria-label="`Show ${slide.eyebrow}`" @click="goToHero(index)"
              >
                <span
                  v-if="index === activeHero"
                  :key="heroProgressTick"
                  class="hero-dot-fill"
                  :style="{ animationDuration: `${HERO_INTERVAL_MS}ms` }"
                />
              </button>
            </div>
            <div class="hero-facts">
              <div class="hero-fact"><strong>2</strong> verification methods</div>
              <div class="hero-fact"><strong>0</strong> signal required to capture</div>
              <div class="hero-fact"><strong>100%</strong> of payments GPS-tagged</div>
            </div>
          </div>
        </div>
      </section>

      <!-- MISSION -->
      <section class="mission" id="mission">
        <div class="wrap">
          <div>
            <h2>Aid reaches whoever's in line. It should reach whoever it's for.</h2>
          </div>
          <div class="mission-body">
            <p>
              Cash and food-for-work programmes lose ground to the oldest problem in aid: proving a
              payment reached the person the programme actually registered. A name on a list can be
              claimed by anyone standing at the front of it.
            </p>
            <p>
              <strong>BioPay ties every registration to the beneficiary's own body</strong>, using a
              fingerprint or face captured once in the field and checked again at the exact moment
              they're paid. No connectivity required to capture it, no way for someone else to stand
              in for them at disbursement.
            </p>
            <p>
              The structure mirrors how these programmes are actually run: an
              <strong>anchor</strong> (an organisation or lead financial partner) oversees one or
              more <strong>organisations/programmes</strong> (the NGOs actually running them),
              whose <strong>field officers</strong> do the registering and paying, down to the
              village, location, county and state the household lives in.
            </p>
          </div>
        </div>
      </section>

      <!-- HOW IT WORKS -->
      <section id="how">
        <div class="wrap">
          <h2 class="section-title">From registration to reconciled payment</h2>

          <div class="how-carousel reveal">
            <div class="how-carousel-viewport">
              <div class="how-carousel-track" :style="howTrackStyle">
                <div
                  v-for="(step, index) in howTrackItems" :key="`${step.title}-${index}`" class="how-card"
                  :aria-hidden="index < howSteps.length || index >= howSteps.length * 2"
                >
                  <img class="how-card-image" :src="step.image" :alt="step.title" width="1402" height="1122" loading="lazy" decoding="async" />
                  <div class="how-card-scrim"></div>
                  <div class="how-card-copy">
                    <span class="how-card-num">{{ String((index % howSteps.length) + 1).padStart(2, '0') }}</span>
                    <h3>{{ step.title }}</h3>
                    <p>{{ step.copy }}</p>
                  </div>
                </div>
              </div>
            </div>

            <div class="how-carousel-controls">
              <button type="button" class="how-carousel-btn" aria-label="Previous step" @click="howStep(-1)">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M15 18l-6-6 6-6" /></svg>
              </button>
              <button
                type="button" class="how-carousel-btn"
                :aria-label="howPaused ? 'Resume rotation' : 'Pause rotation'"
                @click="howPaused = !howPaused"
              >
                <svg v-if="howPaused" width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z" /></svg>
                <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M6 5h4v14H6zM14 5h4v14h-4z" /></svg>
              </button>
              <button type="button" class="how-carousel-btn" aria-label="Next step" @click="howStep(1)">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 18l6-6-6-6" /></svg>
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- PLATFORM / FEATURES -->
      <section id="platform">
        <div class="wrap">
          <header class="platform-heading reveal">
            <h2 class="section-title">Built for the field first, the office second</h2>
            <p>Capture the work where it happens, then give programme teams a complete record to manage and reconcile.</p>
          </header>

          <div class="platform-layout">
            <article class="platform-offline feature-card-reveal">
              <div class="platform-offline-heading">
                <span class="platform-symbol"><i class="mdi mdi-wifi-off"></i></span>
                <div>
                  <h3>Keep working without a connection</h3>
                  <p>Registration, biometric verification and payment records remain available on the field device until connectivity returns.</p>
                </div>
              </div>

              <div class="platform-flow" aria-label="Offline workflow: capture, verify, then sync">
                <div><i class="mdi mdi-account-plus-outline"></i><b>Capture</b><span>Save the field record</span></div>
                <i class="mdi mdi-chevron-right platform-flow-arrow" aria-hidden="true"></i>
                <div><i class="mdi mdi-fingerprint"></i><b>Verify</b><span>Confirm the beneficiary</span></div>
                <i class="mdi mdi-chevron-right platform-flow-arrow" aria-hidden="true"></i>
                <div><i class="mdi mdi-cloud-upload"></i><b>Sync</b><span>Upload when online</span></div>
              </div>

              <div class="platform-offline-note">
                <i class="mdi mdi-shield-check-outline"></i>
                <span><b>Nothing is lost between visits.</b> Records stay encrypted on the device and sync automatically.</span>
              </div>
            </article>

            <div class="platform-groups">
              <article class="platform-group feature-card-reveal">
                <header>
                  <span class="platform-symbol"><i class="mdi mdi-account-check-outline"></i></span>
                  <div><h3>Identity and presence</h3><p>Prove who was served and where the activity took place.</p></div>
                </header>
                <div class="platform-points">
                  <div><i class="mdi mdi-fingerprint"></i><p><b>Fingerprint or face verification</b><span>Match a person against their enrolment at payment.</span></p></div>
                  <div><i class="mdi mdi-map-marker-radius-outline"></i><p><b>Location on every capture</b><span>Attach the freshest available device or network location.</span></p></div>
                </div>
              </article>

              <article class="platform-group feature-card-reveal">
                <header>
                  <span class="platform-symbol accent"><i class="mdi mdi-clipboard-check-outline"></i></span>
                  <div><h3>Field operations</h3><p>Use the same beneficiary record across programme activity.</p></div>
                </header>
                <div class="platform-points">
                  <div><i class="mdi mdi-calendar-check"></i><p><b>Event attendance</b><span>Record workshops, immunisation drives and cash-for-work.</span></p></div>
                  <div><i class="mdi mdi-ticket-confirmation-outline"></i><p><b>Voucher redemption</b><span>Issue once, redeem once, and keep the event traceable.</span></p></div>
                </div>
              </article>

              <article class="platform-group feature-card-reveal">
                <header>
                  <span class="platform-symbol"><i class="mdi mdi-account-multiple-check-outline"></i></span>
                  <div><h3>Payment cycle assurance</h3><p>Every disbursement is checked before it's paid.</p></div>
                </header>
                <div class="platform-points">
                  <div><i class="mdi mdi-robot-outline"></i><p><b>AI-monitored disbursements</b><span>An AI agent reviews every batch as it lands, flagging the patterns a manual review would miss.</span></p></div>
                  <div><i class="mdi mdi-history"></i><p><b>End-to-end audit trail</b><span>Every batch, approval and disbursement is time-stamped and traceable from anchor to recipient.</span></p></div>
                </div>
              </article>

              <article class="platform-group feature-card-reveal">
                <header>
                  <span class="platform-symbol accent"><i class="mdi mdi-chart-line"></i></span>
                  <div><h3>Programme oversight</h3><p>Give authorised teams one accountable operating record.</p></div>
                </header>
                <div class="platform-points">
                  <div><i class="mdi mdi-eye-outline"></i><p><b>Continuous review</b><span>Surface duplicate registrations and unusual activity for review.</span></p></div>
                  <div><i class="mdi mdi-credit-card-outline"></i><p><b>Subscription &amp; billing</b><span>Renewals, invoices and grace periods, priced per the terms agreed for your anchor.</span></p></div>
                </div>
              </article>
            </div>
          </div>

          <div class="platform-assurance reveal">
            <span><i class="mdi mdi-lock-outline"></i> Encrypted records</span>
            <span><i class="mdi mdi-shield-key-outline"></i> Role-based access</span>
            <span><i class="mdi mdi-history"></i> Time-stamped audit trail</span>
            <span><i class="mdi mdi-check-circle-outline"></i> Accountable by default</span>
          </div>
        </div>
      </section>

      <!-- SHOWCASE -->
      <section id="showcase">
        <div class="wrap">
          <div class="showcase-heading">
            <h2 class="section-title">One dashboard for oversight, one app for the field</h2>
          </div>

          <div class="product-stage">
            <figure class="product-view product-view-dashboard reveal">
              <div
                class="product-frame dashboard-frame"
                role="img"
                aria-label="Representative BioPay web dashboard based on the current product interface"
              >
                <div class="preview-web-shell" aria-hidden="true">
                  <aside class="preview-web-nav">
                    <img src="/biopay_logo_horizontal_light.svg" alt="" />
                    <div class="preview-nav-item active"><i class="mdi mdi-view-dashboard-outline"></i>Dashboard</div>
                    <small>Configs</small>
                    <div class="preview-nav-item"><i class="mdi mdi-domain"></i>Organizations</div>
                    <div class="preview-nav-item"><i class="mdi mdi-map-marker-radius"></i>Locations</div>
                    <small>User management</small>
                    <div class="preview-nav-item"><i class="mdi mdi-account-multiple-outline"></i>Users</div>
                    <div class="preview-nav-item"><i class="mdi mdi-shield-account-outline"></i>Roles &amp; permissions</div>
                    <div class="preview-nav-item"><i class="mdi mdi-account-tie"></i>Field officers</div>
                    <small>Biodata</small>
                    <div class="preview-nav-item"><i class="mdi mdi-home-group"></i>Households</div>
                    <div class="preview-nav-item"><i class="mdi mdi-cash-multiple"></i>Payments</div>
                    <div class="preview-nav-item"><i class="mdi mdi-calendar-check"></i>Attendance</div>
                    <small>Payment generation</small>
                    <div class="preview-nav-item"><i class="mdi mdi-calendar-month-outline"></i>Payment cycles</div>
                    <div class="preview-nav-item"><i class="mdi mdi-ticket-confirmation-outline"></i>Vouchers</div>
                  </aside>

                  <div class="preview-web-app">
                    <div class="preview-web-toolbar">
                      <span><i class="mdi mdi-menu"></i> Dashboard</span>
                      <div><b>Illustrative preview · Anchor administrator</b><span class="preview-avatar">AO</span></div>
                    </div>
                    <div class="preview-web-content">
                      <div class="preview-web-heading">
                        <div>
                          <img src="/biopay_logo_horizontal.svg" alt="" />
                          <strong>Welcome back, Amina</strong>
                          <span>Track programme activity and keep every disbursement accountable.</span>
                        </div>
                        <div class="preview-web-actions"><span>Refresh</span><b>Generate payment cycle</b></div>
                      </div>

                      <div class="preview-metric-grid">
                        <div class="preview-metric"><span>Organizations</span><strong>12</strong><small>Active programmes</small><i class="mdi mdi-domain"></i></div>
                        <div class="preview-metric green"><span>Households</span><strong>4,286</strong><small>Approved records</small><i class="mdi mdi-home-group"></i></div>
                        <div class="preview-metric amber"><span>Total value disbursed</span><strong>USD 840K</strong><small>Cash + vouchers</small><i class="mdi mdi-cash-multiple"></i></div>
                        <div class="preview-metric"><span>Active officers</span><strong>38</strong><small>Currently enabled</small><i class="mdi mdi-account-tie"></i></div>
                        <div class="preview-metric"><span>Fingerprints</span><strong>6,914</strong><small>Ready to verify</small><i class="mdi mdi-fingerprint"></i></div>
                        <div class="preview-metric amber"><span>Pending approvals</span><strong>3</strong><small>Awaiting a checker</small><i class="mdi mdi-timer-sand"></i></div>
                      </div>

                      <div class="preview-analytics">
                        <div class="preview-chart-card">
                          <strong>Payment volume over time</strong>
                          <svg viewBox="0 0 360 100" preserveAspectRatio="none">
                            <path class="chart-gridline" d="M0 20H360M0 50H360M0 80H360" />
                            <path class="chart-area" d="M0 78 55 64 110 70 165 42 220 51 275 29 330 36 360 18V100H0Z" />
                            <path class="chart-line" d="M0 78 55 64 110 70 165 42 220 51 275 29 330 36 360 18" />
                          </svg>
                        </div>
                        <div class="preview-chart-card">
                          <strong>Household registration trend</strong>
                          <svg viewBox="0 0 250 100" preserveAspectRatio="none">
                            <path class="chart-gridline" d="M0 20H250M0 50H250M0 80H250" />
                            <path class="chart-line green-line" d="M0 83 42 72 84 66 126 48 168 55 210 31 250 22" />
                          </svg>
                        </div>
                      </div>

                      <div class="preview-activity">
                        <strong>Recent activity</strong>
                        <div><span>HH-2026-0412 · Hope Programme</span><b>USD 120</b><em>Paid</em></div>
                        <div><span>HH-2026-0408 · Community Fund</span><b>USD 75</b><em>Paid</em></div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <figcaption>
                <span>Web dashboard</span>
                The web dashboard for anchors and organisations to register, disburse and reconcile.
              </figcaption>
            </figure>

            <figure class="product-view product-view-mobile reveal">
              <div
                class="product-frame mobile-frame"
                role="img"
                aria-label="Representative BioPay Android field workspace based on the current app interface"
              >
                <div class="preview-phone" aria-hidden="true">
                  <div class="preview-phone-status"><b>9:41</b><span>● ◔ ▰</span></div>
                  <div class="preview-phone-scroll">
                    <section class="preview-phone-hero">
                      <div class="preview-phone-brand"><img src="/favicon.svg" alt="" /><b>BioPay field workspace</b><i class="mdi mdi-bell-outline"></i></div>
                      <h3>Hello, Amina</h3>
                      <p>HOPE-RELIEF-01 · illustrative preview</p>
                      <div class="preview-sync"><i class="mdi mdi-sync"></i> All local records are synced</div>
                      <div class="preview-phone-buttons"><b><i class="mdi mdi-account-plus-outline"></i> Register household</b><span><i class="mdi mdi-sync"></i> Sync now</span></div>
                    </section>

                    <section class="preview-phone-card preview-summary">
                      <header><b><i class="mdi mdi-chart-box-outline"></i> Operational summary</b><span>View all ›</span></header>
                      <div class="preview-phone-kpis">
                        <div><i class="mdi mdi-home-group"></i><strong>318</strong><span>Households</span></div>
                        <div class="amber"><i class="mdi mdi-sync"></i><strong>6</strong><span>Pending sync</span></div>
                        <div class="green"><i class="mdi mdi-cash-check"></i><strong>204</strong><span>Paid</span></div>
                        <div class="orange"><i class="mdi mdi-cash-clock"></i><strong>12</strong><span>Payment pending</span></div>
                      </div>
                    </section>

                    <section class="preview-phone-card preview-donut">
                      <header><b><i class="mdi mdi-chart-donut"></i> Payment status</b></header>
                      <div class="donut-row">
                        <svg class="donut-svg" viewBox="0 0 100 100" aria-hidden="true">
                          <circle cx="50" cy="50" r="40" fill="none" stroke="#e7eeeb" stroke-width="14" />
                          <circle cx="50" cy="50" r="40" fill="none" stroke="#0d9488" stroke-width="14" stroke-linecap="round" stroke-dasharray="237.4 251.3" transform="rotate(-90 50 50)" />
                        </svg>
                        <div class="donut-legend">
                          <span><i class="donut-dot green"></i>Paid<b>204</b></span>
                          <span><i class="donut-dot amber"></i>Pending<b>12</b></span>
                        </div>
                      </div>
                    </section>
                  </div>
                  <nav class="preview-phone-nav">
                    <div class="active"><i class="mdi mdi-home-outline"></i><span>Home</span></div>
                    <div><i class="mdi mdi-home-group"></i><span>Households</span></div>
                    <b><i class="mdi mdi-fingerprint"></i></b>
                    <div><i class="mdi mdi-bell-outline"></i><span>Activity</span></div>
                    <div><i class="mdi mdi-dots-horizontal"></i><span>More</span></div>
                  </nav>
                </div>
              </div>
              <figcaption>
                <span>Field agent app</span>
                The field agent app is offline-first and built to run all day on one battery.
              </figcaption>
            </figure>
          </div>
        </div>
      </section>

      <!-- REQUEST A DEMO -->
      <section id="demo">
        <div class="wrap demo-grid">
          <div class="demo-copy">
            <h2 class="section-title">See BioPay against your own programme</h2>
            <p style="max-width: 46ch; color: var(--color-text-muted); margin-top: 0.9rem">
              Tell us about your anchor, your organisations, and how many households you're
              registering this cycle. We'll walk you through registration, biometric verification
              and reconciliation on a live environment.
            </p>
            <ul class="demo-points">
              <li>Offline-first field registration &amp; payment</li>
              <li>Fingerprint and face verification</li>
              <li>Anchor → organisation → officer oversight</li>
            </ul>
          </div>

          <div class="demo-card">
            <div v-if="demoSubmitted" class="demo-thanks" role="status" aria-live="polite">
              <div class="demo-check" aria-hidden="true">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4 4L19 7" /></svg>
              </div>
              <h4>Thanks, {{ demo.name }}.</h4>
              <p>Your email app should have opened with your request ready to send. If it didn't,
                email us at <a :href="`mailto:${demoEmail}`">{{ demoEmail }}</a>.</p>
            </div>
            <form v-else class="demo-form" @submit.prevent="submitDemo">
              <label>Full name
                <input v-model="demo.name" type="text" required placeholder="Your name" autocomplete="name" />
              </label>
              <label>Organisation
                <input v-model="demo.organisation" type="text" placeholder="Anchor or organisation" autocomplete="organization" />
              </label>
              <label>Work email
                <input v-model="demo.email" type="email" required placeholder="you@organisation.org" autocomplete="email" />
              </label>
              <label>What would you like to see?
                <textarea v-model="demo.message" rows="3" placeholder="Tell us about your programme"></textarea>
              </label>
              <button class="btn btn-primary demo-submit" type="submit">Request a demo</button>
            </form>
          </div>
        </div>
      </section>
    </main>

    <footer>
      <div class="wrap">
        <div class="foot-grid">
          <div class="foot-brand">
            <a class="brand" href="#top"><img src="/biopay_logo_horizontal.svg" alt="BioPay" class="brand-logo" /></a>
            <p>Biometric registration and payment infrastructure for anchors and organisations
              running cash transfers, food distribution, voucher redemption, in-kind
              interventions (goods and items), and cash-for-work programmes.</p>
          </div>
          <div class="foot-links">
            <div class="foot-col">
              <h5>Platform</h5>
              <a href="#how">How it works</a>
              <a href="#platform">Capabilities</a>
              <a href="#showcase">Product tour</a>
            </div>
            <div class="foot-col">
              <h5>Company</h5>
              <a href="#mission">Mission</a>
              <a href="#demo">Request a demo</a>
              <router-link to="/login">Log in</router-link>
            </div>
            <div class="foot-col">
              <h5>Contact us</h5>
              <a :href="`mailto:${contactEmail}`">{{ contactEmail }}</a>
            </div>
          </div>
        </div>
        <div class="foot-bottom">
          <span>© 2026 BioPay.</span>
          <span>Anchor · Organisation · Field officer</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
/* ---------------------------------------------------------------
   Tokens -- scoped to .landing-root (not :root/body) since this page
   lives inside the app's single shared document; unscoped :root/body/*
   rules would leak onto every other route in the SPA.
   --------------------------------------------------------------- */
.landing-root {
  --color-bg: #FFFFFF;
  --color-surface: #FFFFFF;
  --color-surface-2: #F8FAFC;
  --color-text: #0F172A;
  --color-text-muted: #475569;
  --color-primary: #0D9488;
  --color-primary-deep: #0F766E;
  --color-primary-soft: #CCFBF1;
  --color-accent: #F59E0B;
  --color-accent-deep: #EA580C;
  --color-accent-soft: #FEF3C7;
  --color-success: #10B981;
  --color-danger: #D64545;
  --color-line: rgba(20, 35, 31, 0.14);
  --shadow-color: rgba(10, 40, 33, 0.16);

  --font-display: 'Ubuntu', sans-serif;
  --font-body: 'Ubuntu', sans-serif;
  --font-mono: "SF Mono", "Cascadia Code", Consolas, "Liberation Mono", monospace;

  --step-caption: 0.72rem;
  --step-body: 0.95rem;
  --step-body-lg: 1.1rem;
  --step-h4: 1.3rem;
  --step-h3: 1.65rem;
  --step-h2: clamp(1.6rem, 1.25rem + 1.3vw, 2.15rem);
  --step-h1: clamp(2.2rem, 1.5rem + 2.6vw, 3.25rem);

  --space-1: 0.45rem;
  --space-2: 0.65rem;
  --space-3: 1.1rem;
  --space-4: 1.75rem;
  --space-5: 2.3rem;
  --space-6: 3.1rem;

  --radius-tag: 3px;
  --radius-card: 14px;
  --max-width: 1180px;
  --nav-height: 52px;

  display: block;
  width: 100%;
  overflow-x: clip;
  background: var(--color-bg);
  color: var(--color-text);
  font-family: var(--font-body);
  font-size: var(--step-body);
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
}

/* Landing is intentionally light/white regardless of the OS colour scheme --
   no prefers-color-scheme:dark override, so it never flips to a dark teal theme. */

.landing-root * { box-sizing: border-box; }

.landing-root .skip-link {
  position: fixed;
  top: 10px;
  left: 12px;
  z-index: 100;
  padding: .7rem 1rem;
  border-radius: 8px;
  background: var(--color-text);
  color: #fff;
  font-weight: 700;
  text-decoration: none;
  transform: translateY(-160%);
  transition: transform 160ms ease-out;
}
.landing-root .skip-link:focus { transform: translateY(0); }

:global(html) {
  scroll-behavior: smooth;
  scroll-padding-top: 6rem;
}

.landing-root section,
.landing-root footer {
  scroll-margin-top: 6rem;
}

.landing-root h1, .landing-root h2, .landing-root h3, .landing-root h4 {
  font-family: var(--font-display);
  font-weight: 700;
  line-height: 1.12;
  margin: 0;
  text-wrap: balance;
  letter-spacing: -0.01em;
}
.landing-root .section-title { font-size: var(--step-h2); max-width: 28ch; }
.landing-root .section-title.narrow { max-width: 20ch; }
.landing-root p { margin: 0; }
.landing-root a { color: inherit; }

.landing-root .wrap {
  max-width: var(--max-width);
  margin: 0 auto;
  padding-left: var(--space-4);
  padding-right: var(--space-4);
}
@media (max-width: 640px) {
  .landing-root .wrap { padding-left: var(--space-3); padding-right: var(--space-3); }
}

/* Use the extra canvas on large monitors without stretching readable copy,
   and without ballooning type past what fits on-screen at 100% zoom on a
   normal 1440-1920px display -- that regression is why this block stays
   modest rather than matching --max-width growth 1:1. */
@media (min-width: 1440px) {
  .landing-root {
    --max-width: 1240px;
  }

  .landing-root .wrap {
    padding-left: 2.25rem;
    padding-right: 2.25rem;
  }

}

@media (min-width: 2200px) {
  .landing-root {
    --max-width: 1520px;
    --step-body: 1rem;
    --step-body-lg: 1.15rem;
    --step-h1: clamp(2.6rem, 1.9rem + 1.6vw, 3.5rem);
    --step-h2: clamp(1.75rem, 1.4rem + 0.8vw, 2.3rem);
  }

}

.landing-root .eyebrow {
  font-family: var(--font-mono);
  font-size: var(--step-caption);
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: var(--color-primary);
  display: inline-flex;
  align-items: center;
  gap: 0.5em;
}

.landing-root .num {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

.landing-root .btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5em;
  font-family: var(--font-body);
  font-weight: 600;
  font-size: 0.95rem;
  padding: 0.85em 1.4em;
  border-radius: var(--radius-tag);
  border: 1px solid transparent;
  cursor: pointer;
  text-decoration: none;
  transition: transform 0.15s ease, box-shadow 0.15s ease, background 0.15s ease;
}
.landing-root .btn:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 3px; }
.landing-root .btn-primary {
  background: var(--color-accent);
  color: #1A1200;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.08);
}
.landing-root .btn-primary:hover { background: var(--color-accent-deep); transform: translateY(-1px); box-shadow: 0 6px 16px -6px var(--shadow-color); }
.landing-root .btn-nav { padding: 0.55em 1.05em; font-size: 0.86rem; }
.landing-root .btn-ghost {
  background: transparent;
  color: var(--color-text);
  border-color: var(--color-line);
}
.landing-root .btn-ghost:hover { border-color: var(--color-primary); }

.landing-root section { padding: var(--space-6) 0; }
.landing-root section + section { border-top: 1px solid var(--color-line); }

.landing-root .kicker-line { display: flex; align-items: center; gap: var(--space-2); margin-bottom: var(--space-3); }
.landing-root .kicker-line::after { content: ""; height: 1px; flex: 1; background: var(--color-line); }

/* Nav -- fixed, edge to edge. Hides the instant the page scrolls and drops
   back in once scrolling settles (handleNavScroll). */
.landing-root .site-nav {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  width: 100%;
  z-index: 40;
  margin: 0;
  border-radius: 0;
  border: 0;
  border-bottom: 1px solid var(--color-line);
  background: color-mix(in srgb, var(--color-bg) 92%, transparent);
  backdrop-filter: blur(10px);
  box-shadow: 0 12px 30px -16px rgba(2, 20, 18, 0.22);
  transition: transform 320ms cubic-bezier(0.16, 1, 0.3, 1), opacity 260ms ease, box-shadow 220ms ease;
}
.landing-root .site-nav .wrap { max-width: none; padding-left: clamp(1.25rem, 4vw, 3rem); padding-right: clamp(1.25rem, 4vw, 3rem); }
.landing-root .site-nav.nav-hidden {
  transform: translateY(-100%);
  opacity: 0;
  pointer-events: none;
}
@media (prefers-reduced-motion: reduce) {
  .landing-root .site-nav { transition: none; }
}
.landing-root .nav-row { height: var(--nav-height); display: flex; align-items: center; justify-content: space-between; padding-top: 0; padding-bottom: 0; }
/* main content sits below the now-fixed nav */
.landing-root main { padding-top: var(--nav-height); }
.landing-root .brand {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 1.2rem;
  text-decoration: none;
  color: var(--color-text);
}
.landing-root .brand-logo { display: block; width: 145px; height: auto; }
.landing-root .brand-mark {
  width: 30px;
  height: 30px;
  border-radius: 7px;
  background: var(--color-primary);
  color: #fff;
  display: grid;
  place-items: center;
  font-family: var(--font-mono);
  font-size: 0.8rem;
  font-weight: 700;
  flex-shrink: 0;
}
.landing-root .nav-links { display: flex; align-items: center; gap: var(--space-4); font-size: 0.92rem; }
.landing-root .nav-links a { text-decoration: none; color: var(--color-text-muted); transition: color 180ms ease; }
.landing-root .nav-links a:hover { color: var(--color-text); }
.landing-root .nav-links a.active { color: var(--color-accent); font-weight: 600; }
.landing-root .nav-links a.active:hover { color: var(--color-accent-deep); }
.landing-root .nav-links a:focus-visible,
.landing-root .nav-login:focus-visible,
.landing-root .brand:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 5px; border-radius: 4px; }
.landing-root .nav-actions { display: flex; align-items: center; gap: var(--space-3); }
.landing-root .nav-login { font-size: 0.9rem; text-decoration: none; color: var(--color-text-muted); }
.landing-root .nav-login:hover { color: var(--color-text); }
.landing-root .nav-toggle { display: none; align-items: center; justify-content: center; width: 38px; height: 38px; background: none; color: var(--color-text); border: 1px solid var(--color-line); border-radius: 10px; padding: 0; }
.landing-root .nav-toggle:focus-visible { outline: 3px solid var(--color-primary); outline-offset: 2px; }
@media (max-width: 860px) {
  .landing-root .nav-links { display: none; }
  .landing-root .nav-toggle { display: inline-flex; }
}
@media (max-width: 480px) {
  .landing-root { --nav-height: 50px; }
  .landing-root .brand-logo { width: 128px; }
  .landing-root .nav-login { display: none; }
  .landing-root .nav-actions { gap: var(--space-1); }
  .landing-root .nav-actions .btn { padding: 0.75em 1em; font-size: 0.84rem; }
}
.landing-root .mobile-nav { display: none; flex-direction: column; align-items: stretch; gap: 0; padding: .55rem 1.25rem 1rem; border-top: 1px solid var(--color-line); background: var(--color-bg); box-shadow: 0 20px 34px -28px var(--shadow-color); }
@media (max-width: 860px) {
  .landing-root .mobile-nav { display: flex; }
}
.landing-root .mobile-nav a { min-height: 44px; display: flex; align-items: center; padding: 0.65rem .35rem; border-bottom: 1px solid var(--color-line); text-decoration: none; color: var(--color-text-muted); font-weight: 600; }
.landing-root .mobile-nav a:last-child { border-bottom: 0; }
.landing-root .mobile-nav a.active { color: var(--color-accent); }
.landing-root .mobile-nav a:focus-visible { outline: 2px solid var(--color-primary); outline-offset: -2px; }

/* Hero artwork is close to square, so it is fitted inside the first screen
   instead of being enlarged and cropped like a conventional full-bleed photo. */
.landing-root .hero-visual { position: relative; isolation: isolate; overflow: hidden; height: min(560px, calc(100svh - var(--nav-height))); min-height: min(500px, calc(100svh - var(--nav-height))); display: flex; flex-direction: column; justify-content: center; border-top: none; border-bottom: 0; padding: clamp(1.6rem, 4vh, 3.1rem) 0; background: var(--color-primary-deep); }
.landing-root .hero-colorwash {
  position: absolute;
  inset: -8%;
  z-index: -3;
  opacity: 0;
  background-position: center;
  background-size: cover;
  filter: blur(40px) saturate(.86) brightness(.48);
  transform: scale(1.08);
  transition: opacity 900ms ease;
}
.landing-root .hero-colorwash.active { opacity: 1; }
.landing-root .hero-scrim { position: absolute; inset: 0; }
.landing-root .hero-image {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: auto;
  aspect-ratio: 1402 / 1122;
  opacity: 0;
  background-size: 100% 100%;
  background-position: center;
  background-repeat: no-repeat;
  -webkit-mask-image: linear-gradient(90deg, transparent 0%, rgba(0, 0, 0, .05) 7%, rgba(0, 0, 0, .18) 16%, rgba(0, 0, 0, .42) 26%, rgba(0, 0, 0, .72) 34%, #000 44%);
  mask-image: linear-gradient(90deg, transparent 0%, rgba(0, 0, 0, .05) 7%, rgba(0, 0, 0, .18) 16%, rgba(0, 0, 0, .42) 26%, rgba(0, 0, 0, .72) 34%, #000 44%);
  filter: saturate(1.05) contrast(1.02);
  transform: scale(1.015);
  transform-origin: right center;
  transition: opacity 900ms ease, transform 7s ease;
  z-index: -2;
}
.landing-root .hero-image.active { opacity: 1; transform: scale(1); }
/* Neutral dark-to-clear scrim (not a colour wash) so the photo reads true on the
   right; it clears by ~58% of the width so the subject isn't hidden behind text
   on the left. A soft bottom fade keeps the dot/arrow controls legible too. */
.landing-root .hero-scrim {
  z-index: -1;
  background:
    linear-gradient(90deg, rgba(3, 12, 11, .76) 0%, rgba(3, 12, 11, .56) 30%, rgba(3, 12, 11, .22) 45%, rgba(3, 12, 11, 0) 57%),
    linear-gradient(0deg, rgba(3, 12, 11, .35) 0%, rgba(3, 12, 11, 0) 22%);
}
.landing-root .hero-visual .wrap { position: relative; z-index: 1; width: 100%; }
.landing-root .hero-copy { max-width: 34rem; }

.landing-root .hero-slide { min-height: clamp(9rem, 30vh, 13rem); }
.landing-root .hero-fade-enter-active { transition: opacity 420ms ease, transform 420ms cubic-bezier(0.16, 1, 0.3, 1); }
.landing-root .hero-fade-leave-active { transition: opacity 260ms ease, transform 260ms ease; }
.landing-root .hero-fade-enter-from { opacity: 0; transform: translateY(14px); }
.landing-root .hero-fade-leave-to { opacity: 0; transform: translateY(-10px); }
.landing-root .hero-slide-icon {
  width: 42px; height: 42px; border-radius: 10px; display: grid; place-items: center;
  margin-bottom: var(--space-2); background: rgba(255, 255, 255, .16); color: #fff;
}
.landing-root .hero-slide-icon.accent { background: rgba(251, 191, 36, .22); color: #fde68a; }
.landing-root .hero h1 { font-size: var(--step-h1); margin-top: clamp(0.6rem, 2vh, var(--space-3)); max-width: 15ch; color: #fff; }
.landing-root .hero .lede { max-width: 46ch; font-size: var(--step-body-lg); color: rgba(255, 255, 255, .87); margin-top: clamp(0.6rem, 2vh, var(--space-3)); }
.landing-root .hero-visual .eyebrow { color: #fde68a; }
.landing-root .hero-ctas { display: flex; gap: var(--space-2); margin-top: clamp(0.9rem, 3vh, var(--space-4)); flex-wrap: wrap; }
.landing-root .hero-visual .btn-ghost { color: #fff; border-color: rgba(255, 255, 255, .55); }
.landing-root .hero-visual .btn-ghost:hover { background: rgba(255, 255, 255, .12); border-color: #fff; }
.landing-root .hero-dots { display: flex; gap: 9px; margin-top: clamp(0.5rem, 2vh, var(--space-4)); }
.landing-root .hero-dots button { position: relative; width: 44px; height: 36px; padding: 0; border: 0; background: transparent; cursor: pointer; overflow: hidden; }
.landing-root .hero-dots button::before { content: ''; position: absolute; inset: 16px 0; border-radius: 99px; background: rgba(255, 255, 255, .32); transition: background .2s ease; }
.landing-root .hero-dots button:hover::before { background: rgba(255, 255, 255, .5); }
.landing-root .hero-dots button:focus-visible,
.landing-root .hero-arrow:focus-visible { outline: 3px solid #fde68a; outline-offset: 3px; }
.landing-root .hero-dot-fill { position: absolute; inset: 16px 0; border-radius: 99px; background: #fbbf24; transform: scaleX(0); transform-origin: left center; animation-name: hero-dot-fill; animation-timing-function: linear; animation-fill-mode: forwards; will-change: transform; }
@keyframes hero-dot-fill { from { transform: scaleX(0); } to { transform: scaleX(1); } }
.landing-root .hero-facts { display: flex; gap: var(--space-4); margin-top: clamp(0.9rem, 3vh, var(--space-4)); flex-wrap: wrap; }
.landing-root .hero-fact { font-size: 0.85rem; color: rgba(255, 255, 255, .78); display: flex; align-items: baseline; gap: 0.4em; }
.landing-root .hero-fact strong { font-family: var(--font-mono); color: #fff; font-size: 0.95rem; }

@media (max-width: 960px) { .landing-root .hero-visual { height: min(520px, calc(100svh - var(--nav-height))); min-height: min(480px, calc(100svh - var(--nav-height))); } }
/* Short viewports (small laptop screens, or Windows display scaling above
   100% shrinking the effective CSS viewport): shrink the headline/lede so the
   whole hero -- including the trust-facts row -- fits without the bottom row
   landing mid-cut at the fold. Width-based sizing alone can't react to this;
   only viewport height tells us the fold is close. */
@media (max-height: 700px) and (min-width: 600px) {
  .landing-root .hero h1 { font-size: clamp(1.9rem, 1.3rem + 1.8vw, 2.6rem); max-width: 22ch; }
  .landing-root .hero .lede { font-size: 0.96rem; max-width: 58ch; }
  .landing-root .hero-slide-icon { width: 34px; height: 34px; margin-bottom: 0.4rem; }
  .landing-root .hero-fact { font-size: 0.78rem; }
}
@media (prefers-reduced-motion: reduce) {
  .landing-root .hero-colorwash,
  .landing-root .hero-image,
  .landing-root .hero-slide,
  .landing-root .hero-fade-enter-active,
  .landing-root .hero-fade-leave-active { transition: none; animation: none; }
  .landing-root .hero-dot-fill { animation: none; transform: scaleX(1); }
}

/* Prev/next arrows */
.landing-root .hero-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, .4);
  background: rgba(8, 30, 27, .35);
  backdrop-filter: blur(6px);
  color: #fff;
  display: grid;
  place-items: center;
  cursor: pointer;
  transition: background .15s ease, border-color .15s ease, transform .15s ease;
}
.landing-root .hero-arrow:hover { background: rgba(8, 30, 27, .6); border-color: #fff; }
.landing-root .hero-arrow:active { transform: translateY(-50%) scale(.94); }
.landing-root .hero-arrow.prev { left: 20px; }
.landing-root .hero-arrow.next { right: 20px; }
@media (max-width: 780px) {
  .landing-root .hero-arrow { display: none; }
  .landing-root .hero-image {
    inset: 0;
    width: auto;
    aspect-ratio: auto;
    background-size: 100% auto;
    background-position: center;
    -webkit-mask-image: linear-gradient(180deg, transparent 0%, #000 14%, #000 86%, transparent 100%);
    mask-image: linear-gradient(180deg, transparent 0%, #000 14%, #000 86%, transparent 100%);
    transform-origin: center;
  }
  .landing-root .hero-scrim { background: linear-gradient(90deg, rgba(3, 12, 11, .9) 0%, rgba(3, 12, 11, .72) 72%, rgba(3, 12, 11, .42) 100%); }
  .landing-root .hero-copy { max-width: min(34rem, 92%); }
}

/* Mission */
.landing-root .mission .wrap { display: grid; grid-template-columns: 0.9fr 1.1fr; gap: var(--space-5); }
.landing-root .mission .wrap > * { min-width: 0; }
@media (max-width: 860px) { .landing-root .mission .wrap { grid-template-columns: 1fr; } }
.landing-root .mission h2 { font-size: var(--step-h2); }
.landing-root .mission-body { color: var(--color-text-muted); font-size: var(--step-body-lg); }
.landing-root .mission-body p + p { margin-top: var(--space-3); }
.landing-root .mission-body strong { color: var(--color-text); font-weight: 600; }

/* How it works -- a flat belt of cards drifting sideways in a continuous,
   seamless loop (no perspective, no tilt) -- a conveyor, not a globe. */
.landing-root .how-carousel { margin-top: var(--space-4); }
.landing-root .how-carousel-viewport {
  overflow: hidden;
  margin-inline: auto;
  max-width: 100%;
  padding-block: 8px;
}
.landing-root .how-carousel-track {
  display: flex;
  gap: 24px;
  width: max-content;
  will-change: transform;
}
.landing-root .how-card {
  position: relative;
  flex: 0 0 auto;
  width: 340px;
  height: 300px;
  overflow: hidden;
  border-radius: var(--radius-card);
  background: var(--color-primary-deep);
  box-shadow: 0 24px 46px -20px var(--shadow-color);
}
.landing-root .how-card-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center;
  transform: scale(1.02);
}
.landing-root .how-card-scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(4, 12, 11, 0) 32%, rgba(4, 12, 11, .74) 74%, rgba(3, 9, 8, .92) 100%);
}
.landing-root .how-card-copy {
  position: absolute;
  inset: auto 0 0 0;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 1.1rem 1.2rem;
  color: #fff;
}
.landing-root .how-card-num { font-family: var(--font-mono); font-size: 0.7rem; font-weight: 700; color: #fde68a; }
.landing-root .how-card-copy h3 { font-size: 1.02rem; line-height: 1.22; margin: 0; color: #fff; }
.landing-root .how-card-copy p {
  margin: 0;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  text-overflow: ellipsis;
  font-size: 0.78rem;
  line-height: 1.4;
  color: rgba(255, 255, 255, .82);
}
.landing-root .how-carousel-controls { display: flex; align-items: center; justify-content: center; gap: 0.75rem; margin-top: var(--space-4); }
.landing-root .how-carousel-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: 1px solid var(--color-line);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-text-muted);
  cursor: pointer;
  transition: border-color 180ms ease, color 180ms ease;
}
.landing-root .how-carousel-btn:hover { border-color: var(--color-primary); color: var(--color-primary); }
.landing-root .how-carousel-btn:focus-visible { outline: 3px solid var(--color-primary); outline-offset: 2px; }
@media (max-width: 820px) {
  .landing-root .how-card { width: 280px; height: 320px; }
}
@media (max-width: 390px) {
  .landing-root .how-card { width: 240px; height: 340px; }
}

/* Platform — one field workflow, followed by the capabilities it supports. */
.landing-root #platform { background: #fbfcfb; }
.landing-root .platform-heading {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  align-items: end;
  gap: var(--space-5);
  margin-bottom: var(--space-5);
}
.landing-root .platform-heading .section-title { max-width: 18ch; }
.landing-root .platform-heading p {
  max-width: 52ch;
  color: var(--color-text-muted);
  font-size: var(--step-body-lg);
}
.landing-root .platform-layout {
  display: grid;
  grid-template-columns: minmax(300px, 0.86fr) minmax(0, 1.45fr);
  gap: clamp(2rem, 5vw, 4.5rem);
  align-items: stretch;
}
.landing-root .platform-offline {
  position: relative;
  min-height: 520px;
  display: flex;
  flex-direction: column;
  padding: clamp(1.5rem, 3vw, 2.25rem);
  border-radius: var(--radius-card);
  background: url('/platform/offline-field-officer.webp') center 18% / cover no-repeat, var(--color-primary-deep);
  color: #fff;
  box-shadow: 0 30px 60px -36px rgba(2, 31, 27, .88);
  transition: opacity 550ms ease, translate 550ms cubic-bezier(0.16, 1, 0.3, 1);
}
.landing-root .platform-offline::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(180deg, rgba(4, 22, 19, .74) 0%, rgba(5, 40, 34, .38) 38%, rgba(5, 40, 34, .3) 58%, rgba(3, 18, 15, .82) 100%);
}
.landing-root .platform-offline > * { position: relative; z-index: 1; }
.landing-root .platform-offline-heading h3,
.landing-root .platform-offline-heading p,
.landing-root .platform-flow b,
.landing-root .platform-flow span,
.landing-root .platform-offline-note { text-shadow: 0 1px 6px rgba(0, 0, 0, .6); }
.landing-root .platform-offline-heading { display: flex; align-items: flex-start; gap: var(--space-3); }
.landing-root .platform-symbol {
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 11px;
  background: #e5f4f1;
  color: var(--color-primary);
  font-size: 1.35rem;
}
.landing-root .platform-symbol.accent { background: #fff1dd; color: #d86400; }
.landing-root .platform-offline .platform-symbol { background: rgba(255,255,255,.12); color: #fff; }
.landing-root .platform-offline h3 { max-width: 17ch; color: #fff; font-size: var(--step-h3); }
.landing-root .platform-offline-heading p { margin-top: .75rem; color: rgba(255,255,255,.76); }
.landing-root .platform-flow {
  display: grid;
  grid-template-columns: 1fr auto 1fr auto 1fr;
  align-items: center;
  gap: .45rem;
  margin: auto 0;
  padding: var(--space-4) 0;
}
.landing-root .platform-flow > div { min-width: 0; display: grid; justify-items: center; gap: .28rem; text-align: center; }
.landing-root .platform-flow > div > i { font-size: 1.65rem; color: #6ee7c5; }
.landing-root .platform-flow b { font-size: .82rem; color: #fff; }
.landing-root .platform-flow span { max-width: 12ch; color: rgba(255,255,255,.58); font-size: .68rem; line-height: 1.35; }
.landing-root .platform-flow-arrow { color: rgba(255,255,255,.32); }
.landing-root .platform-offline-note {
  display: flex;
  align-items: flex-start;
  gap: .8rem;
  padding-top: var(--space-3);
  border-top: 1px solid rgba(255,255,255,.17);
  color: rgba(255,255,255,.72);
  font-size: .8rem;
}
.landing-root .platform-offline-note > i { flex: 0 0 auto; color: #6ee7c5; font-size: 1.3rem; }
.landing-root .platform-offline-note b { color: #fff; }
.landing-root .platform-groups { display: grid; align-content: stretch; }
.landing-root .platform-group {
  display: grid;
  grid-template-columns: minmax(175px, .78fr) minmax(0, 1.22fr);
  gap: clamp(1.2rem, 3vw, 2.5rem);
  padding: clamp(1.15rem, 2.2vw, 1.7rem) 0;
  border-top: 1px solid var(--color-line);
  transition: opacity 550ms ease, translate 550ms cubic-bezier(0.16, 1, 0.3, 1);
}
.landing-root .platform-group:last-child { border-bottom: 1px solid var(--color-line); }
.landing-root .platform-group header { display: flex; align-items: flex-start; gap: .85rem; }
.landing-root .platform-group h3 { font-size: 1.05rem; }
.landing-root .platform-group header p { margin-top: .35rem; color: var(--color-text-muted); font-size: .78rem; line-height: 1.45; }
.landing-root .platform-points { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-4); }
.landing-root .platform-points > div { display: flex; align-items: flex-start; gap: .7rem; min-width: 0; }
.landing-root .platform-points > div > i { flex: 0 0 auto; margin-top: .05rem; color: var(--color-primary); font-size: 1.3rem; }
.landing-root .platform-points p { display: grid; gap: .28rem; }
.landing-root .platform-points b { font-size: .83rem; line-height: 1.35; }
.landing-root .platform-points span { color: var(--color-text-muted); font-size: .76rem; line-height: 1.48; }
.landing-root .platform-assurance {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  margin-top: var(--space-4);
  padding: 1rem 0;
  border-top: 1px solid var(--color-line);
  color: var(--color-text-muted);
  font-size: .76rem;
  font-weight: 600;
}
.landing-root .platform-assurance span { display: inline-flex; align-items: center; gap: .5rem; }
.landing-root .platform-assurance i { color: var(--color-primary); font-size: 1.15rem; }

@media (max-width: 980px) {
  .landing-root .platform-heading { grid-template-columns: 1fr; gap: var(--space-3); }
  .landing-root .platform-layout { grid-template-columns: 1fr; gap: var(--space-4); }
  .landing-root .platform-offline { min-height: 390px; }
}
@media (max-width: 700px) {
  .landing-root .platform-group { grid-template-columns: 1fr; gap: var(--space-3); }
  .landing-root .platform-points { gap: var(--space-3); }
  .landing-root .platform-assurance { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 480px) {
  .landing-root .platform-offline { min-height: auto; }
  .landing-root .platform-flow { margin: 1rem 0; padding: .65rem 0; grid-template-columns: 1fr; justify-items: stretch; gap: .65rem; }
  .landing-root .platform-flow > div { grid-template-columns: 28px 54px 1fr; justify-items: start; align-items: center; text-align: left; }
  .landing-root .platform-flow span { max-width: none; }
  .landing-root .platform-flow-arrow { display: none; }
  .landing-root .platform-points { grid-template-columns: 1fr; }
  .landing-root .platform-assurance { grid-template-columns: 1fr; }
}

/* Product showcase */
.landing-root #showcase { background: var(--color-bg); }
.landing-root .showcase-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: var(--space-5);
}
.landing-root .product-stage {
  --product-preview-height: clamp(360px, 32vw, 460px);
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(190px, 0.9fr);
  align-items: start;
  gap: clamp(2rem, 4vw, 4rem);
  width: min(100%, 1080px);
  margin: var(--space-5) auto 0;
  background: transparent;
}
.landing-root .product-view {
  min-width: 0;
  margin: 0;
}
.landing-root .product-view-dashboard { width: 100%; }
.landing-root .product-frame {
  overflow: hidden;
  background: #f5f8f7;
  box-shadow: 0 30px 70px -34px rgba(2, 23, 20, 0.72);
}
.landing-root .dashboard-frame {
  height: var(--product-preview-height);
  border-radius: 12px;
  color: #17201e;
  font-size: clamp(5px, 0.55vw, 8px);
}
.landing-root .product-view-mobile {
  position: static;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.landing-root .mobile-frame {
  width: min(100%, 230px);
  height: var(--product-preview-height);
  border: 7px solid #111716;
  border-radius: 34px;
  box-shadow: 0 34px 72px -28px rgba(2, 23, 20, 0.82);
}

/* The web preview mirrors DefaultLayout.vue and DashboardPage.vue. */
.landing-root .preview-web-shell {
  display: grid;
  grid-template-columns: 18% minmax(0, 1fr);
  width: 100%;
  height: 100%;
  background: #f6f8f7;
  line-height: 1.25;
}
.landing-root .preview-web-nav {
  min-width: 0;
  padding: 1.8em 1.1em;
  background: #075f54;
  color: rgba(255, 255, 255, 0.82);
}
.landing-root .preview-web-nav > img { width: 78%; margin: 0 auto 2.2em; display: block; }
.landing-root .preview-web-nav small {
  display: block;
  margin: 1.6em 0 0.5em 0.8em;
  color: rgba(255, 255, 255, 0.48);
  font-size: 0.78em;
  font-weight: 700;
  letter-spacing: 0.07em;
  text-transform: uppercase;
}
.landing-root .preview-nav-item {
  display: flex;
  align-items: center;
  gap: 0.7em;
  min-height: 2.45em;
  padding: 0.55em 0.75em;
  border-radius: 7px;
  white-space: nowrap;
}
.landing-root .preview-nav-item i { width: 1.2em; font-size: 1.2em; }
.landing-root .preview-nav-item.active { background: #0d9488; color: #fff; font-weight: 700; }
.landing-root .preview-web-app { min-width: 0; background: #f8faf9; }
.landing-root .preview-web-toolbar {
  height: 7.5%;
  min-height: 28px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2em;
  background: #fff;
  color: #52615d;
  box-shadow: 0 4px 12px -10px rgba(2, 23, 20, 0.55);
}
.landing-root .preview-web-toolbar > span { display: flex; align-items: center; gap: 0.8em; font-weight: 700; }
.landing-root .preview-web-toolbar > div { display: flex; align-items: center; gap: 1em; }
.landing-root .preview-web-toolbar b { color: #a94c00; font-size: 0.88em; }
.landing-root .preview-avatar { width: 2.5em; height: 2.5em; border-radius: 50%; display: grid; place-items: center; background: #006b5b; color: #fff; font-weight: 700; }
.landing-root .preview-web-content { padding: 1.7em 2em 2em; }
.landing-root .preview-web-heading { display: flex; justify-content: space-between; align-items: start; gap: 1.5em; }
.landing-root .preview-web-heading > div:first-child { display: grid; }
.landing-root .preview-web-heading img { width: 9.5em; margin-bottom: 0.8em; }
.landing-root .preview-web-heading strong { color: #0f172a; font-size: 1.65em; letter-spacing: -0.02em; }
.landing-root .preview-web-heading span { margin-top: 0.35em; color: #64748b; }
.landing-root .preview-web-actions { display: flex; align-items: center; gap: 0.65em; white-space: nowrap; }
.landing-root .preview-web-actions span,
.landing-root .preview-web-actions b { padding: 0.7em 1em; border-radius: 6px; }
.landing-root .preview-web-actions span { color: #0f766e; }
.landing-root .preview-web-actions b { background: #e87918; color: #fff; font-size: 1em; }
.landing-root .preview-metric-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 1em; margin-top: 1.5em; }
.landing-root .preview-metric {
  position: relative;
  min-height: 8.9em;
  overflow: hidden;
  display: grid;
  align-content: center;
  padding: 1.3em 5.2em 1.3em 1.4em;
  border: 1px solid #dfe7e4;
  border-radius: 10px;
  background: #fff;
}
.landing-root .preview-metric > span { overflow: hidden; color: #64748b; font-size: 0.82em; font-weight: 700; letter-spacing: 0.04em; text-overflow: ellipsis; text-transform: uppercase; white-space: nowrap; }
.landing-root .preview-metric strong { margin-top: 0.3em; color: #0f172a; font-size: 1.75em; letter-spacing: -0.02em; }
.landing-root .preview-metric small { margin-top: 0.3em; color: #64748b; font-size: 0.8em; }
.landing-root .preview-metric > i {
  position: absolute;
  top: 50%;
  right: 1.1em;
  width: 3.4em;
  height: 3.4em;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: #d8f3ec;
  color: #006b5b;
  font-size: 1.25em;
  transform: translateY(-50%);
}
.landing-root .preview-metric.green > i { background: #d7f5e3; color: #18794e; }
.landing-root .preview-metric.amber > i { background: #ffe2c3; color: #a94c00; }
.landing-root .preview-analytics { display: grid; grid-template-columns: 1.45fr 1fr; gap: 1em; margin-top: 1em; }
.landing-root .preview-chart-card { min-width: 0; padding: 1.15em 1.3em 0.8em; border: 1px solid #dfe7e4; border-radius: 10px; background: #fff; }
.landing-root .preview-chart-card > strong { color: #0f172a; font-size: 1.05em; }
.landing-root .preview-chart-card svg { display: block; width: 100%; height: 9em; margin-top: 0.7em; overflow: visible; }
.landing-root .chart-gridline { fill: none; stroke: #e7eeeb; stroke-width: 1; }
.landing-root .chart-area { fill: rgba(13, 148, 136, 0.13); stroke: none; }
.landing-root .chart-line { fill: none; stroke: #0d9488; stroke-width: 2.2; stroke-linecap: round; stroke-linejoin: round; }
.landing-root .green-line { stroke: #18794e; }
.landing-root .preview-activity { margin-top: 1em; padding: 1.1em 1.3em; border: 1px solid #dfe7e4; border-radius: 10px; background: #fff; }
.landing-root .preview-activity > strong { display: block; margin-bottom: 0.65em; color: #0f172a; font-size: 1.05em; }
.landing-root .preview-activity > div { display: grid; grid-template-columns: 1fr auto auto; align-items: center; gap: 1.2em; padding: 0.45em 0; border-top: 1px solid #edf1ef; color: #52615d; }
.landing-root .preview-activity b { color: #17201e; }
.landing-root .preview-activity em { padding: 0.25em 0.65em; border-radius: 999px; background: #d7f5e3; color: #18794e; font-style: normal; font-weight: 700; }

/* The phone preview mirrors activity_home.xml, including its offline state,
   field-task hierarchy and anchored fingerprint action. */
.landing-root .preview-phone { height: 100%; display: grid; grid-template-rows: auto minmax(0, 1fr) auto; background: #f5f8f7; color: #17201e; font-size: clamp(5.8px, 0.52vw, 7.5px); line-height: 1.25; }
.landing-root .preview-phone-status { display: flex; justify-content: space-between; padding: 1.2em 2.5em 0.8em; background: #006b5b; color: #fff; }
.landing-root .preview-phone-scroll { min-height: 0; overflow: hidden; padding: 1.4em; }
.landing-root .preview-phone-hero { padding: 2em; border-radius: 15px; background: #006b5b; color: #fff; }
.landing-root .preview-phone-brand { display: flex; align-items: center; gap: 0.9em; }
.landing-root .preview-phone-brand img { width: 3.5em; height: 3.5em; }
.landing-root .preview-phone-brand b { flex: 1; font-size: 1.2em; }
.landing-root .preview-phone-brand i { font-size: 2em; }
.landing-root .preview-phone-hero h3 { margin-top: 1.2em; color: #fff; font-size: 2.6em; letter-spacing: -0.02em; }
.landing-root .preview-phone-hero p { margin-top: 0.25em; color: rgba(255, 255, 255, 0.8); font-size: 1.1em; }
.landing-root .preview-sync { display: flex; gap: 0.6em; align-items: center; margin-top: 1.1em; font-size: 1.05em; }
.landing-root .preview-phone-buttons { display: grid; grid-template-columns: 1.45fr 1fr; gap: 0.8em; margin-top: 1.5em; }
.landing-root .preview-phone-buttons b,
.landing-root .preview-phone-buttons span { display: flex; align-items: center; justify-content: center; gap: 0.55em; min-height: 3.7em; border-radius: 999px; }
.landing-root .preview-phone-buttons b { background: #fff; color: #004d42; }
.landing-root .preview-phone-buttons span { border: 1px solid rgba(255, 255, 255, 0.78); color: #fff; font-weight: 700; }
.landing-root .preview-phone-card { margin-top: 1.35em; padding: 1.4em; border-radius: 14px; background: #fff; box-shadow: 0 8px 20px -18px rgba(23, 32, 30, 0.7); }
.landing-root .preview-phone-card header { display: flex; align-items: center; justify-content: space-between; }
.landing-root .preview-phone-card header b { display: flex; align-items: center; gap: 0.6em; font-size: 1.25em; }
.landing-root .preview-phone-card header b i { color: #006b5b; font-size: 1.4em; }
.landing-root .preview-phone-card header > span { color: #006b5b; font-weight: 700; }
.landing-root .preview-phone-kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 0.4em; margin-top: 1.3em; }
.landing-root .preview-phone-kpis > div { display: grid; justify-items: center; text-align: center; }
.landing-root .preview-phone-kpis i { width: 3em; height: 3em; border-radius: 50%; display: grid; place-items: center; background: #d8f3ec; color: #006b5b; font-size: 1.3em; }
.landing-root .preview-phone-kpis strong { margin-top: 0.55em; font-size: 1.7em; }
.landing-root .preview-phone-kpis span { color: #52615d; font-size: 0.85em; }
.landing-root .preview-phone-kpis .amber i { background: #fff1d6; color: #a65300; }
.landing-root .preview-phone-kpis .green i { background: #d7f5e3; color: #18794e; }
.landing-root .preview-phone-kpis .orange i { background: #ffe2c3; color: #a94c00; }
.landing-root .donut-row { display: flex; align-items: center; gap: 1.4em; margin-top: 1.1em; }
.landing-root .donut-svg { flex: 0 0 auto; width: 4.4em; height: 4.4em; }
.landing-root .donut-legend { display: grid; gap: 0.6em; }
.landing-root .donut-legend span { display: flex; align-items: center; gap: 0.55em; color: #52615d; font-size: 0.92em; }
.landing-root .donut-legend b { margin-left: auto; color: #0f172a; font-size: 1.05em; }
.landing-root .donut-dot { width: 0.7em; height: 0.7em; border-radius: 50%; background: #0d9488; }
.landing-root .donut-dot.amber { background: #e2a33d; }
.landing-root .preview-phone-nav { position: relative; min-height: 7em; display: grid; grid-template-columns: repeat(5, 1fr); align-items: center; padding: 0.8em 0.7em 0.55em; background: #fff; box-shadow: 0 -8px 20px -18px rgba(23, 32, 30, 0.8); }
.landing-root .preview-phone-nav > div { display: grid; justify-items: center; gap: 0.3em; color: #52615d; }
.landing-root .preview-phone-nav > div i { font-size: 1.8em; }
.landing-root .preview-phone-nav > div span { font-size: 0.78em; }
.landing-root .preview-phone-nav > div.active { color: #006b5b; font-weight: 700; }
.landing-root .preview-phone-nav > b { width: 5.2em; height: 5.2em; margin: -3em auto 0; border-radius: 50%; display: grid; place-items: center; background: #006b5b; color: #fff; box-shadow: 0 12px 22px -12px rgba(0, 77, 66, 0.8); }
.landing-root .preview-phone-nav > b i { font-size: 2.2em; }
.landing-root .product-view figcaption {
  max-width: 58ch;
  margin-top: 1rem;
  color: var(--color-text-muted);
  font-size: 0.85rem;
  line-height: 1.55;
  text-align: left;
}
.landing-root .product-view figcaption span {
  display: block;
  margin-bottom: 0.25rem;
  color: var(--color-text);
  font-weight: 700;
}
.landing-root .product-view-mobile figcaption { width: min(100%, 230px); max-width: none; }

@media (max-width: 900px) {
  .landing-root .showcase-heading { align-items: start; flex-direction: column; gap: var(--space-2); }
  .landing-root .product-stage {
    grid-template-columns: 1fr;
    gap: var(--space-5);
    width: min(100%, 720px);
  }
  .landing-root .dashboard-frame { height: auto; aspect-ratio: 1.7; }
  .landing-root .mobile-frame,
  .landing-root .product-view-mobile figcaption { width: min(62%, 230px); }
  .landing-root .mobile-frame { height: auto; aspect-ratio: 0.52; }
}

@media (max-width: 700px) {
  .landing-root .product-view-dashboard,
  .landing-root .product-view-mobile {
    width: 100%;
  }
  .landing-root .product-view-dashboard { max-width: 680px; margin-inline: auto; }
  .landing-root .mobile-frame,
  .landing-root .product-view-mobile figcaption { width: min(72%, 240px); }
}

/* Request a demo */
.landing-root #demo { background: var(--color-surface-2); }
.landing-root .demo-grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-5); align-items: center; }
.landing-root .demo-grid > * { min-width: 0; }
@media (max-width: 860px) { .landing-root .demo-grid { grid-template-columns: 1fr; } }
.landing-root .demo-copy h2 { font-size: var(--step-h2); }
.landing-root .demo-points { list-style: none; margin: var(--space-3) 0 0; padding: 0; display: grid; gap: 0.45rem; font-size: 0.92rem; color: var(--color-text-muted); }
.landing-root .demo-points li { display: flex; gap: 0.5em; }
.landing-root .demo-points li::before { content: "✓"; color: var(--color-primary); font-weight: 700; flex-shrink: 0; }
.landing-root .demo-card { background: var(--color-surface); border: 1px solid var(--color-line); border-radius: var(--radius-card); padding: var(--space-4); box-shadow: 0 30px 60px -34px var(--shadow-color); transition: transform 240ms cubic-bezier(0.16, 1, 0.3, 1), box-shadow 240ms ease; }
.landing-root .demo-card:focus-within { transform: translateY(-3px); box-shadow: 0 34px 64px -34px var(--shadow-color); }
@media (hover: hover) and (pointer: fine) {
  .landing-root .demo-card:hover { transform: translateY(-4px); box-shadow: 0 34px 64px -34px var(--shadow-color); }
}
.landing-root .demo-form { display: grid; gap: var(--space-3); }
.landing-root .demo-form label { display: grid; gap: 0.35rem; font-size: 0.82rem; font-weight: 600; color: var(--color-text); }
.landing-root .demo-form input, .landing-root .demo-form textarea {
  font-family: var(--font-body); font-size: 0.92rem; font-weight: 400; color: var(--color-text);
  background: var(--color-bg); border: 1px solid var(--color-line); border-radius: 8px; padding: 0.7em 0.85em; width: 100%; resize: vertical;
}
.landing-root .demo-form input:focus, .landing-root .demo-form textarea:focus { outline: 2px solid var(--color-primary); outline-offset: 1px; border-color: var(--color-primary); }
.landing-root .demo-form input::placeholder, .landing-root .demo-form textarea::placeholder { color: #64748b; opacity: 1; }
.landing-root .demo-submit { justify-content: center; margin-top: 0.2rem; }
.landing-root .demo-thanks { text-align: center; padding: var(--space-3) 0; }
.landing-root .demo-thanks h4 { font-size: 1.2rem; margin-bottom: 0.4rem; }
.landing-root .demo-thanks p { color: var(--color-text-muted); font-size: 0.9rem; }
.landing-root .demo-thanks a { color: var(--color-primary); }
.landing-root .demo-check { width: 46px; height: 46px; margin: 0 auto var(--space-2); border-radius: 50%; background: var(--color-primary-soft); color: var(--color-primary-deep); display: grid; place-items: center; font-size: 1.4rem; font-weight: 700; }

/* CTA + footer */
.landing-root .cta-band { background: var(--color-primary-deep); color: #fff; text-align: left; }
.landing-root .cta-band .wrap { display: flex; justify-content: space-between; align-items: center; gap: var(--space-4); flex-wrap: wrap; }
.landing-root .cta-band h2 { font-size: var(--step-h2); color: #fff; max-width: 20ch; }
.landing-root .cta-band .lede { color: rgba(255, 255, 255, 0.75); margin-top: 0.5rem; max-width: 40ch; }

.landing-root footer {
  padding: var(--space-5) 0 var(--space-4);
  background: var(--color-accent);
  color: #1A1200;
}
.landing-root .foot-grid { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-4); flex-wrap: wrap; }
.landing-root .foot-brand .brand { margin-bottom: 0.5rem; }
.landing-root .foot-brand p { color: #1A1200; opacity: 0.82; font-size: 0.85rem; max-width: 38ch; }
.landing-root .foot-links { display: flex; gap: var(--space-5); flex-wrap: wrap; }
.landing-root .foot-col h5 { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.08em; color: #1A1200; opacity: 0.72; margin-bottom: 0.6rem; }
.landing-root .foot-col a { display: block; font-size: 0.88rem; text-decoration: none; color: #1A1200; opacity: 0.88; margin-bottom: 0.5rem; transition: opacity 150ms ease, transform 150ms ease; }
.landing-root .foot-col a:hover { opacity: 1; transform: translateX(3px); }
.landing-root .foot-col a:focus-visible { outline: 2px solid #1A1200; outline-offset: 3px; }
.landing-root .foot-bottom { margin-top: var(--space-5); padding-top: var(--space-3); border-top: 1px solid rgba(26, 18, 0, 0.24); display: flex; justify-content: space-between; font-size: 0.78rem; color: #1A1200; opacity: 0.78; flex-wrap: wrap; gap: 0.5rem; }

@media (max-width: 640px) {
  .landing-root .foot-grid { display: grid; grid-template-columns: 1fr; }
  .landing-root .foot-links { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-4); width: 100%; }
  .landing-root .foot-col:last-child { grid-column: 1 / -1; }
}

/* Reveal-on-scroll */
.landing-root .reveal { opacity: 0; transform: translateY(14px); transition: opacity 0.6s ease, transform 0.6s ease; }
.landing-root .reveal.in { opacity: 1; transform: none; }

/* Platform cards: direction-aware reveal that re-triggers on every pass --
   slides up from below while scrolling down, down from above while scrolling
   back up, set by JS via .enter-from-below / .enter-from-above. Uses the
   standalone `translate` property (not `transform`) so it composes cleanly
   with the card's own hover-lift transform instead of overriding it. */
.landing-root .feature-card-reveal { opacity: 0; translate: 0 36px; }
.landing-root .feature-card-reveal.enter-from-below { translate: 0 36px; }
.landing-root .feature-card-reveal.enter-from-above { translate: 0 -36px; }
.landing-root .feature-card-reveal.in { opacity: 1; translate: 0 0; }

@media (prefers-reduced-motion: reduce) {
  :global(html) { scroll-behavior: auto; }
  .landing-root .reveal { opacity: 1; transform: none; transition: none; }
  .landing-root .feature-card-reveal { opacity: 1; translate: 0 0; transition: none; }
  .landing-root .platform-offline,
  .landing-root .platform-group,
  .landing-root .kpi,
  .landing-root .demo-card,
  .landing-root .foot-col a { transition: none; }
  .landing-root .how-carousel-track { transition: none; }
}

/* Compact desktop rhythm for ordinary laptop/desktop heights at 100% zoom. */
@media (min-width: 1024px) and (max-height: 900px) {
  .landing-root {
    --step-body: 0.9rem;
    --step-body-lg: 1rem;
    --step-h1: clamp(2rem, 1.45rem + 2.15vw, 2.85rem);
    --step-h2: clamp(1.45rem, 1.2rem + 1vw, 1.95rem);
    --space-5: 2rem;
    --space-6: 2.55rem;
  }
  .landing-root .hero-visual { height: min(560px, calc(100svh - var(--nav-height))); min-height: min(520px, calc(100svh - var(--nav-height))); }
  .landing-root .hero-slide { min-height: 11.5rem; }
  .landing-root .hero-slide-icon { width: 38px; height: 38px; }
}

/* Component-level wide-screen overrides live after their base rules. Kept
   modest -- the previous version scaled these up enough that the page no
   longer fit at 100% zoom on an ordinary 1440-1920px display. */
@media (min-width: 1440px) {
  .landing-root .hero-copy { max-width: 36rem; }
}

@media (min-width: 2200px) {
  .landing-root .hero-copy { max-width: 37.5rem; }
  .landing-root .hero-visual { height: min(580px, calc(100svh - var(--nav-height))); min-height: min(540px, calc(100svh - var(--nav-height))); }
}
</style>
