<template>
  <div class="home-root">
    <div class="nav-bar">
      <div 
        v-for="(tab, index) in tabs" 
        :key="index"
        class="nav-item"
        :class="{ 'nav-item-active': currentTab === index }"
        @click="onTabClick(index)"
      >
        <text class="nav-text">{{ tab }}</text>
      </div>
    </div>
    
    <div class="content-area">
      <div v-show="currentTab === 0" class="page-container">
        <InstallPage />
      </div>
      <div v-show="currentTab === 1" class="page-container">
        <RemotePage />
      </div>
      <div v-show="currentTab === 2" class="page-container">
        <ManagerPage />
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref } from 'vue'
import InstallPage from './install/InstallPage.vue'
import RemotePage from './remote/RemotePage.vue'
import ManagerPage from './manager/ManagerPage.vue'

export default defineComponent({
  name: 'HomePage',
  components: {
    InstallPage,
    RemotePage,
    ManagerPage
  },
  setup() {
    const tabs = ['口令安装', '远程推送', '应用管理']
    const currentTab = ref(0)

    const onTabClick = (index: number) => {
      currentTab.value = index
    }

    return {
      tabs,
      currentTab,
      onTabClick
    }
  }
})
</script>

<style scoped lang="scss">
.home-root {
  width: 1920px;
  height: 1080px;
  display: flex;
  flex-direction: column;
  background-color: #0a0a0f;
}

.nav-bar {
  width: 1920px;
  height: 100px;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  background-color: #12121f;
  gap: 80px;
}

.nav-item {
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 20px;
  position: relative;
}

.nav-item-active {
  &::after {
    content: '';
    position: absolute;
    bottom: 0;
    left: 20px;
    right: 20px;
    height: 3px;
    background: linear-gradient(90deg, #4a9eff, #2d7dd2);
    border-radius: 2px;
  }
}

.nav-text {
  font-size: 28px;
  color: #ffffff;
  font-weight: 500;
}

.nav-item-active .nav-text {
  color: #4a9eff;
  font-weight: bold;
}

.content-area {
  flex: 1;
  width: 1920px;
  overflow: hidden;
}

.page-container {
  width: 1920px;
  height: 980px;
}
</style>
