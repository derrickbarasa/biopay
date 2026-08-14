<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from 'vue'

const mobileNavOpen = ref(false)
const revealRoot = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

// Hero carousel -- five value props, each with a full-bleed background photo.
// Drop the actual files into frontend/public/hero/ using the filenames below
// (landscape, 1800px+ wide works well as background-size:cover) and they'll
// pick up automatically -- nothing else needs to change.
const heroSlides = [
  {
    icon: 'cash', tone: 'primary', eyebrow: 'Cash transfers',
    title: 'Cash assistance, delivered with certainty.',
    copy: 'Generate a payment cycle, verify each recipient by fingerprint or face, and disburse — with a maker-checker approval trail from anchor to organisation.',
    image: '/hero/cash-transfers.jpg',
  },
  {
    icon: 'voucher', tone: 'accent', eyebrow: 'Voucher redemption',
    title: 'Choice at the market, accountability at every step.',
    copy: 'Issue biometric-verified vouchers households can redeem with confidence, while every issue, redemption and void stays on a traceable ledger.',
    image: '/hero/voucher-redemption.jpg',
  },
  {
    icon: 'fingerprint', tone: 'primary', eyebrow: 'Biometric verification',
    title: 'The right person gets paid — every single time.',
    copy: 'A live fingerprint or face scan checked against the enrolled template at the moment of payment, even with no signal to check it against a server.',
    image: '/hero/biometric-verification.jpg',
  },
  {
    icon: 'dedup', tone: 'accent', eyebrow: 'Deduplication',
    title: 'One household, one record — fraud stopped before it starts.',
    copy: 'Every new registration is screened against existing records for the same name, phone number and location before it is ever accepted.',
    image: '/hero/deduplication.jpg',
  },
  {
    icon: 'ai', tone: 'primary', eyebrow: 'AI agent',
    title: 'An agent that never stops watching the ledger.',
    copy: 'Registrations and disbursements are reviewed as they land, flagging the patterns a person reviewing thousands of rows would miss.',
    image: '/hero/ai-agent.jpg',
  },
]
const activeHero = ref(0)
let heroTimer: ReturnType<typeof setInterval> | null = null
let heroTimerReduceMotion = false

function restartHeroTimer() {
  if (heroTimer) clearInterval(heroTimer)
  if (!heroTimerReduceMotion) {
    heroTimer = setInterval(() => setHero((activeHero.value + 1) % heroSlides.length), 5500)
  }
}

function setHero(index: number) {
  activeHero.value = index
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

  heroTimerReduceMotion = reduceMotion
  restartHeroTimer()
})

onBeforeUnmount(() => {
  observer?.disconnect()
  if (heroTimer) clearInterval(heroTimer)
})
</script>

