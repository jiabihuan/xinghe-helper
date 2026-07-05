<template>
  <div class="manager-page">
    <div class="manager-header">
      <text class="manager-title">应用管理</text>
      <text class="manager-subtitle">已安装 {{ appList.length }} 个应用</text>
    </div>

    <div class="app-grid">
      <div v-if="appList.length === 0" class="empty-state">
        <text class="empty-icon">📭</text>
        <text class="empty-text">暂无已安装应用</text>
        <text class="empty-tip">使用口令安装或远程推送安装应用</text>
      </div>
      <div 
        v-for="(app, index) in appList" 
        :key="index" 
        class="app-card"
        @click="onAppClick(app)"
      >
        <div class="app-icon">
          <text class="app-icon-text">{{ app.icon }}</text>
        </div>
        <text class="app-name">{{ app.name }}</text>
        <text class="app-size">{{ app.size }}</text>
      </div>
    </div>

    <div v-if="selectedApp" class="app-dialog">
      <div class="dialog-mask" @click="selectedApp = null"></div>
      <div class="dialog-content">
        <div class="dialog-header">
          <div class="dialog-icon">
            <text class="dialog-icon-text">{{ selectedApp.icon }}</text>
          </div>
          <div class="dialog-info">
            <text class="dialog-name">{{ selectedApp.name }}</text>
            <text class="dialog-version">版本：{{ selectedApp.version }}</text>
            <text class="dialog-size">大小：{{ selectedApp.size }}</text>
          </div>
        </div>
        <div class="dialog-actions">
          <div class="dialog-btn open-btn" @click="onOpenApp">
            <text class="btn-text">打开</text>
          </div>
          <div class="dialog-btn uninstall-btn" @click="onUninstallApp">
            <text class="btn-text">卸载</text>
          </div>
          <div class="dialog-btn cancel-btn" @click="selectedApp = null">
            <text class="btn-text">取消</text>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref } from "@vue/runtime-core";
import { useESToast } from "@extscreen/es3-core";

interface AppItem {
  name: string;
  icon: string;
  size: string;
  version: string;
  packageName: string;
}

export default defineComponent({
  name: 'ManagerPage',
  setup() {
    const toast = useESToast();
    const selectedApp = ref<AppItem | null>(null);
    
    const appList = ref<AppItem[]>([
      { name: '演示应用1', icon: '📺', size: '25.6 MB', version: '1.0.0', packageName: 'com.demo.app1' },
      { name: '演示应用2', icon: '🎮', size: '45.2 MB', version: '2.1.0', packageName: 'com.demo.app2' },
      { name: '演示应用3', icon: '🎵', size: '12.8 MB', version: '1.5.2', packageName: 'com.demo.app3' },
    ]);

    const onAppClick = (app: AppItem) => {
      selectedApp.value = app;
    };

    const onOpenApp = () => {
      if (selectedApp.value) {
        toast.show('正在打开：' + selectedApp.value.name);
        selectedApp.value = null;
      }
    };

    const onUninstallApp = () => {
      if (selectedApp.value) {
        toast.show('正在卸载：' + selectedApp.value.name);
        const index = appList.value.findIndex(a => a.packageName === selectedApp.value!.packageName);
        if (index > -1) {
          appList.value.splice(index, 1);
        }
        selectedApp.value = null;
      }
    };

    return {
      appList,
      selectedApp,
      onAppClick,
      onOpenApp,
      onUninstallApp
    };
  }
});
</script>

<style scoped>
.manager-page {
  width: 1920px;
  height: 1080px;
  display: flex;
  flex-direction: column;
  background-color: #0a0a0f;
  padding-top: 120px;
  padding-left: 100px;
  padding-right: 100px;
}

.manager-header {
  display: flex;
  flex-direction: column;
  margin-bottom: 40px;
}

.manager-title {
  font-size: 48px;
  color: #ffffff;
  font-weight: bold;
  margin-bottom: 12px;
}

.manager-subtitle {
  font-size: 24px;
  color: #888888;
}

.app-grid {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 30px;
}

.empty-state {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 100px 0;
}

.empty-icon {
  font-size: 100px;
  margin-bottom: 30px;
}

.empty-text {
  font-size: 32px;
  color: #ffffff;
  margin-bottom: 15px;
}

.empty-tip {
  font-size: 22px;
  color: #666666;
}

.app-card {
  width: 180px;
  display: flex;
  flex-direction: column;
  align-items: center;
  background-color: #1a1a2e;
  border-radius: 16px;
  padding: 25px 15px;
}

.app-icon {
  width: 100px;
  height: 100px;
  background-color: #252545;
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 15px;
}

.app-icon-text {
  font-size: 50px;
}

.app-name {
  font-size: 22px;
  color: #ffffff;
  margin-bottom: 8px;
  text-align: center;
}

.app-size {
  font-size: 18px;
  color: #888888;
}

.app-dialog {
  position: absolute;
  top: 0;
  left: 0;
  width: 1920px;
  height: 1080px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dialog-mask {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.7);
}

.dialog-content {
  position: relative;
  width: 500px;
  background-color: #1a1a2e;
  border-radius: 20px;
  padding: 40px;
  z-index: 10;
}

.dialog-header {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-bottom: 40px;
}

.dialog-icon {
  width: 100px;
  height: 100px;
  background-color: #252545;
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 30px;
}

.dialog-icon-text {
  font-size: 50px;
}

.dialog-info {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.dialog-name {
  font-size: 32px;
  color: #ffffff;
  font-weight: bold;
  margin-bottom: 10px;
}

.dialog-version {
  font-size: 20px;
  color: #888888;
  margin-bottom: 8px;
}

.dialog-size {
  font-size: 20px;
  color: #888888;
}

.dialog-actions {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.dialog-btn {
  height: 60px;
  border-radius: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.open-btn {
  background: linear-gradient(135deg, #4a9eff 0%, #2d7dd2 100%);
}

.uninstall-btn {
  background-color: #ff4757;
}

.cancel-btn {
  background-color: #333355;
}

.btn-text {
  font-size: 24px;
  color: #ffffff;
  font-weight: bold;
}
</style>
