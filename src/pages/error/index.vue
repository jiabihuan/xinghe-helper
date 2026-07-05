<template>
  <div class="error-page">
    <text class="title">加载失败</text>
    <text class="subtitle">{{ errorMessage }}</text>
    <div class="retry-btn" @click="retry">
      <text class="btn-text">重试</text>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref } from 'vue'
import { useESRouter } from '@extscreen/es3-router'

export default defineComponent({
  name: 'ErrorPage',
  setup() {
    const router = useESRouter()
    const errorMessage = ref('页面加载出错，请重试')

    function retry() {
      router.replace({ name: 'home' })
    }

    return {
      errorMessage,
      retry
    }
  }
})
</script>

<style scoped lang="scss">
.error-page {
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
