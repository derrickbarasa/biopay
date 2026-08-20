<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'

const mobileNavOpen = ref(false)
const revealRoot = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null
let featureObserver: IntersectionObserver | null = null
let scrollDir: 'up' | 'down' = 'down'
let lastScrollY = 0

function trackScrollDir() {
  const y = window.scrollY
  if (y > lastScrollY + 2) scrollDir = 'down'
  else if (y < lastScrollY - 2) scrollDir = 'up'
  lastScrollY = y
}

// Nav hides the instant the page moves and drops back in once scrolling settles.
const navHidden = ref(false)
let navHideTimer: ReturnType<typeof setTimeout> | null = null

function handleNavScroll() {
  if (window.scrollY > 8) navHidden.value = true
  if (navHideTimer) clearTimeout(navHideTimer)
  navHideTimer = setTimeout(() => { navHidden.value = false }, 260)
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
  window.location.href = `mailto:demo@biopay.app?subject=${subject}&body=${body}`
  demoSubmitted.value = true
}

// Hero carousel -- five value props, each with a full-bleed background photo.
// Project-owned carousel images live in frontend/public/hero/ (landscape,
// 1800px+ wide works well as background-size:cover).
const heroSlides = [
  {
    icon: 'cash', tone: 'primary', eyebrow: 'Cash transfers',
    title: 'Cash assistance, delivered with certainty.',
    copy: 'Generate a payment cycle, verify each recipient by fingerprint or face, and disburse — with a maker-checker approval trail from anchor to organisation.',
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
    title: 'The right person gets paid — every single time.',
    copy: 'A live fingerprint or face scan checked against the enrolled template at the moment of payment, even with no signal to check it against a server.',
    image: '/hero/biometric-verification.webp',
  },
  {
    icon: 'dedup', tone: 'accent', eyebrow: 'Deduplication',
    title: 'One household, one record — no fraud.',
    copy: 'Every new registration is screened against existing records for the same name, phone number and location before it is ever accepted.',
    image: '/hero/deduplication.webp',
  },
  {
    icon: 'ai', tone: 'primary', eyebrow: 'An AI',
    title: 'Agent that never stops watching.',
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
    copy: 'A field officer captures the household offline: demographics, fingerprints, a photo, and the exact GPS location — down to village level.',
  },
  {
    title: 'Sync when signal returns', image: '/how-it-works/sync-when-signal-returns.webp',
    copy: "Every record sits queued on the device until a connection appears, then uploads on its own — no officer has to remember to press sync.",
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
const contactEmail = import.meta.env.VITE_CONTACT_EMAIL || 'biopay@money.com'

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

  heroTimerReduceMotion = reduceMotion
  restartHeroTimer()

  howPaused.value = reduceMotion
  howRafId = requestAnimationFrame(howTick)
  window.addEventListener('resize', onHowResize, { passive: true })
})

onBeforeUnmount(() => {
  observer?.disconnect()
  featureObserver?.disconnect()
  window.removeEventListener('scroll', trackScrollDir)
  window.removeEventListener('scroll', handleNavScroll)
  window.removeEventListener('resize', onHowResize)
  if (navHideTimer) clearTimeout(navHideTimer)
  if (heroTimer) clearInterval(heroTimer)
  if (howRafId) cancelAnimationFrame(howRafId)
})
</script>