<template>
  <div class="landing-root" ref="revealRoot">
    <header class="site-nav">
      <div class="wrap nav-row">
        <a class="brand" href="#top">
          <img src="/biopay_logo_horizontal.svg" alt="BioPay" class="brand-logo" />
        </a>
        <nav class="nav-links">
          <a href="#mission">Mission</a>
          <a href="#how">How it works</a>
          <a href="#platform">Platform</a>
          <a href="#showcase">Product</a>
          <a href="#pricing">Pricing</a>
        </nav>
        <div class="nav-actions">
          <router-link class="nav-login" to="/login">Log in</router-link>
          <a class="btn btn-primary" href="#pricing">Get a quote</a>
          <button class="nav-toggle" aria-label="Toggle navigation" @click="mobileNavOpen = !mobileNavOpen">☰</button>
        </div>
      </div>
      <nav v-if="mobileNavOpen" class="mobile-nav">
        <a href="#mission" @click="closeMobileNav">Mission</a>
        <a href="#how" @click="closeMobileNav">How it works</a>
        <a href="#platform" @click="closeMobileNav">Platform</a>
        <a href="#showcase" @click="closeMobileNav">Product</a>
        <a href="#pricing" @click="closeMobileNav">Pricing</a>
        <router-link to="/login" @click="closeMobileNav">Log in</router-link>
      </nav>
    </header>

    <main id="top">
      <!-- HERO -->
      <section class="hero hero-visual">
        <div
          v-for="(slide, index) in heroSlides" :key="slide.eyebrow"
          class="hero-image" :class="{ active: index === activeHero }"
          :style="{ backgroundImage: `url(${slide.image})` }"
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
            <div class="hero-ctas">
              <a class="btn btn-primary" href="#pricing">See pricing</a>
              <a class="btn btn-ghost" href="#how">How it works</a>
            </div>
            <div class="hero-dots" aria-label="Hero slides">
              <button
                v-for="(slide, index) in heroSlides" :key="slide.eyebrow"
                :class="{ active: index === activeHero }"
                :aria-label="`Show ${slide.eyebrow}`" @click="goToHero(index)"
              />
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
              <strong>anchor</strong> (a bank or a lead financial partner) oversees one or more
              <strong>organisations</strong> (the NGOs actually running programmes), whose
              <strong>field officers</strong> do the registering and paying — down to the boma,
              payam, county and state the household lives in.
            </p>
          </div>
        </div>
      </section>

      <!-- HOW IT WORKS -->
      <section id="how">
        <div class="wrap">
          <span class="eyebrow">The cycle</span>
          <h2 style="margin-top: 0.6rem">From registration to reconciled payment</h2>
          <div class="steps">
            <div class="step reveal">
              <div class="step-num num">01</div>
              <h4>Register in the field</h4>
              <p>A field officer captures the household offline: demographics, fingerprints, a
                photo, and the exact GPS location — down to boma level.</p>
            </div>
            <div class="step reveal">
              <div class="step-num num">02</div>
              <h4>Sync when signal returns</h4>
              <p>Every record sits queued on the device until a connection appears, then uploads
                on its own — no officer has to remember to press sync.</p>
            </div>
            <div class="step reveal">
              <div class="step-num num">03</div>
              <h4>Verify at disbursement</h4>
              <p>Fingerprint or face match confirms it's the registered person, not whoever's
                holding their ID card that day.</p>
            </div>
            <div class="step reveal">
              <div class="step-num num">04</div>
              <h4>Reconcile on the dashboard</h4>
              <p>Anchors and organisations see every payment, time- and location-stamped, ready
                for donor reporting.</p>
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
            <div class="feature reveal">
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

            <div class="feature reveal">
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

            <div class="feature reveal">
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

            <div class="feature reveal">
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

            <div class="feature reveal">
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

            <div class="feature reveal">
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
                <div class="dash-top"><span class="dash-dot"></span><span class="dash-dot"></span><span class="dash-dot"></span>&nbsp;dashboard.biopay.app/attendance</div>
                <div class="dash-body">
                  <div class="dash-nav">
                    <div class="item">Dashboard</div>
                    <div class="item">Organizations</div>
                    <div class="item">Households</div>
                    <div class="item">Officers</div>
                    <div class="item">Payments</div>
                    <div class="item">Payroll</div>
                    <div class="item active">Attendance</div>
                    <div class="item">Settings</div>
                  </div>
                  <div class="dash-main">
                    <div class="dash-kpis">
                      <div class="kpi"><span class="lbl">Total records</span><span class="num">1,284</span></div>
                      <div class="kpi"><span class="lbl">Clocked in</span><span class="num">742</span></div>
                      <div class="kpi"><span class="lbl">Clocked out</span><span class="num">611</span></div>
                    </div>
                    <div class="dash-table">
                      <div class="row head"><span>Beneficiary</span><span>Household</span><span>Clock</span></div>
                      <div class="row"><span>Achol Deng</span><span class="num">HH8K3M2Q</span><span><span class="status-pill paid">In</span></span></div>
                      <div class="row"><span>Nyibol Garang</span><span class="num">HH7F19XR</span><span><span class="status-pill paid">In</span></span></div>
                      <div class="row"><span>James Lado</span><span class="num">HH2P8H6T</span><span><span class="status-pill pending">Out</span></span></div>
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
                    <div class="welcome">Welcome, Peter Deng</div>
                    <div class="org">PARTNER · 1002</div>
                  </div>
                  <div class="phone-kpis">
                    <div class="phone-kpi">Households<b class="num">318</b></div>
                    <div class="phone-kpi">Pending sync<b class="num">6</b></div>
                    <div class="phone-kpi">Paid<b class="num">204</b></div>
                    <div class="phone-kpi">Pending pay<b class="num">12</b></div>
                  </div>
                  <div class="phone-menu">
                    <div class="phone-btn">Households</div>
                    <div class="phone-btn alt">Attendance</div>
                    <div class="phone-btn outline">Payments</div>
                    <div class="phone-btn outline">Reports</div>
                  </div>
                </div>
              </div>
              <p class="showcase-caption">The field agent app — offline-first, built to run all day on one battery.</p>
            </div>
          </div>
        </div>
      </section>

      <!-- PRICING -->
      <section id="pricing">
        <div class="wrap">
          <span class="eyebrow">Pricing</span>
          <h2 style="margin-top: 0.6rem">Plans start at $1,000. You only meter what you use.</h2>
          <p style="max-width: 56ch; color: var(--color-text-muted); margin-top: 0.75rem">
            Every plan is a fixed platform fee per anchor. Deduplication runs and AI agent review
            are usage-based on top of it, so a small pilot and a national rollout are never on
            the same bill by accident.
          </p>

          <div class="price-ledger">
            <div class="price-row featured">
              <div>
                <div class="price-tier-name">Starter</div>
                <div class="price-tier-note">One anchor, up to two organisations</div>
              </div>
              <div class="price-amount"><span class="cur">USD</span>1,000<span class="per">/mo</span></div>
              <ul class="price-includes">
                <li>Unlimited household registration</li>
                <li>Offline field agent app</li>
                <li>Fingerprint verification</li>
                <li>GPS tagging on every record</li>
                <li>Standard dashboard &amp; reporting</li>
                <li>Deduplication, metered per check</li>
              </ul>
            </div>
            <div class="price-row">
              <div>
                <div class="price-tier-name">Growth</div>
                <div class="price-tier-note">Multiple organisations under one anchor</div>
              </div>
              <div class="price-amount"><span class="cur">USD</span>2,500<span class="per">/mo</span></div>
              <ul class="price-includes">
                <li>Everything in Starter</li>
                <li>Face recognition verification</li>
                <li>Deduplication, fair-use volume included</li>
                <li>AI agent review, standard tuning</li>
                <li>Payroll maker-checker workflow</li>
                <li>Priority email support</li>
              </ul>
            </div>
            <div class="price-row">
              <div>
                <div class="price-tier-name">Enterprise</div>
                <div class="price-tier-note">National or multi-country programmes</div>
              </div>
              <div class="price-amount">Custom</div>
              <ul class="price-includes">
                <li>Everything in Growth</li>
                <li>Unlimited organisations</li>
                <li>AI agent tuned to your fraud patterns</li>
                <li>Dedicated onboarding &amp; migration</li>
                <li>SLA-backed support</li>
                <li>Custom data residency</li>
              </ul>
            </div>
          </div>

          <div class="metered-note">
            <span class="tag">Metered</span>
            <span>Deduplication runs and AI agent usage beyond your plan's included volume are
              billed monthly per unit consumed — ask for volume pricing if you're planning a
              large rollout.</span>
          </div>
        </div>
      </section>

      <!-- CTA BAND -->
      <section class="cta-band">
        <div class="wrap">
          <div>
            <h2>Ready to reach the people your programme is actually meant to serve?</h2>
            <p class="lede">Talk to us about your anchor, your organisations, and how many
              households you're registering this cycle.</p>
          </div>
          <a class="btn btn-primary" href="#top">Request a demo</a>
        </div>
      </section>
    </main>

    <footer>
      <div class="wrap">
        <div class="foot-grid">
          <div class="foot-brand">
            <a class="brand" href="#top"><img src="/biopay_logo_horizontal.svg" alt="BioPay" class="brand-logo" /></a>
            <p>Biometric registration and payment infrastructure for anchors and organisations
              running cash-transfer programmes.</p>
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
              <a href="#pricing">Pricing</a>
              <router-link to="/login">Log in</router-link>
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
  --color-bg: #F8FAFC;
  --color-surface: #FFFFFF;
  --color-surface-2: #F0FDFA;
  --color-text: #0F172A;
  --color-text-muted: #475569;
  --color-primary: #059669;
  --color-primary-deep: #047857;
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

  --step-caption: 0.75rem;
  --step-body: 1rem;
  --step-body-lg: 1.1875rem;
  --step-h4: 1.375rem;
  --step-h3: 1.75rem;
  --step-h2: clamp(1.75rem, 1.3rem + 1.6vw, 2.5rem);
  --step-h1: clamp(2.5rem, 1.6rem + 3.2vw, 4rem);

  --space-1: 0.5rem;
  --space-2: 0.75rem;
  --space-3: 1.25rem;
  --space-4: 2rem;
  --space-5: 3.25rem;
  --space-6: 5.25rem;

  --radius-tag: 3px;
  --radius-card: 14px;
  --max-width: 1180px;

  display: block;
  background: var(--color-bg);
  color: var(--color-text);
  font-family: var(--font-body);
  font-size: var(--step-body);
  line-height: 1.6;
  -webkit-font-smoothing: antialiased;
}

