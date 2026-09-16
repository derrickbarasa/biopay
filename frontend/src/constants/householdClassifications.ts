export const VULNERABILITY_OPTIONS = [
  { title: 'Disability', value: 'DISABILITY' },
  { title: 'Elderly-headed household', value: 'ELDERLY_HEADED' },
  { title: 'Child-headed household', value: 'CHILD_HEADED' },
  { title: 'Chronic illness', value: 'CHRONIC_ILLNESS' },
  { title: 'Pregnant or lactating caregiver', value: 'PREGNANT_OR_LACTATING' },
  { title: 'Single caregiver', value: 'SINGLE_CAREGIVER' },
] as const

// Stored as the plain label itself (not a code) -- marital_status is a free-text column
// and the mobile field app (see mobile/agent .../strings.xml marital_status_options) writes
// these same literal words, so dashboard- and mobile-registered households read identically.
export const MARITAL_STATUS_OPTIONS = ['Single', 'Married', 'Widowed', 'Divorced', 'Separated'] as const

export const LEGAL_STATUS_OPTIONS = [
  { title: 'Citizen', value: 'CITIZEN' },
  { title: 'Refugee', value: 'REFUGEE' },
  { title: 'Internally displaced person (IDP)', value: 'IDP' },
  { title: 'Asylum seeker', value: 'ASYLUM_SEEKER' },
  { title: 'Returnee', value: 'RETURNEE' },
  { title: 'Host community', value: 'HOST_COMMUNITY' },
  { title: 'Stateless', value: 'STATELESS' },
  { title: 'Other', value: 'OTHER' },
] as const

const vulnerabilityLabels = new Map(VULNERABILITY_OPTIONS.map((option) => [option.value, option.title]))
const legalStatusLabels = new Map(LEGAL_STATUS_OPTIONS.map((option) => [option.value, option.title]))

export function vulnerabilityLabel(value: string) {
  return vulnerabilityLabels.get(value as (typeof VULNERABILITY_OPTIONS)[number]['value']) ?? value
}

export function legalStatusLabel(value?: string) {
  if (!value) return 'Not recorded'
  return legalStatusLabels.get(value as (typeof LEGAL_STATUS_OPTIONS)[number]['value']) ?? value
}

export const VULNERABILITY_FILTER_OPTIONS = [
  ...VULNERABILITY_OPTIONS,
  { title: 'Not recorded', value: 'NOT_RECORDED' },
]

export const LEGAL_STATUS_FILTER_OPTIONS = [
  ...LEGAL_STATUS_OPTIONS,
  { title: 'Not recorded', value: 'NOT_RECORDED' },
]
