<template>
  <div class="mc-page-shell">
    <div class="mc-page-frame">
      <div class="mc-page-inner">
        <div class="mc-page-header">
          <div>
            <div class="mc-page-kicker">Analytics</div>
            <h1 class="mc-page-title">{{ t('nav.dataManagement') }}</h1>
          </div>
        </div>

        <!-- Scenario guidance -->
        <div class="guidance-area">
          <div class="guidance-card" @click="goTo('/analytics/datasets')">
            <div class="guidance-icon">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>
            </div>
            <div class="guidance-text">
              <div class="guidance-title">{{ t('analytics.guidanceUpload') }}</div>
              <div class="guidance-desc">{{ t('analytics.guidanceUploadDesc') }}</div>
            </div>
            <svg class="guidance-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
          <div class="guidance-card" @click="goTo('/analytics/datasources')">
            <div class="guidance-icon guidance-icon-db">
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><ellipse cx="12" cy="5" rx="9" ry="3"/><path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3"/><path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5"/></svg>
            </div>
            <div class="guidance-text">
              <div class="guidance-title">{{ t('analytics.guidanceConnect') }}</div>
              <div class="guidance-desc">{{ t('analytics.guidanceConnectDesc') }}</div>
            </div>
            <svg class="guidance-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
          </div>
        </div>

        <div class="analytics-tabs">
          <router-link
            to="/analytics/datasets"
            class="analytics-tab"
            :class="{ active: route.path.startsWith('/analytics/datasets') }"
          >
            {{ t('analytics.datasets') }}
          </router-link>
          <router-link
            to="/analytics/templates"
            class="analytics-tab"
            :class="{ active: route.path.startsWith('/analytics/templates') }"
          >
            {{ t('analytics.templates') }}
          </router-link>
          <router-link
            to="/analytics/datasources"
            class="analytics-tab"
            :class="{ active: route.path.startsWith('/analytics/datasources') }"
          >
            {{ t('analytics.externalSources') }}
          </router-link>
        </div>

        <router-view />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

function goTo(path: string) {
  router.push(path)
}
</script>

<style>
.analytics-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--mc-border-light);
  padding-bottom: 0;
}

.analytics-tab {
  padding: 10px 20px;
  font-size: 14px;
  font-weight: 500;
  color: var(--mc-text-secondary);
  text-decoration: none;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
  transition: all 0.15s ease;
  cursor: pointer;
}

.analytics-tab:hover {
  color: var(--mc-text-primary);
}

.analytics-tab.active {
  color: var(--mc-primary);
  border-bottom-color: var(--mc-primary);
  font-weight: 600;
}

.sub-page-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}

/* Guidance area */
.guidance-area {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 20px;
}

.guidance-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 18px;
  background: var(--mc-bg-elevated);
  border: 1px solid var(--mc-border);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.guidance-card:hover {
  border-color: var(--mc-primary);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.guidance-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: color-mix(in srgb, var(--mc-primary) 10%, transparent);
  color: var(--mc-primary);
}

.guidance-icon-db {
  background: color-mix(in srgb, #336791 10%, transparent);
  color: #336791;
}

.guidance-text {
  flex: 1;
  min-width: 0;
}

.guidance-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--mc-text-primary);
}

.guidance-desc {
  font-size: 12px;
  color: var(--mc-text-tertiary);
  margin-top: 2px;
}

.guidance-arrow {
  flex-shrink: 0;
  color: var(--mc-text-tertiary);
}

.guidance-card:hover .guidance-arrow {
  color: var(--mc-primary);
}

@media (max-width: 640px) {
  .guidance-area {
    grid-template-columns: 1fr;
  }
}
</style>