@media (prefers-color-scheme: dark) {
  .landing-root {
    --color-bg: #0B1613;
    --color-surface: #11201C;
    --color-surface-2: #16251F;
    --color-text: #EDEFE9;
    --color-text-muted: #9EB0AA;
    --color-primary: #35A886;
    --color-primary-deep: #1F7A61;
    --color-primary-soft: rgba(53, 168, 134, 0.14);
    --color-accent: #E8A23C;
    --color-accent-deep: #C9821F;
    --color-accent-soft: rgba(232, 162, 60, 0.14);
    --color-success: #45C07E;
    --color-danger: #E5686B;
    --color-line: rgba(237, 239, 233, 0.16);
    --shadow-color: rgba(0, 0, 0, 0.55);
  }
  .landing-root .eyebrow { color: var(--color-accent); }
  .landing-root .phone { border-color: #06110D; }
  .landing-root .price-includes li::before { color: var(--color-accent); }
  .landing-root .feature-foot { color: var(--color-accent); }
}

.landing-root * { box-sizing: border-box; }

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
.landing-root .btn-primary:hover { transform: translateY(-1px); box-shadow: 0 6px 16px -6px var(--shadow-color); }
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

/* Nav */
.landing-root .site-nav {
  position: sticky;
  top: 0;
  z-index: 40;
  background: color-mix(in srgb, var(--color-bg) 88%, transparent);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--color-line);
}
.landing-root .nav-row { display: flex; align-items: center; justify-content: space-between; padding: 0.9rem 0; }
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
.landing-root .brand-logo { display: block; width: 196px; height: auto; }
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
.landing-root .mobile-nav { display: none; flex-direction: column; align-items: flex-start; gap: 0; padding: 0 1.25rem 1rem; }
@media (max-width: 860px) {
  .landing-root .mobile-nav { display: flex; }
}
.landing-root .mobile-nav a { padding: 0.5rem 0; text-decoration: none; color: var(--color-text-muted); }

