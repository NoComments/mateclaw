<template>
  <!-- Top warning banner: shows when <= 7 days remaining -->
  <div v-if="showBanner" class="trial-banner" :class="bannerClass">
    <span class="trial-badge">{{ t('license.trialActive') }}</span>
    <span class="trial-text">{{ t('license.daysRemaining', { days: status?.daysRemaining ?? 0 }) }}</span>
  </div>

  <!-- Full-screen expired overlay -->
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="showOverlay" class="trial-overlay">
        <div class="trial-overlay-card">
          <div class="trial-overlay-icon">⏱</div>
          <h2 class="trial-overlay-title">{{ overlayTitle }}</h2>
          <p class="trial-overlay-message">{{ overlayMessage }}</p>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { http } from '@/api'

interface LicenseStatus {
  status: string
  customer: string
  expiresAt: string
  daysRemaining: number
  message: string
}

interface ApiEnvelope<T> {
  data: T
}

const { t } = useI18n()
const status = ref<LicenseStatus | null>(null)

const showBanner = computed(() => {
  if (!status.value) return false
  return status.value.status === 'active' && status.value.daysRemaining <= 7
})

const bannerClass = computed(() => {
  if (!status.value) return ''
  if (status.value.daysRemaining <= 3) return 'trial-banner--critical'
  return 'trial-banner--warning'
})

const showOverlay = computed(() => {
  if (!status.value) return false
  return ['expired', 'clock_tampered', 'missing', 'invalid'].includes(status.value.status)
})

const overlayTitle = computed(() => {
  if (!status.value) return ''
  switch (status.value.status) {
    case 'expired': return t('license.expiredTitle')
    case 'clock_tampered': return t('license.clockTampered')
    case 'missing': return t('license.missing')
    default: return t('license.invalid')
  }
})

const overlayMessage = computed(() => {
  if (!status.value) return ''
  switch (status.value.status) {
    case 'expired': return t('license.expiredMessage')
    case 'clock_tampered': return t('license.clockTamperedMessage')
    case 'missing': return t('license.missingMessage')
    default: return status.value.message
  }
})

async function fetchLicenseStatus() {
  try {
    const res = await http.get('/license/status')
    const payload = res as unknown as ApiEnvelope<LicenseStatus>
    status.value = payload.data
  } catch {
    // License endpoint not available — assume no license system
  }
}

onMounted(() => {
  fetchLicenseStatus()
  // Re-check every 30 minutes
  setInterval(fetchLicenseStatus, 30 * 60 * 1000)
})
</script>

<style scoped>
.trial-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 6px 16px;
  font-size: 13px;
  font-weight: 600;
  z-index: 1000;
}

.trial-banner--warning {
  background: var(--el-color-warning-light-9, #fdf6ec);
  color: var(--el-color-warning-dark-2, #b88230);
  border-bottom: 1px solid var(--el-color-warning-light-5, #f3d19e);
}

.trial-banner--critical {
  background: var(--el-color-danger-light-9, #fef0f0);
  color: var(--el-color-danger-dark-2, #b25252);
  border-bottom: 1px solid var(--el-color-danger-light-5, #fab6b6);
}

.trial-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}
.trial-banner--warning .trial-badge { background: var(--el-color-warning, #e6a23c); color: #fff; }
.trial-banner--critical .trial-badge { background: var(--el-color-danger, #f56c6c); color: #fff; }

/* Overlay */
.trial-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(4px);
}

.trial-overlay-card {
  background: var(--mc-bg-elevated, #fff);
  border-radius: 16px;
  padding: 48px;
  max-width: 420px;
  text-align: center;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.trial-overlay-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.trial-overlay-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--mc-text-primary, #1a1a1a);
  margin: 0 0 12px;
}

.trial-overlay-message {
  font-size: 14px;
  line-height: 1.6;
  color: var(--mc-text-secondary, #666);
  margin: 0;
}

.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