<template>
  <div class="landing-root" ref="revealRoot">
    <header class="site-nav" :class="{ 'nav-hidden': navHidden && !mobileNavOpen }">
      <div class="wrap nav-row">
        <a class="brand" href="#top">
          <img src="/biopay_logo_horizontal.svg" alt="BioPay" class="brand-logo" />
        </a>
        <nav class="nav-links">
          <a href="#mission">Mission</a>
          <a href="#how">How it works</a>
          <a href="#platform">Platform</a>
          <a href="#showcase">Product</a>
        </nav>
        <div class="nav-actions">
          <router-link class="nav-login" to="/login">Log in</router-link>
          <a class="btn btn-primary btn-nav" href="#demo">Request a demo</a>
          <button class="nav-toggle" aria-label="Toggle navigation" @click="mobileNavOpen = !mobileNavOpen">☰</button>
        </div>
      </div>
      <nav v-if="mobileNavOpen" class="mobile-nav">
        <a href="#mission" @click="closeMobileNav">Mission</a>
        <a href="#how" @click="closeMobileNav">How it works</a>
        <a href="#platform" @click="closeMobileNav">Platform</a>
        <a href="#showcase" @click="closeMobileNav">Product</a>
        <a href="#demo" @click="closeMobileNav">Request a demo</a>
        <router-link to="/login" @click="closeMobileNav">Log in</router-link>
      </nav>
    </header>

    <main id="top">
      <!-- HERO -->
      <section class="hero hero-visual">
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
            <div class="kicker-line"><span class="eyebrow">Why it exists</span></div>
            <h2>Aid reaches whoever's in line. It should reach whoever it's for.</h2>
          </div>
          <div class="mission-body">
            <p>
              Cash and food-for-work programmes lose ground to the oldest problem in aid: proving a
              payment reached the person the programme actually registered. A name on a list can be
              claimed by anyone standing at the front of it.
            </p>
            <p>
              <strong>BioPay ties every registration to the beneficiary's own body</strong> — a
              fingerprint or a face — captured once in the field, checked again at the exact moment
              they're paid. No connectivity required to capture it, no way for someone else to stand
              in for them at disbursement.
            </p>
            <p>
              The structure mirrors how these programmes are actually run: an
              <strong>anchor</strong> (an organisation or lead financial partner) oversees one or
              more <strong>organisations/programmes</strong> (the NGOs actually running them),
              whose <strong>field officers</strong> do the registering and paying — down to the
              village, location, county and state the household lives in.
            </p>
          </div>
        </div>
      </section>

      <!-- HOW IT WORKS -->
      <section id="how">
        <div class="wrap">
          <span class="eyebrow">The cycle</span>
          <h2 style="margin-top: 0.6rem">From registration to reconciled payment</h2>

          <div class="how-carousel reveal">
            <div class="how-carousel-viewport">
              <div class="how-carousel-track" :style="howTrackStyle">
                <div v-for="(step, index) in howTrackItems" :key="`${step.title}-${index}`" class="how-card">
                  <img class="how-card-image" :src="step.image" :alt="step.title" loading="lazy" decoding="async" />
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
          <span class="eyebrow">Platform</span>
          <h2 style="margin-top: 0.6rem; max-width: 20ch">Built for the field first, the office second</h2>

          <div class="feature-grid">
            <div class="feature feature-card-reveal">
              <div class="feature-icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" style="stroke: var(--color-primary)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="6" y="2.5" width="12" height="19" rx="2.4" />
                  <path d="M10 18.5h4" />
                  <path d="M4 9l2.5 2.5L4 14" />
                  <path d="M20 9l-2.5 2.5L20 14" />
                </svg>
              </div>
              <span class="feature-tag">Mobile · offline</span>
              <h4>Works with no signal at all</h4>
              <p>Registration, fingerprint enrolment, photos and attendance all write to the
                device first. A background sync picks them up the moment a connection appears —
                the officer never waits on a spinner in the field.</p>
              <div class="feature-foot">→ queued locally, uploaded automatically</div>
            </div>

            <div class="feature feature-card-reveal">
              <div class="feature-icon accent">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" style="stroke: var(--color-accent-deep)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="9.5" cy="12" r="6.5" />
                  <circle cx="14.5" cy="12" r="6.5" />
                </svg>
              </div>
              <span class="feature-tag">Fraud prevention</span>
              <h4>Deduplication checker</h4>
              <p>Every new registration is screened against the existing record for the same
                name, phone number and location before it's accepted — catching the same
                household enrolled twice under two names.</p>
              <div class="mini-widget">
                <div class="row"><span>Candidate match</span><span class="match">94%</span></div>
                <div class="row"><span>Same phone number</span><span>Yes</span></div>
                <div class="row"><span>Distance apart</span><span>40 m</span></div>
              </div>
            </div>

            <div class="feature feature-card-reveal">
              <div class="feature-icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" style="stroke: var(--color-primary)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 21s7-6.4 7-12a7 7 0 1 0-14 0c0 5.6 7 12 7 12z" />
                  <circle cx="12" cy="9" r="2.4" />
                </svg>
              </div>
              <span class="feature-tag">Location</span>
              <h4>GPS on every capture</h4>
              <p>Registrations and payments are tagged with the device's GPS or network
                location — whichever fix is freshest — so a payment's location is never just a
                claim.</p>
              <div class="feature-foot num">4.8517°N, 31.5825°E</div>
            </div>

            <div class="feature feature-card-reveal">
              <div class="feature-icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" style="stroke: var(--color-primary)" stroke-width="1.6" stroke-linecap="round">
                  <path d="M12 3a7 7 0 0 1 7 7c0 3.5-1 6-1 8.5" />
                  <path d="M12 3a7 7 0 0 0-7 7c0 2 .3 3.6.8 5" />
                  <path d="M9 20c1.2-2 1.5-4.5 1.5-7A4.5 4.5 0 0 1 15 8.5c1 0 2 .3 2.7 1" />
                  <path d="M6.5 17c.8-1.7 1-3.8 1-6a4.5 4.5 0 0 1 4-4.5" />
                </svg>
              </div>
              <span class="feature-tag">Verification</span>
              <h4>Fingerprint payment</h4>
              <p>A live scan checked against the enrolled template at the point of payment — the
                same verification standard used for national ID and banking KYC, brought to the
                last mile.</p>
            </div>

            <div class="feature feature-card-reveal">
              <div class="feature-icon accent">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" style="stroke: var(--color-accent-deep)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M4 8V6a2 2 0 0 1 2-2h2" /><path d="M16 4h2a2 2 0 0 1 2 2v2" />
                  <path d="M20 16v2a2 2 0 0 1-2 2h-2" /><path d="M8 20H6a2 2 0 0 1-2-2v-2" />
                  <circle cx="9" cy="10" r="1" /><circle cx="15" cy="10" r="1" />
                  <path d="M9 15c.8.7 1.9 1 3 1s2.2-.3 3-1" />
                </svg>
              </div>
              <span class="feature-tag">Verification</span>
              <h4>Face recognition payment</h4>
              <p>Where a fingerprint scanner isn't practical — older beneficiaries, manual
                labour, injury — a face match against the enrolment photo confirms identity
                instead.</p>
            </div>

            <div class="feature feature-card-reveal">
              <div class="feature-icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" style="stroke: var(--color-primary)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="4" width="18" height="16" rx="2" /><path d="M3 9h18" />
                  <path d="M8 14.5l2 2 4-4.5" />
                </svg>
              </div>
              <span class="feature-tag">Attendance</span>
              <h4>Event and activity attendance</h4>
              <p>Clock beneficiaries in and out of a maternity workshop, an immunisation drive
                or a cash-for-work day the same way a payment is verified — biometric, GPS- and
                time-stamped, with no paper sign-in sheet to lose.</p>
              <div class="feature-foot">→ workshops · immunisation · cash-for-work</div>
            </div>

            <div class="feature feature-card-reveal">
              <div class="feature-icon accent">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" style="stroke: var(--color-accent-deep)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M4 8a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v1.5a2 2 0 0 0 0 5V16a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-1.5a2 2 0 0 0 0-5V8z" />
                  <path d="M14 6.5v11" stroke-dasharray="2 2.4" />
                </svg>
              </div>
              <span class="feature-tag">Vouchers</span>
              <h4>Voucher redemption</h4>
              <p>Print or issue a biometric-verified voucher a household redeems for food,
                goods or services at a partner vendor — every issue, redemption and void stays
                on one traceable ledger, not a paper stub.</p>
            </div>

            <div class="feature feature-card-reveal">
              <div class="feature-icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" style="stroke: var(--color-primary)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="3.5" width="18" height="17" rx="2.4" />
                  <path d="M8 8.5h8M8 12.5h8M8 16.5h5" />
                </svg>
              </div>
              <span class="feature-tag">Subscription</span>
              <h4>Simple per-anchor subscription</h4>
              <p>One subscription per anchor covers every organisation under it. A grace period
                gives time to renew before access pauses, renewal is a click for an anchor
                admin, and pricing is set per your agreement — not listed here.</p>
            </div>

            <div class="feature feature-card-reveal">
              <div class="feature-icon">
                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" style="stroke: var(--color-primary)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 3v2.4M12 18.6V21M3 12h2.4M18.6 12H21M5.6 5.6l1.7 1.7M16.7 16.7l1.7 1.7M5.6 18.4l1.7-1.7M16.7 7.3l1.7-1.7" />
                  <circle cx="12" cy="12" r="3.6" />
                </svg>
              </div>
              <span class="feature-tag">Intelligence</span>
              <h4>AI agent, watching the ledger</h4>
              <p>An agent reviews registrations and disbursements as they land, flagging the
                patterns a person reviewing thousands of rows would miss.</p>
              <div class="mini-widget">
                <div class="ai-line"><span class="ai-dot"></span><span>3 registrations shared
                  one GPS point in 24h — flagged for review before disbursement.</span></div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- SHOWCASE -->
      <section id="showcase">
        <div class="wrap">
          <span class="eyebrow">Product</span>
          <h2 style="margin-top: 0.6rem">One dashboard for oversight, one app for the field</h2>

          <div class="showcase-grid">
            <div>
              <div class="dash reveal">
                <div class="dash-top"><span class="dash-dot"></span><span class="dash-dot"></span><span class="dash-dot"></span>&nbsp;dashboard.biopay.app/dashboard</div>
                <div class="dash-body">
                  <div class="dash-nav">
                    <div class="item active">Dashboard</div>
                    <div class="item">Organizations</div>
                    <div class="item">Households</div>
                    <div class="item">Officers</div>
                    <div class="item">Payments</div>
                    <div class="item">Payroll</div>
                    <div class="item">Vouchers</div>
                    <div class="item">Settings</div>
                  </div>
                  <div class="dash-main">
                    <div class="dash-metric-grid">
                      <div class="dmetric">
                        <div class="dmetric-copy"><span class="lbl">Organizations</span><span class="num">2</span><span class="sub">Active programmes</span></div>
                        <div class="dmetric-icon teal">
                          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M4 21V9l8-5 8 5v12" /><path d="M9 21v-6h6v6" /><path d="M4 21h16" /></svg>
                        </div>
                      </div>
                      <div class="dmetric">
                        <div class="dmetric-copy"><span class="lbl">Households</span><span class="num">5</span><span class="sub">Approved records</span></div>
                        <div class="dmetric-icon blue">
                          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M3 11l9-7 9 7" /><path d="M5 10v10h14V10" /><path d="M9 20v-6h6v6" /></svg>
                        </div>
                      </div>
                      <div class="dmetric">
                        <div class="dmetric-copy"><span class="lbl">Value disbursed</span><span class="num">SSP 2.4M</span><span class="sub">Cash + vouchers</span></div>
                        <div class="dmetric-icon amber">
                          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="6" width="18" height="13" rx="2" /><path d="M3 10h18" /><circle cx="16" cy="14.5" r="1.4" /></svg>
                        </div>
                      </div>
                      <div class="dmetric">
                        <div class="dmetric-copy"><span class="lbl">Active officers</span><span class="num">2</span><span class="sub">Field officers enabled</span></div>
                        <div class="dmetric-icon blue">
                          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M12 3l7 3v6c0 5-3 7.5-7 9-4-1.5-7-4-7-9V6z" /><path d="M9 12l2 2 4-4.5" /></svg>
                        </div>
                      </div>
                    </div>
                    <div class="dash-chart-card">
                      <span class="chart-title">Household registration trend</span>
                      <div class="dash-chart">
                        <div class="bar" style="--h: 38%"><span class="v">2</span></div>
                        <div class="bar" style="--h: 58%"><span class="v">3</span></div>
                        <div class="bar" style="--h: 74%"><span class="v">4</span></div>
                        <div class="bar" style="--h: 91%"><span class="v">5</span></div>
                        <div class="bar" style="--h: 100%"><span class="v">6</span></div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <p class="showcase-caption">The web dashboard — for anchors and organisations to register, disburse and reconcile.</p>
            </div>

            <div>
              <div class="phone reveal">
                <div class="phone-screen">
                  <div class="phone-head">
                    <div class="welcome">Welcome, John Doe</div>
                    <div class="org">PARTNER · 1002 <span class="sync-pill">Synced</span></div>
                  </div>
                  <div class="phone-kpis">
                    <div class="phone-kpi">Households<b class="num">318</b></div>
                    <div class="phone-kpi">Pending sync<b class="num">6</b></div>
                    <div class="phone-kpi">Paid<b class="num">204</b></div>
                    <div class="phone-kpi">Pending pay<b class="num">12</b></div>
                  </div>
                  <div class="phone-menu">
                    <div class="phone-btn">Register household</div>
                    <div class="phone-btn alt">Sync now</div>
                    <div class="phone-btn outline">Attendance</div>
                    <div class="phone-btn outline">Payments</div>
                    <div class="phone-btn outline">Vouchers</div>
                    <div class="phone-btn outline">Reports</div>
                  </div>
                </div>
              </div>
              <p class="showcase-caption">The field agent app — offline-first, built to run all day on one battery.</p>
            </div>
          </div>
        </div>
      </section>

      <!-- REQUEST A DEMO -->
      <section id="demo">
        <div class="wrap demo-grid">
          <div class="demo-copy">
            <span class="eyebrow">Request a demo</span>
            <h2 style="margin-top: 0.6rem">See BioPay against your own programme</h2>
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
            <div v-if="demoSubmitted" class="demo-thanks">
              <div class="demo-check">✓</div>
              <h4>Thanks, {{ demo.name }}.</h4>
              <p>Your email app should have opened with your request ready to send. If it didn't,
                email us at <a href="mailto:demo@biopay.app">demo@biopay.app</a>.</p>
            </div>
            <form v-else class="demo-form" @submit.prevent="submitDemo">
              <label>Full name
                <input v-model="demo.name" type="text" required placeholder="Your name" />
              </label>
              <label>Organisation
                <input v-model="demo.organisation" type="text" placeholder="Anchor or organisation" />
              </label>
              <label>Work email
                <input v-model="demo.email" type="email" required placeholder="you@organisation.org" />
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
  --nav-height: 62px;

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
  background: var(--color-primary);
  color: #fff;
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.08);
}
.landing-root .btn-primary:hover { background: var(--color-primary-deep); transform: translateY(-1px); box-shadow: 0 6px 16px -6px var(--shadow-color); }
.landing-root .btn-nav { padding: 0.68em 1.15em; font-size: 0.9rem; }
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
  .landing-root .site-nav.nav-hidden { transform: none; opacity: 1; pointer-events: auto; }
}
.landing-root .nav-row { min-height: var(--nav-height); display: flex; align-items: center; justify-content: space-between; padding: 0.45rem 0; }
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
.landing-root .brand-logo { display: block; width: 164px; height: auto; }
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
.landing-root .nav-links a { text-decoration: none; color: var(--color-text-muted); }
.landing-root .nav-links a:hover { color: var(--color-text); }
.landing-root .nav-actions { display: flex; align-items: center; gap: var(--space-3); }
.landing-root .nav-login { font-size: 0.9rem; text-decoration: none; color: var(--color-text-muted); }
.landing-root .nav-login:hover { color: var(--color-text); }
.landing-root .nav-toggle { display: none; background: none; border: 1px solid var(--color-line); border-radius: var(--radius-tag); padding: 0.4rem 0.6rem; }
@media (max-width: 860px) {
  .landing-root .nav-links { display: none; }
  .landing-root .nav-toggle { display: inline-flex; }
}
@media (max-width: 480px) {
  .landing-root { --nav-height: 58px; }
  .landing-root .brand-logo { width: 138px; }
  .landing-root .nav-login { display: none; }
  .landing-root .nav-actions { gap: var(--space-1); }
  .landing-root .nav-actions .btn { padding: 0.75em 1em; font-size: 0.84rem; }
}
.landing-root .mobile-nav { display: none; flex-direction: column; align-items: flex-start; gap: 0; padding: 0 1.25rem 1rem; }
@media (max-width: 860px) {
  .landing-root .mobile-nav { display: flex; }
}
.landing-root .mobile-nav a { padding: 0.5rem 0; text-decoration: none; color: var(--color-text-muted); }