/* Hero -- full-bleed photo carousel. Backgrounds come from heroSlides[].image
   (frontend/public/hero/*.jpg); until those files exist the section still
   works, just showing the scrim gradient with no photo underneath. */
/* flex, not grid: a lone in-flow child (.wrap) inside an implicit CSS Grid column sizes that
   column to the child's content width (auto-track sizing), which then centers it as a narrow
   island instead of spanning full width -- flex's default align-items:stretch avoids that trap
   and reliably stretches .wrap to full width, letting its own max-width/margin:auto match the
   nav/every other section's alignment exactly. */
.landing-root .hero-visual { position: relative; isolation: isolate; overflow: hidden; min-height: 560px; display: flex; flex-direction: column; justify-content: center; border-top: none; border-bottom: 0; }
.landing-root .hero-image, .landing-root .hero-scrim { position: absolute; inset: 0; }
.landing-root .hero-image { opacity: 0; background-size: cover; background-position: center; background-color: var(--color-primary-deep); filter: saturate(1.2) contrast(1.05); transform: scale(1.03); transition: opacity 900ms ease, transform 7s ease; z-index: -2; }
.landing-root .hero-image.active { opacity: 1; transform: scale(1); }
.landing-root .hero-scrim { z-index: -1; background: radial-gradient(circle at 85% 18%, rgba(251, 191, 36, .34), transparent 26rem), linear-gradient(90deg, rgba(2, 44, 40, .92) 0%, rgba(2, 79, 68, .74) 45%, rgba(2, 44, 40, .28) 100%); }
.landing-root .hero-visual .wrap { position: relative; z-index: 1; width: 100%; }
/* .hero-grid deliberately does NOT set its own max-width/margin -- it inherits .wrap's
   1180px/auto-margin/padding exactly like the nav and every other section, so the hero's
   left edge lines up with the logo and every heading below it. Only .hero-copy caps its
   own reading width, without re-centering the whole block. */
.landing-root .hero-copy { max-width: 42rem; }

