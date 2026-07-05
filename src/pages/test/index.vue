<template>
  <div class="test-page">
    <text class="title-text">星河助手 v1.2.0</text>
    <text class="sub-text">测试页面 - 如果能看到这个就说明框架正常工作了</text>
    <div class="nav-tabs">
      <div 
        v-for="(tab, index) in tabs" 
        :key="index"
        class="nav-tab"
        :class="{ 'nav-tab-active': currentTab === index }"
        @click="currentTab = index"
      >
        <text class="tab-text">{{ tab }}</text>
      </div>
    </div>
    <div class="content-box">
      <text class="content-text">当前选中：{{ tabs[currentTab] }}</text>
      <text class="content-text">服务器地址：{{ serverUrl }}</text>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref } from 'vue'
import BuildConfig from '../../config/build-config'

export default defineComponent({
  name: 'TestPage',
  setup() {
    const tabs = ['口令安装', '远程推送', '应用管理']
    const currentTab = ref(0)
    const serverUrl = BuildConfig.requestBaseUrl

    return {
      tabs,
      currentTab,
      serverUrl
    }
  }
})
</script>

<style scoped lang="scss">
.test-page {
  width: 1920px;
  height: 1080px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: #0a0a0f;
}

.title-text {
  font-size: 56px;
  color: #ffffff;
  font-weight: bold;
  margin-bottom: 20px;
}

.sub-text {
  font-size: 28px;
  color: #4a9eff;
  margin-bottom: 60px;
}

.nav-tabs {
  display: flex;
  flex-direction: row;
  gap: 30px;
  margin-bottom: 50px;
}

.nav-tab {
  padding: 20px 40px;
  background-color: #1a1a2e;
  border: 2px solid #333355;
  border-radius: 12px;
}

.nav-tab-active {
  background-color: #252545;
  border-color: #4a9eff;
}

.tab-text {
  font-size: 28px;
  color: #ffffff;
}

.nav-tab-active .tab-text {
  color: #4a9eff;
  font-weight: bold;
}

.content-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding: 40px 60px;
  background-color: #12121f;
  border-radius: 16px;
}

.content-text {
  font-size: 24px;
  color: #cccccc;
}
</style>
