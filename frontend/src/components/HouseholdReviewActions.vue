<script setup lang="ts">
import { computed, ref } from 'vue'
import { dispatch } from '@/api/client'
import { useToast } from '@/composables/useToast'
import { householdIsPendingReview } from '@/utils/householdReview'

const props = withDefaults(defineProps<{
  householdNumber: string
  householdName: string
  reviewStatus?: string | null
  compact?: boolean
}>(), {
  reviewStatus: 'PENDING',
  compact: false,
})

const emit = defineEmits<{ updated: [] }>()
const toast = useToast()
const approving = ref(false)
const rejecting = ref(false)
const rejectDialog = ref(false)
const rejectionReason = ref('')
const isPending = computed(() => householdIsPendingReview(props.reviewStatus))

async function approve() {
  approving.value = true
  try {
    await dispatch('SET_HOUSEHOLD_REVIEW_STATUS', {
      householdNumber: props.householdNumber,
      reviewStatus: 'APPROVED',
    })
    toast.success(`${props.householdName} approved`)
    emit('updated')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Approval failed')
  } finally {
    approving.value = false
  }
}

function openReject() {
  rejectionReason.value = ''
  rejectDialog.value = true
}

async function reject() {
  const reason = rejectionReason.value.trim()
  if (!reason) {
    toast.error('Enter a reason for rejecting this household')
    return
  }
  rejecting.value = true
  try {
    await dispatch('SET_HOUSEHOLD_REVIEW_STATUS', {
      householdNumber: props.householdNumber,
      reviewStatus: 'REJECTED',
      rejectionReason: reason,
    })
    toast.success(`${props.householdName} rejected`)
    rejectDialog.value = false
    emit('updated')
  } catch (err) {
    toast.error(err instanceof Error ? err.message : 'Rejection failed')
  } finally {
    rejecting.value = false
  }
}
</script>

<template>
  <div v-if="isPending" class="household-review-actions" :class="{ 'household-review-actions--compact': compact }">
    <template v-if="compact">
      <v-tooltip text="Approve" location="top">
        <template #activator="{ props: tip }">
          <v-btn
            v-bind="tip"
            icon="mdi-check-circle-outline"
            variant="tonal"
            color="success"
            density="comfortable"
            size="small"
            :loading="approving"
            :disabled="rejecting"
            :aria-label="`Approve ${householdName}`"
            @click="approve"
          />
        </template>
      </v-tooltip>
      <v-tooltip text="Reject" location="top">
        <template #activator="{ props: tip }">
          <v-btn
            v-bind="tip"
            icon="mdi-close-circle-outline"
            variant="tonal"
            color="error"
            density="comfortable"
            size="small"
            :disabled="approving"
            :aria-label="`Reject ${householdName}`"
            @click="openReject"
          />
        </template>
      </v-tooltip>
    </template>
    <template v-else>
      <v-btn
        color="success"
        variant="tonal"
        prepend-icon="mdi-check-circle-outline"
        :loading="approving"
        :disabled="rejecting"
        @click="approve"
      >
        Approve
      </v-btn>
      <v-btn
        color="error"
        variant="tonal"
        prepend-icon="mdi-close-circle-outline"
        :disabled="approving"
        @click="openReject"
      >
        Reject
      </v-btn>
    </template>

    <v-dialog v-model="rejectDialog" max-width="440">
      <v-card>
        <dialog-close-button @close="rejectDialog = false" />
        <v-card-title>Reject {{ householdName }}?</v-card-title>
        <v-card-text class="pt-3">
          <v-textarea
            v-model="rejectionReason"
            label="Reason for rejection"
            placeholder="Explain what needs to be corrected"
            rows="3"
            required
            autofocus
            hint="The reason will be visible with the household record."
            persistent-hint
            @keydown.ctrl.enter="reject"
          />
        </v-card-text>
        <v-card-actions class="pa-4 pt-0">
          <v-spacer />
          <v-btn variant="text" @click="rejectDialog = false">Cancel</v-btn>
          <v-btn color="error" :loading="rejecting" :disabled="!rejectionReason.trim()" @click="reject">
            Reject household
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.household-review-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.household-review-actions--compact {
  gap: 6px;
}
</style>
