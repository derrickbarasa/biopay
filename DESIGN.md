---
name: BioPay
description: A compact operational interface for accountable biometric payment programmes.
colors:
  primary-teal: "#0D9488"
  deep-teal: "#0F766E"
  action-orange: "#F59E0B"
  success-green: "#10B981"
  ink: "#0F172A"
  secondary-ink: "#64748B"
  canvas: "#F8FAFC"
  surface: "#FFFFFF"
  border: "#E2E8F0"
typography:
  headline:
    fontFamily: "Ubuntu, sans-serif"
    fontSize: "clamp(1.45rem, 1.2rem + 0.8vw, 1.9rem)"
    fontWeight: 750
    lineHeight: 1.18
    letterSpacing: "-0.03em"
  body:
    fontFamily: "Ubuntu, sans-serif"
    fontSize: "0.9375rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Ubuntu, sans-serif"
    fontSize: "0.72rem"
    fontWeight: 700
    lineHeight: 1.3
rounded:
  control: "8px"
  surface: "10px"
  dashboard-panel: "14px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  section: "28px"
components:
  button-primary:
    backgroundColor: "{colors.action-orange}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.surface}"
---

# Design System: BioPay

## Overview

**Creative North Star: "The Accountability Ledger"**

BioPay is a restrained, information-dense operations interface. It should feel like a dependable programme ledger translated into a modern application: clear scopes, exact amounts, visible status, and immediate routes into work. Teal establishes ownership, orange identifies actions and attention, and quiet neutral surfaces keep long administrative sessions comfortable.

**Key Characteristics:**

- Compact, role-aware information density
- Teal navigation and identity with orange operational actions
- Flat white surfaces separated by crisp cool-gray borders
- Tabular alignment for financial values and counts
- Clear empty, loading, error, and permission-scoped states

## Colors

The palette is restrained: cool neutrals carry most of the interface, deep teal anchors identity and navigation, and orange is reserved for actions or attention.

**The Orange Means Action Rule.** Action Orange belongs to buttons and attention states; it is not general decoration.

**The Green Means Completion Rule.** Green communicates success, active state, or completed processing and must not replace the primary teal identity.

## Typography

Ubuntu is the established display and body face. Headlines are compact and strongly weighted; labels stay small but use sufficient weight and contrast for scanning.

**The Data Stays Still Rule.** Amounts, counts, dates, and ranking indices use tabular numerals so changing values do not disturb alignment.

## Layout

Authenticated screens use a fixed teal navigation rail and a cool-gray content canvas. Operational pages favor compact CSS grids: five summary columns at wide desktop widths, four and two columns through laptop/tablet breakpoints, and one column below 520px. Closely related dashboard groups use 10–12px gaps.

Dashboard composition follows summary → trends → detailed operations. Responsive reflow preserves that reading order and never introduces horizontal page overflow.

The authenticated content area is the vertical scroll container (`100dvh` with `overflow-y: auto`), keeping navigation and the app bar stable. It exposes a thin neutral scrollbar so long pages remain discoverable and operable by wheel, touch, keyboard, or drag.

## Elevation & Depth

The application is flat by default. White surfaces sit on the cool canvas and use a crisp one-pixel border; bordered cards do not also carry drop shadows. Overlays may use depth when separation from the underlying task is necessary.

**The One Boundary Rule.** A container uses either its established border or elevation to define depth, never both.

## Shapes

Controls use gently rounded 8px corners. Standard application surfaces use 10px corners; larger dashboard panels may use 14px where the additional scale warrants it. Pills are limited to chips, small statuses, and identity controls.

## Components

### Buttons

Primary operational actions use Action Orange with bold sentence-case labels. Text buttons remain visually quiet for refresh, navigation, and secondary actions. Every interactive control retains a visible focus state.

### Cards / Containers

Cards are white, border-led, and shadowless. Metric cards are compact and pair one small circular tonal icon with a label, value, and short factual detail. Charts and lists use a heading row inside the same surface rather than nested cards.

### Inputs / Fields

Inputs use Vuetify's comfortable outlined treatment, 8px corners, and a consistent two-pixel field outline variable. Error and disabled states retain the component library's semantic behavior.

### Navigation

The authenticated navigation rail uses Deep Teal, white iconography, compact rows, and a translucent white active state. Section labels are small uppercase utility text because they describe real navigation groups.

### Ranked Flow List

Organisation comparison uses exact values above six-pixel teal tracks. Tracks encode relative scale while the adjacent cash and voucher amounts preserve auditability; zero values remain visible without becoming visually dominant.

## Do's and Don'ts

### Do:

- **Do** show only data and actions the signed-in role can access.
- **Do** keep monetary values exact in supporting detail even when headline values are compacted.
- **Do** use concise empty-state copy that explains what activity will populate the view.
- **Do** collapse grids predictably at established breakpoints.

### Don't:

- **Don't** let a single chart or decorative illustration dominate an operational page.
- **Don't** repeat the same dataset as both a large chart and a full table without a distinct task reason.
- **Don't** use orange as ambient decoration or green as the primary brand color.
- **Don't** combine directional shadows with bordered cards.
