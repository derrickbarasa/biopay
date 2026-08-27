import { ref, watch } from 'vue'
import { dispatch } from '@/api/client'
import { useAuthStore } from '@/stores/auth'

export interface OrgOption { organisationCode: string; name: string; anchorId?: number }

/**
 * Anchor -> organisation cascade for create dialogs (Field Officers, Households):
 * a Super Admin must choose an anchor before the organisation list narrows to it.
 * An Anchor Administrator only ever has their own anchor, so the anchor step is
 * skipped for them and the organisation list loads immediately, scoped server-side.
 */
export function useOrgCascade() {
  const auth = useAuthStore()
  const dialogAnchorId = ref<number | null>(null)
  const dialogOrganizations = ref<OrgOption[]>([])
  const loadingDialogOrgs = ref(false)

  async function loadDialogOrganizations(anchorId: number | null) {
    if (auth.isSystemAdmin && anchorId == null) {
      dialogOrganizations.value = []
      return
    }
    loadingDialogOrgs.value = true
    try {
      const res = await dispatch<{ results: OrgOption[] }>('GET_ORGANIZATIONS', {
        targetAnchorId: auth.isSystemAdmin ? anchorId : undefined,
      })
      dialogOrganizations.value = res.results ?? []
    } finally {
      loadingDialogOrgs.value = false
    }
  }

  // Opens the dialog fresh: pre-fills the anchor from whatever's already selected
  // on the page (if any) for a Super Admin, or loads straight away for an Anchor
  // Administrator, who never sees the anchor step at all.
  function resetDialogScope(prefillAnchorId: number | null = null) {
    dialogOrganizations.value = []
    if (auth.isSystemAdmin) {
      dialogAnchorId.value = prefillAnchorId
      if (prefillAnchorId != null) void loadDialogOrganizations(prefillAnchorId)
    } else {
      dialogAnchorId.value = null
      void loadDialogOrganizations(null)
    }
  }

  watch(dialogAnchorId, (id) => { if (auth.isSystemAdmin) void loadDialogOrganizations(id) })

  return { dialogAnchorId, dialogOrganizations, loadingDialogOrgs, resetDialogScope }
}
