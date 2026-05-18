<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'

const router = useRouter()
const route = useRoute()

// Navigate to the main chat console with the analytics agent pre-selected.
// The analytics agent ID is seeded as 1000000020.
onMounted(() => {
  const datasetId = route.query.datasetId
  localStorage.setItem('currentAgentId', '1000000020')
  const query: Record<string, string> = { agentId: '1000000020' }
  if (datasetId) query.datasetId = String(datasetId)
  router.push({ path: '/chat', query })
})
</script>

<template>
  <div class="mc-page-shell">
    <div class="mc-page-frame">
      <p>正在跳转到数据分析对话...</p>
    </div>
  </div>
</template>
