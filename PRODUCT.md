# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

BioPay administrators operate at three scopes: the System Owner across the platform, Anchor Administrators across the organisations under one anchor, and Organisation Administrators within one organisation. They use the dashboard to understand programme activity, spot work requiring attention, and move into operational workflows.

## Product Purpose

BioPay manages accountable cash-transfer and voucher programmes backed by household records, biometric verification, payment cycles, field operations, and audit controls. Success means administrators can understand the current state quickly and act within their permitted scope.

## Operating Context

Administrators work from a role- and permission-scoped web dashboard. Common information should appear consistently across admin roles, while organisation, officer, approval, and platform-wide information is shown only when the signed-in administrator can access it.

## Capabilities and Constraints

- Dashboard data is supplied by role-scoped metrics and six-month payment and household series.
- System and Anchor Administrators can compare organisations and inspect recent transactions.
- Organisation Administrators see only their organisation's programme data.
- Dashboard actions and information must continue to respect existing permissions and enabled modules.
- Existing BioPay terminology and the current Vue 3, Vuetify, Vite, and TypeScript stack are fixed.

## Brand Commitments

Preserve the BioPay name, logo, teal primary identity, orange action color, and direct operational voice already used throughout the application.

## Evidence on Hand

The repository contains the working web application, role and permission model, backend dashboard queries, real application terminology, and existing brand assets. No external benchmarks or customer claims should be invented.

## Product Principles

- Make operational state understandable at a glance.
- Reveal information according to role, permission, and enabled module.
- Keep financial totals accountable and traceable to detailed activity.
- Prefer compact, information-rich views over oversized decoration.
- Make the next useful action obvious without crowding the page.

## Accessibility & Inclusion

Maintain semantic structure, keyboard-visible interactions, readable contrast, responsive behavior, and reduced-motion support across the dashboard.