.landing-root .hero-slide { animation: hero-copy-in 550ms ease both; min-height: 15rem; }
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
.landing-root .hero-dots button { width: 26px; height: 4px; padding: 0; border: 0; border-radius: 99px; background: rgba(255, 255, 255, .38); cursor: pointer; transition: width .2s ease, background .2s ease; }
.landing-root .hero-dots button:hover { background: rgba(255, 255, 255, .6); }
.landing-root .hero-dots button.active { width: 42px; background: #fbbf24; }
.landing-root .hero-facts { display: flex; gap: var(--space-4); margin-top: var(--space-4); flex-wrap: wrap; }
.landing-root .hero-fact { font-size: 0.85rem; color: rgba(255, 255, 255, .78); display: flex; align-items: baseline; gap: 0.4em; }
.landing-root .hero-fact strong { font-family: var(--font-mono); color: #fff; font-size: 0.95rem; }

@keyframes hero-copy-in { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }
@media (max-width: 960px) { .landing-root .hero-visual { min-height: 520px; } }
@media (prefers-reduced-motion: reduce) { .landing-root .hero-image, .landing-root .hero-slide { transition: none; animation: none; } }

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
  /* Hero copy runs nearly full-width and taller than the section on narrow screens, so
     side arrows would sit on top of the paragraph text instead of beside it -- the dots
     below the CTAs remain as the nav control here. */
  .landing-root .hero-arrow { display: none; }
}

/* Mission */
.landing-root .mission .wrap { display: grid; grid-template-columns: 0.9fr 1.1fr; gap: var(--space-5); }
@media (max-width: 860px) { .landing-root .mission .wrap { grid-template-columns: 1fr; } }
.landing-root .mission h2 { font-size: var(--step-h2); }
.landing-root .mission-body { color: var(--color-text-muted); font-size: var(--step-body-lg); }
.landing-root .mission-body p + p { margin-top: var(--space-3); }
.landing-root .mission-body strong { color: var(--color-text); font-weight: 600; }

/* Steps */
.landing-root .steps { display: grid; grid-template-columns: repeat(4, 1fr); gap: var(--space-4); margin-top: var(--space-5); }
@media (max-width: 960px) { .landing-root .steps { grid-template-columns: 1fr 1fr; } }
@media (max-width: 560px) { .landing-root .steps { grid-template-columns: 1fr; } }
.landing-root .step-num {
  font-family: var(--font-mono);
  font-size: 0.78rem;
  color: var(--color-primary);
  border: 1px solid var(--color-line);
  border-radius: 999px;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  margin-bottom: var(--space-2);
}
.landing-root .step h4 { font-size: 1.05rem; margin-bottom: 0.4em; }
.landing-root .step p { color: var(--color-text-muted); font-size: 0.92rem; }

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
.landing-root .feature { background: var(--color-surface); padding: var(--space-4); display: flex; flex-direction: column; gap: var(--space-2); min-height: 190px; }
.landing-root .feature-icon { width: 42px; height: 42px; border-radius: 10px; background: var(--color-primary-soft); display: grid; place-items: center; margin-bottom: 0.2rem; }
.landing-root .feature-icon.accent { background: var(--color-accent-soft); }
.landing-root .feature-tag { font-family: var(--font-mono); font-size: 0.68rem; text-transform: uppercase; letter-spacing: 0.08em; color: var(--color-text-muted); }
.landing-root .feature h4 { font-size: 1.05rem; }
.landing-root .feature p { color: var(--color-text-muted); font-size: 0.9rem; flex: 1; }
.landing-root .feature-foot { font-family: var(--font-mono); font-size: 0.75rem; color: var(--color-primary); margin-top: auto; }

.landing-root .mini-widget { border: 1px solid var(--color-line); border-radius: 10px; padding: var(--space-3); background: var(--color-surface-2); font-size: 0.82rem; }
.landing-root .mini-widget .row { display: flex; justify-content: space-between; margin-top: 0.35em; }
.landing-root .mini-widget .row span:last-child { font-family: var(--font-mono); }
.landing-root .mini-widget .match { color: var(--color-danger); font-weight: 700; font-family: var(--font-mono); }
.landing-root .ai-line { display: flex; gap: 0.6em; align-items: flex-start; }
.landing-root .ai-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--color-accent); margin-top: 6px; flex-shrink: 0; }

/* Showcase */
.landing-root .showcase-grid { display: grid; grid-template-columns: 1.55fr 1fr; gap: var(--space-5); margin-top: var(--space-5); align-items: start; }
@media (max-width: 900px) { .landing-root .showcase-grid { grid-template-columns: 1fr; } }

