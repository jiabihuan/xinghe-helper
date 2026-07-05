<template>
  <div class="network-page">
    <text class="title">网络不可用</text>
    <text class="subtitle">请检查网络连接后重试</text>
    <div class="retry-btn" @click="retry">
      <text class="btn-text">重试</text>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue'
import { useESNetwork } from '@extscreen/es3-core'
import { useESRouter } from '@extscreen/es3-router'

export default defineComponent({
  name: 'NetworkPage',
  setup() {
    const network = useESNetwork()
    const router = useESRouter()

    function retry() {
      const isConnected = network.isNetworkConnected()
      if (isConnected) {
        router.replace({ name: 'home' })
      }
    }

    return {
      retry
    }
  }
})
</script>

<style scoped lang="scss">
.network-page {
  width: 1920px;
  height: 1080px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: #0a0a0f;
}

.title {
  font-size: 48px;
  color: #ffffff;
  font-weight: bold;
  margin-bottom: 20px;
}

.subtitle {
  font-size: 28px;
  color: #888888;
  margin-bottom: 60px;
}

.retry-btn {
  padding: 20px 60px;
  background-color: #4a9eff;
  border-radius: 12px;
}

.btn-text {
  font-size: 28px;
  color: #ffffff;
  font-weight: bold;
}
</style>