/* Hero -- full-bleed photo carousel. Backgrounds come from heroSlides[].image
   (frontend/public/hero/) at 1402x1122; cover keeps the section filled edge to
   edge with no gaps or seams (some crop on one axis is inherent to a full-bleed
   background -- the scrim keeps the text side readable regardless of photo). */
.landing-root .hero-visual { position: relative; isolation: isolate; overflow: hidden; min-height: 420px; display: flex; flex-direction: column; justify-content: center; border-top: none; border-bottom: 0; }
.landing-root .hero-image, .landing-root .hero-scrim { position: absolute; inset: 0; }
.landing-root .hero-image { opacity: 0; background-size: cover; background-position: center; background-color: var(--color-primary-deep); filter: saturate(1.2) contrast(1.05); transform: scale(1.03); transition: opacity 900ms ease, transform 7s ease; z-index: -2; }
.landing-root .hero-image.active { opacity: 1; transform: scale(1); }
.landing-root .hero-scrim { z-index: -1; background: radial-gradient(circle at 85% 18%, rgba(251, 191, 36, .34), transparent 26rem), linear-gradient(90deg, rgba(4, 47, 46, .92) 0%, rgba(15, 118, 110, .74) 45%, rgba(4, 47, 46, .28) 100%); }
.landing-root .hero-visual .wrap { position: relative; z-index: 1; width: 100%; }
.landing-root .hero-copy { max-width: 42rem; }

