import { computed, onMounted, ref } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@/composables/useToast'

export interface AnchorOption {
  id: number
  anchorCode: string
  name: string
}

/**
 * Shared "which anchor am I looking at" state for the Super Admin, used on
 * every anchor-scoped operational page (Households, Payments, Payment
 * Cycles, Vouchers, Attendance, Field Officers). Mirrors the anchor-picker
 * pattern already established on OrganizationsPage/RolesPage/SubscriptionPage:
 * the Super Admin has total access everywhere, but must choose an anchor
 * before an anchor-scoped page shows anything, the same way an Anchor
 * Administrator must choose an organisation on the same pages (see each
 * page's own `organisationCode` filter, now required rather than optional
 * for that role).
 */
export interface UseAnchorScopeOptions {
  /** True for a "create a new record under this anchor" picker (Add Household and similar):
   *  a deactivated anchor can't be picked for new work, so it's left out entirely. False (the
   *  default) for a page-level view/filter scope, where a deactivated anchor must stay pickable
   *  so its already-existing records (payments, vouchers, households, ...) stay reviewable. */
  activeOnly?: boolean
}

export function useAnchorScope(options: UseAnchorScopeOptions = {}) {
  const auth = useAuthStore()
  const toast = useToast()

  const anchors = ref<AnchorOption[]>([])
  const selectedAnchorId = ref<number | null>(null)

  // Only the Super Admin ever needs to pick an anchor -- an Anchor
  // Administrator already has exactly one (their own, from the JWT).
  const anchorGateActive = computed(() => auth.isSystemAdmin)
  const anchorChosen = computed(() => !auth.isSystemAdmin || selectedAnchorId.value != null)

  async function loadAnchors() {
    if (!auth.isSystemAdmin) return
    try {
      const res = await dispatch<{ results: AnchorOption[] }>('GET_ANCHORS', {
        status: options.activeOnly ? 1 : undefined,
      })
      anchors.value = res.results ?? []
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Unable to load anchors')
    }
  }

  onMounted(loadAnchors)

  return { anchors, selectedAnchorId, anchorGateActive, anchorChosen, loadAnchors }
}
