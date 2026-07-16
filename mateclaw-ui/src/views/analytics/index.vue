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

        <div class="analytics-tabs">
          <router-link
            to="/analytics/datasets"
            class="analytics-tab"
            :class="{ active: route.path.startsWith('/analytics/datasets') }"
          >
            {{ t('analytics.datasets') }}
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
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'

const route = useRoute()
const { t } = useI18n()
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

</style>