.landing-root .dash { background: var(--color-surface); border: 1px solid var(--color-line); border-radius: var(--radius-card); overflow: hidden; box-shadow: 0 30px 60px -30px var(--shadow-color); }
.landing-root .dash-top { background: var(--color-primary); color: #fff; padding: 0.7rem 1rem; display: flex; align-items: center; gap: 0.6rem; font-size: 0.78rem; }
.landing-root .dash-dot { width: 8px; height: 8px; border-radius: 50%; background: rgba(255, 255, 255, 0.5); }
.landing-root .dash-body { display: grid; grid-template-columns: 150px 1fr; min-height: 300px; }
@media (max-width: 620px) { .landing-root .dash-body { grid-template-columns: 1fr; } }
.landing-root .dash-nav { background: var(--color-primary-deep); color: rgba(255, 255, 255, 0.82); padding: 0.9rem 0.7rem; font-size: 0.78rem; display: flex; flex-direction: column; gap: 0.15rem; }
@media (max-width: 620px) { .landing-root .dash-nav { display: none; } }
.landing-root .dash-nav .item { padding: 0.45rem 0.6rem; border-radius: 7px; }
.landing-root .dash-nav .item.active { background: rgba(255, 255, 255, 0.14); color: #fff; font-weight: 600; }
.landing-root .dash-main { padding: 1rem 1.1rem; }
.landing-root .dash-kpis { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0.6rem; }
@media (max-width: 480px) { .landing-root .dash-kpis { grid-template-columns: 1fr; } }
.landing-root .kpi { border: 1px solid var(--color-line); border-radius: 10px; padding: 0.7rem 0.8rem; }
.landing-root .kpi .lbl { font-size: 0.68rem; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: 0.06em; }
.landing-root .kpi .num { font-size: 1.25rem; font-weight: 700; display: block; margin-top: 0.15rem; }
.landing-root .dash-table { margin-top: 0.9rem; border: 1px solid var(--color-line); border-radius: 10px; overflow: hidden; font-size: 0.78rem; }
.landing-root .dash-table .row { display: grid; grid-template-columns: 1.2fr 1fr 0.7fr; padding: 0.5rem 0.7rem; }
.landing-root .dash-table .row + .row { border-top: 1px solid var(--color-line); }
.landing-root .dash-table .head { background: var(--color-surface-2); color: var(--color-text-muted); font-size: 0.68rem; text-transform: uppercase; letter-spacing: 0.05em; }
.landing-root .status-pill { font-family: var(--font-mono); font-size: 0.68rem; padding: 0.12em 0.55em; border-radius: 999px; display: inline-block; }
.landing-root .status-pill.paid { background: color-mix(in srgb, var(--color-success) 16%, transparent); color: var(--color-success); }
.landing-root .status-pill.pending { background: color-mix(in srgb, var(--color-accent) 18%, transparent); color: var(--color-accent-deep); }

.landing-root .phone { width: 240px; margin-inline: auto; border: 10px solid var(--color-text); border-radius: 34px; overflow: hidden; box-shadow: 0 30px 60px -30px var(--shadow-color); }
.landing-root .phone-screen { background: var(--color-surface); min-height: 430px; }
.landing-root .phone-head { background: var(--color-primary); color: #fff; padding: 1rem 0.9rem 1.4rem; }
.landing-root .phone-head .welcome { font-size: 0.85rem; font-weight: 700; }
.landing-root .phone-head .org { font-size: 0.68rem; opacity: 0.85; margin-top: 2px; font-family: var(--font-mono); }
.landing-root .phone-kpis { display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; padding: 0.8rem; margin-top: -1rem; }
.landing-root .phone-kpi { background: var(--color-surface); border: 1px solid var(--color-line); border-radius: 9px; padding: 0.5rem 0.6rem; font-size: 0.68rem; box-shadow: 0 8px 16px -10px var(--shadow-color); }
.landing-root .phone-kpi b { display: block; font-size: 0.95rem; font-family: var(--font-mono); }
.landing-root .phone-menu { padding: 0 0.8rem; display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem; margin-top: 0.3rem; }
.landing-root .phone-btn { background: var(--color-primary); color: #fff; border-radius: 8px; padding: 0.7rem 0.5rem; font-size: 0.68rem; text-align: center; font-weight: 600; }
.landing-root .phone-btn.alt { background: var(--color-accent); color: #1A1200; }
.landing-root .phone-btn.outline { background: var(--color-surface-2); color: var(--color-text); }

.landing-root .showcase-caption { margin-top: var(--space-3); font-size: 0.85rem; color: var(--color-text-muted); text-align: center; }

/* Pricing */
.landing-root .price-ledger { margin-top: var(--space-5); border: 1px solid var(--color-line); border-radius: var(--radius-card); overflow: hidden; background: var(--color-surface); }
.landing-root .price-row { display: grid; grid-template-columns: 1.1fr 0.7fr 2fr; gap: var(--space-4); padding: var(--space-4); align-items: center; }
.landing-root .price-row + .price-row { border-top: 1px solid var(--color-line); }
.landing-root .price-row.featured { background: var(--color-primary-soft); }
@media (max-width: 760px) { .landing-root .price-row { grid-template-columns: 1fr; gap: var(--space-2); } }
.landing-root .price-tier-name { font-family: var(--font-display); font-size: 1.2rem; font-weight: 700; }
.landing-root .price-tier-note { font-size: 0.8rem; color: var(--color-text-muted); margin-top: 0.2rem; }
.landing-root .price-amount { font-family: var(--font-mono); font-size: 1.6rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.landing-root .price-amount .cur { font-size: 0.95rem; vertical-align: 0.35em; margin-right: 0.1em; color: var(--color-text-muted); }
.landing-root .price-amount .per { font-size: 0.8rem; font-weight: 400; color: var(--color-text-muted); font-family: var(--font-body); }
.landing-root .price-includes { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 0.35rem 1rem; font-size: 0.86rem; }
@media (max-width: 480px) { .landing-root .price-includes { grid-template-columns: 1fr; } }
.landing-root .price-includes li { display: flex; gap: 0.5em; }
.landing-root .price-includes li::before { content: "—"; color: var(--color-primary); flex-shrink: 0; }

.landing-root .metered-note { margin-top: var(--space-3); border: 1px dashed var(--color-line); border-radius: 10px; padding: var(--space-3); font-size: 0.86rem; color: var(--color-text-muted); display: flex; gap: 0.7em; }
.landing-root .metered-note .tag { font-family: var(--font-mono); font-size: 0.68rem; text-transform: uppercase; letter-spacing: 0.08em; color: var(--color-accent-deep); background: var(--color-accent-soft); border-radius: 4px; padding: 0.2em 0.5em; height: fit-content; flex-shrink: 0; }

/* CTA + footer */
.landing-root .cta-band { background: var(--color-primary-deep); color: #fff; text-align: left; }
.landing-root .cta-band .wrap { display: flex; justify-content: space-between; align-items: center; gap: var(--space-4); flex-wrap: wrap; }
.landing-root .cta-band h2 { font-size: var(--step-h2); color: #fff; max-width: 20ch; }
.landing-root .cta-band .lede { color: rgba(255, 255, 255, 0.75); margin-top: 0.5rem; max-width: 40ch; }

.landing-root footer { padding: var(--space-5) 0 var(--space-4); }
.landing-root .foot-grid { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-4); flex-wrap: wrap; }
.landing-root .foot-brand .brand { margin-bottom: 0.5rem; }
.landing-root .foot-brand p { color: var(--color-text-muted); font-size: 0.85rem; max-width: 34ch; }
.landing-root .foot-links { display: flex; gap: var(--space-5); flex-wrap: wrap; }
.landing-root .foot-col h5 { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.08em; color: var(--color-text-muted); margin-bottom: 0.6rem; }
.landing-root .foot-col a { display: block; font-size: 0.88rem; text-decoration: none; color: var(--color-text); margin-bottom: 0.5rem; }
.landing-root .foot-col a:hover { color: var(--color-primary); }
.landing-root .foot-bottom { margin-top: var(--space-5); padding-top: var(--space-3); border-top: 1px solid var(--color-line); display: flex; justify-content: space-between; font-size: 0.78rem; color: var(--color-text-muted); flex-wrap: wrap; gap: 0.5rem; }

/* Reveal-on-scroll */
.landing-root .reveal { opacity: 0; transform: translateY(14px); transition: opacity 0.6s ease, transform 0.6s ease; }
.landing-root .reveal.in { opacity: 1; transform: none; }
@media (prefers-reduced-motion: reduce) {
  .landing-root .reveal { opacity: 1; transform: none; transition: none; }
}
</style>