.landing-root .hero-slide { min-height: 13rem; }
.landing-root .hero-fade-enter-active { transition: opacity 420ms ease, transform 420ms cubic-bezier(0.16, 1, 0.3, 1); }
.landing-root .hero-fade-leave-active { transition: opacity 260ms ease, transform 260ms ease; }
.landing-root .hero-fade-enter-from { opacity: 0; transform: translateY(14px); }
.landing-root .hero-fade-leave-to { opacity: 0; transform: translateY(-10px); }
.landing-root .hero-slide-icon {
  width: 42px; height: 42px; border-radius: 10px; display: grid; place-items: center;
  margin-bottom: var(--space-2); background: rgba(255, 255, 255, .16); color: #fff;
}
.landing-root .hero-slide-icon.accent { background: rgba(251, 191, 36, .22); color: #fde68a; }
.landing-root .hero h1 { font-size: var(--step-h1); margin-top: var(--space-3); max-width: 15ch; color: #fff; }
.landing-root .hero .lede { max-width: 46ch; font-size: var(--step-body-lg); color: rgba(255, 255, 255, .87); margin-top: var(--space-3); }
.landing-root .hero-visual .eyebrow { color: #fde68a; }
.landing-root .hero-ctas { display: flex; gap: var(--space-2); margin-top: var(--space-4); flex-wrap: wrap; }
.landing-root .hero-visual .btn-ghost { color: #fff; border-color: rgba(255, 255, 255, .55); }
.landing-root .hero-visual .btn-ghost:hover { background: rgba(255, 255, 255, .12); border-color: #fff; }
.landing-root .hero-dots { display: flex; gap: 9px; margin-top: var(--space-4); }
.landing-root .hero-dots button { position: relative; width: 42px; height: 4px; padding: 0; border: 0; border-radius: 99px; background: rgba(255, 255, 255, .32); cursor: pointer; overflow: hidden; transition: background .2s ease; }
.landing-root .hero-dots button:hover { background: rgba(255, 255, 255, .5); }
.landing-root .hero-dot-fill { position: absolute; inset: 0; border-radius: inherit; background: #fbbf24; transform: scaleX(0); transform-origin: left center; animation-name: hero-dot-fill; animation-timing-function: linear; animation-fill-mode: forwards; will-change: transform; }
@keyframes hero-dot-fill { from { transform: scaleX(0); } to { transform: scaleX(1); } }
.landing-root .hero-facts { display: flex; gap: var(--space-4); margin-top: var(--space-4); flex-wrap: wrap; }
.landing-root .hero-fact { font-size: 0.85rem; color: rgba(255, 255, 255, .78); display: flex; align-items: baseline; gap: 0.4em; }
.landing-root .hero-fact strong { font-family: var(--font-mono); color: #fff; font-size: 0.95rem; }

@media (max-width: 960px) { .landing-root .hero-visual { min-height: 440px; } }
@media (prefers-reduced-motion: reduce) {
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
  height: 260px;
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
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  font-size: 0.78rem;
  line-height: 1.4;
  color: rgba(255, 255, 255, .82);
}
.landing-root .how-carousel-controls { display: flex; align-items: center; justify-content: center; gap: 0.75rem; margin-top: var(--space-4); }
.landing-root .how-carousel-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
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
  .landing-root .how-card { width: 280px; height: 280px; }
}
@media (max-width: 390px) {
  .landing-root .how-card { width: 240px; }
}

/* Feature grid */
.landing-root .feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: var(--color-line);
  border: 1px solid var(--color-line);
  margin-top: var(--space-5);
}
@media (max-width: 860px) { .landing-root .feature-grid { grid-template-columns: 1fr 1fr; } }
@media (max-width: 560px) { .landing-root .feature-grid { grid-template-columns: 1fr; } }
.landing-root .feature {
  position: relative;
  z-index: 0;
  background: var(--color-surface);
  padding: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  min-height: 190px;
  transition: transform 220ms cubic-bezier(0.16, 1, 0.3, 1),
    box-shadow 220ms cubic-bezier(0.16, 1, 0.3, 1),
    background-color 180ms ease,
    opacity 550ms ease,
    translate 550ms cubic-bezier(0.16, 1, 0.3, 1);
}
.landing-root .feature-icon { width: 42px; height: 42px; border-radius: 10px; background: var(--color-primary-soft); display: grid; place-items: center; margin-bottom: 0.2rem; }
.landing-root .feature-icon.accent { background: var(--color-accent-soft); }
.landing-root .feature-tag { font-family: var(--font-mono); font-size: 0.68rem; text-transform: uppercase; letter-spacing: 0.08em; color: var(--color-text-muted); }
.landing-root .feature h4 { font-size: 1.05rem; }
.landing-root .feature p { color: var(--color-text-muted); font-size: 0.9rem; flex: 1; }
.landing-root .feature-foot { font-family: var(--font-mono); font-size: 0.75rem; color: var(--color-primary); margin-top: auto; }

@media (hover: hover) and (pointer: fine) {
  .landing-root .feature:hover {
    z-index: 2;
    transform: translateY(-4px);
    background: var(--color-surface);
    box-shadow: 0 10px 30px rgba(0, 0, 0, 0.06);
  }
  .landing-root .feature:hover .feature-icon {
    transform: translateY(-2px);
  }
  .landing-root .feature:active { transform: translateY(-2px); }
}

.landing-root .feature-icon {
  transition: transform 220ms cubic-bezier(0.16, 1, 0.3, 1), box-shadow 220ms ease;
}

.landing-root .mini-widget { border: 1px solid var(--color-line); border-radius: 10px; padding: var(--space-3); background: var(--color-surface-2); font-size: 0.82rem; transition: transform 180ms ease, box-shadow 180ms ease; }
.landing-root .mini-widget .row { display: flex; justify-content: space-between; margin-top: 0.35em; }
.landing-root .mini-widget .row span:last-child { font-family: var(--font-mono); }
.landing-root .mini-widget .match { color: var(--color-danger); font-weight: 700; font-family: var(--font-mono); }
.landing-root .ai-line { display: flex; gap: 0.6em; align-items: flex-start; }
.landing-root .ai-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--color-accent); margin-top: 6px; flex-shrink: 0; }

@media (hover: hover) and (pointer: fine) {
  .landing-root .mini-widget:hover,
  .landing-root .dmetric:hover,
  .landing-root .phone-kpi:hover {
    transform: translateY(-3px);
    box-shadow: 0 12px 24px -20px var(--shadow-color);
  }
}

/* Showcase */
.landing-root .showcase-grid { display: grid; grid-template-columns: 1.55fr 1fr; gap: var(--space-5); margin-top: var(--space-5); align-items: start; }
.landing-root .showcase-grid > * { min-width: 0; }
@media (max-width: 900px) { .landing-root .showcase-grid { grid-template-columns: 1fr; } }

.landing-root .dash { background: var(--color-surface); border: 1px solid var(--color-line); border-radius: var(--radius-card); overflow: hidden; box-shadow: 0 30px 60px -30px var(--shadow-color); transition: transform 260ms cubic-bezier(0.16, 1, 0.3, 1), box-shadow 260ms ease; }
.landing-root .dash-top { background: var(--color-primary); color: #fff; padding: 0.7rem 1rem; display: flex; align-items: center; gap: 0.6rem; font-size: 0.78rem; }
.landing-root .dash-dot { width: 8px; height: 8px; border-radius: 50%; background: rgba(255, 255, 255, 0.5); }
.landing-root .dash-body { display: grid; grid-template-columns: 150px 1fr; min-height: 300px; }
@media (max-width: 620px) { .landing-root .dash-body { grid-template-columns: 1fr; } }
.landing-root .dash-nav { background: var(--color-primary-deep); color: rgba(255, 255, 255, 0.82); padding: 0.9rem 0.7rem; font-size: 0.78rem; display: flex; flex-direction: column; gap: 0.15rem; }
@media (max-width: 620px) { .landing-root .dash-nav { display: none; } }
.landing-root .dash-nav .item { padding: 0.45rem 0.6rem; border-radius: 7px; }
.landing-root .dash-nav .item.active { background: rgba(255, 255, 255, 0.14); color: #fff; font-weight: 600; }
.landing-root .dash-main { padding: 1rem 1.1rem; }
.landing-root .dash-metric-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 0.6rem; }
.landing-root .dmetric {
  border: 1px solid var(--color-line); border-radius: 10px; padding: 0.65rem 0.75rem;
  display: flex; align-items: center; justify-content: space-between; gap: 0.5rem;
  transition: transform 180ms ease, box-shadow 180ms ease;
}
.landing-root .dmetric-copy { display: flex; flex-direction: column; min-width: 0; }
.landing-root .dmetric-copy .lbl { font-size: 0.62rem; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.05em; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.landing-root .dmetric-copy .num { font-size: 1.05rem; font-weight: 700; margin-top: 0.1rem; }
.landing-root .dmetric-copy .sub { font-size: 0.62rem; color: var(--color-text-muted); margin-top: 0.1rem; }
.landing-root .dmetric-icon { flex-shrink: 0; width: 32px; height: 32px; border-radius: 50%; display: grid; place-items: center; }
.landing-root .dmetric-icon.teal { background: var(--color-primary-soft); color: var(--color-primary-deep); }
.landing-root .dmetric-icon.blue { background: #dbeafe; color: #2563eb; }
.landing-root .dmetric-icon.amber { background: var(--color-accent-soft); color: var(--color-accent-deep); }
.landing-root .dash-chart-card { border: 1px solid var(--color-line); border-radius: 10px; padding: 0.7rem 0.8rem 0.5rem; margin-top: 0.6rem; }
.landing-root .chart-title { font-size: 0.72rem; font-weight: 600; color: var(--color-text); }
.landing-root .dash-chart { display: flex; align-items: flex-end; gap: 0.5rem; height: 64px; margin-top: 0.7rem; padding: 0 0.2rem; }
.landing-root .dash-chart .bar { flex: 1; height: var(--h); min-height: 8px; border-radius: 5px 5px 2px 2px; background: linear-gradient(180deg, var(--color-primary) 0%, var(--color-primary-deep) 100%); position: relative; }
.landing-root .dash-chart .v { position: absolute; top: -1.15rem; left: 50%; transform: translateX(-50%); font-family: var(--font-mono); font-size: 0.62rem; color: var(--color-text-muted); }

.landing-root .phone { width: 240px; margin-inline: auto; border: 10px solid var(--color-text); border-radius: 34px; overflow: hidden; box-shadow: 0 30px 60px -30px var(--shadow-color); transition: transform 260ms cubic-bezier(0.16, 1, 0.3, 1), box-shadow 260ms ease; }
.landing-root .phone-screen { background: var(--color-surface); min-height: 430px; }
.landing-root .phone-head { background: var(--color-primary); color: #fff; padding: 1rem 0.9rem 1.4rem; }
.landing-root .phone-head .welcome { font-size: 0.85rem; font-weight: 700; }
.landing-root .phone-head .org { display: flex; align-items: center; gap: 0.4rem; font-size: 0.68rem; opacity: 0.85; margin-top: 2px; font-family: var(--font-mono); }
.landing-root .sync-pill { font-family: var(--font-sans, inherit); background: rgba(255, 255, 255, 0.18); color: #fff; border-radius: 999px; padding: 0.1em 0.55em; font-size: 0.6rem; letter-spacing: 0.03em; }
.landing-root .phone-kpis { display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; padding: 0.8rem; margin-top: -1rem; }
.landing-root .phone-kpi { background: var(--color-surface); border: 1px solid var(--color-line); border-radius: 9px; padding: 0.5rem 0.6rem; font-size: 0.68rem; box-shadow: 0 8px 16px -10px var(--shadow-color); transition: transform 180ms ease, box-shadow 180ms ease; }
.landing-root .phone-kpi b { display: block; font-size: 0.95rem; font-family: var(--font-mono); }
.landing-root .phone-menu { padding: 0 0.8rem; display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; margin-top: 0.3rem; }
.landing-root .phone-btn { background: var(--color-primary); color: #fff; border-radius: 8px; padding: 0.7rem 0.5rem; font-size: 0.68rem; text-align: center; font-weight: 600; }
.landing-root .phone-btn.alt { background: var(--color-accent); color: #1A1200; }
.landing-root .phone-btn.outline { background: var(--color-surface-2); color: var(--color-text); }

.landing-root .showcase-caption { margin-top: var(--space-3); font-size: 0.85rem; color: var(--color-text-muted); text-align: center; }

@media (hover: hover) and (pointer: fine) {
  .landing-root .dash:hover { transform: translateY(-5px); box-shadow: 0 36px 70px -34px var(--shadow-color); }
  .landing-root .phone:hover { transform: translateY(-5px) rotate(0.4deg); box-shadow: 0 36px 70px -34px var(--shadow-color); }
  .landing-root .dash:active,
  .landing-root .phone:active { transform: translateY(-2px) scale(0.996); }
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
.landing-root .foot-brand p { color: var(--color-primary-deep); opacity: 0.82; font-size: 0.85rem; max-width: 34ch; }
.landing-root .foot-links { display: flex; gap: var(--space-5); flex-wrap: wrap; }
.landing-root .foot-col h5 { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.08em; color: var(--color-primary-deep); opacity: 0.75; margin-bottom: 0.6rem; }
.landing-root .foot-col a { display: block; font-size: 0.88rem; text-decoration: none; color: var(--color-primary-deep); margin-bottom: 0.5rem; transition: color 150ms ease, transform 150ms ease; }
.landing-root .foot-col a:hover { color: #1A1200; transform: translateX(3px); }
.landing-root .foot-col a:focus-visible { outline: 2px solid var(--color-primary-deep); outline-offset: 3px; }
.landing-root .foot-bottom { margin-top: var(--space-5); padding-top: var(--space-3); border-top: 1px solid rgba(26, 18, 0, 0.24); display: flex; justify-content: space-between; font-size: 0.78rem; color: var(--color-primary-deep); opacity: 0.85; flex-wrap: wrap; gap: 0.5rem; }

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
  .landing-root .feature,
  .landing-root .feature-icon,
  .landing-root .mini-widget,
  .landing-root .dash,
  .landing-root .kpi,
  .landing-root .phone,
  .landing-root .phone-kpi,
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
  .landing-root .hero-visual { min-height: min(410px, calc(100svh - var(--nav-height))); }
  .landing-root .hero-slide { min-height: 11.5rem; }
  .landing-root .hero-slide-icon { width: 38px; height: 38px; }
}

/* Component-level wide-screen overrides live after their base rules. Kept
   modest -- the previous version scaled these up enough that the page no
   longer fit at 100% zoom on an ordinary 1440-1920px display. */
@media (min-width: 1440px) {
  .landing-root .hero-copy { max-width: 44rem; }
}

@media (min-width: 2200px) {
  .landing-root .hero-copy { max-width: 46rem; }
  .landing-root .hero-visual { min-height: clamp(440px, 46vh, 500px); }
}
</style>
