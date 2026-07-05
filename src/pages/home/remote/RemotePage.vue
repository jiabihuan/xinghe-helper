<template>
  <div class="remote-page">
    <div class="remote-title">远程推送</div>
    <div class="remote-subtitle">扫描二维码或访问网页推送应用</div>
    
    <div class="qrcode-container">
      <div class="qrcode-placeholder">
        <text class="qrcode-icon">📱</text>
      </div>
      <div class="qrcode-info">
        <text class="qrcode-tip">扫描二维码推送应用</text>
        <text class="qrcode-url">{{ pushUrl }}</text>
      </div>
    </div>

    <div class="device-info">
      <div class="info-item">
        <text class="info-label">设备名称：</text>
        <text class="info-value">{{ deviceName }}</text>
      </div>
      <div class="info-item">
        <text class="info-label">设备码：</text>
        <text class="info-value">{{ deviceCode }}</text>
      </div>
    </div>

    <div class="push-list-title">
      <text class="list-title-text">推送记录</text>
    </div>

    <div class="push-list">
      <div v-if="pushList.length === 0" class="empty-state">
        <text class="empty-text">暂无推送记录</text>
      </div>
      <div v-for="(item, index) in pushList" :key="index" class="push-item">
        <div class="item-icon">
          <text class="icon-text">📦</text>
        </div>
        <div class="item-info">
          <text class="item-name">{{ item.name }}</text>
          <text class="item-time">{{ item.time }}</text>
        </div>
        <div class="item-status" :class="item.status">
          <text class="status-text">{{ getStatusText(item.status) }}</text>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, onMounted } from "@vue/runtime-core";
import { useESDevice } from "@extscreen/es3-core";
import BuildConfig from "../../../config/build-config";

export default defineComponent({
  name: 'RemotePage',
  setup() {
    const device = useESDevice();
    const deviceName = ref('未知设备');
    const deviceCode = ref('');
    const pushUrl = ref(BuildConfig.requestBaseUrl + '/push');
    const pushList = ref<Array<{name: string, time: string, status: string}>>([
      { name: '示例应用1', time: '2024-01-15 10:30', status: 'completed' },
      { name: '示例应用2', time: '2024-01-14 15:20', status: 'pending' },
    ]);

    onMounted(() => {
      try {
        deviceName.value = device.getDeviceName() || '电视设备';
        deviceCode.value = device.getDeviceId()?.substring(0, 8) || 'ABCDEFGH';
      } catch (e) {
        console.error('获取设备信息失败', e);
      }
    });

    const getStatusText = (status: string) => {
      const map: Record<string, string> = {
        pending: '待安装',
        downloading: '下载中',
        completed: '已完成',
        failed: '失败'
      };
      return map[status] || status;
    };

    return {
      deviceName,
      deviceCode,
      pushUrl,
      pushList,
      getStatusText
    };
  }
});
</script>

<style scoped>
.remote-page {
  width: 1920px;
  height: 1080px;
  display: flex;
  flex-direction: column;
  align-items: center;
  background-color: #0a0a0f;
  padding-top: 120px;
  padding-left: 100px;
  padding-right: 100px;
}

.remote-title {
  font-size: 48px;
  color: #ffffff;
  font-weight: bold;
  margin-bottom: 12px;
}

.remote-subtitle {
  font-size: 24px;
  color: #888888;
  margin-bottom: 50px;
}

.qrcode-container {
  display: flex;
  flex-direction: row;
  align-items: center;
  background-color: #1a1a2e;
  border-radius: 20px;
  padding: 40px 60px;
  margin-bottom: 40px;
}

.qrcode-placeholder {
  width: 200px;
  height: 200px;
  background-color: #ffffff;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 50px;
}

.qrcode-icon {
  font-size: 80px;
}

.qrcode-info {
  display: flex;
  flex-direction: column;
}

.qrcode-tip {
  font-size: 28px;
  color: #ffffff;
  margin-bottom: 15px;
}

.qrcode-url {
  font-size: 20px;
  color: #4a9eff;
}

.device-info {
  display: flex;
  flex-direction: row;
  gap: 60px;
  margin-bottom: 40px;
}

.info-item {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.info-label {
  font-size: 22px;
  color: #888888;
}

.info-value {
  font-size: 22px;
  color: #ffffff;
}

.push-list-title {
  width: 100%;
  margin-bottom: 20px;
}

.list-title-text {
  font-size: 28px;
  color: #ffffff;
  font-weight: bold;
}

.push-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.empty-state {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
}

.empty-text {
  font-size: 24px;
  color: #666666;
}

.push-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  background-color: #1a1a2e;
  border-radius: 12px;
  padding: 20px 30px;
}

.item-icon {
  width: 60px;
  height: 60px;
  background-color: #252545;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20px;
}

.icon-text {
  font-size: 30px;
}

.item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.item-name {
  font-size: 24px;
  color: #ffffff;
  margin-bottom: 8px;
}

.item-time {
  font-size: 18px;
  color: #888888;
}

.item-status {
  padding: 8px 20px;
  border-radius: 20px;
}

.item-status.completed {
  background-color: rgba(76, 175, 80, 0.2);
}

.item-status.completed .status-text {
  color: #4caf50;
}

.item-status.pending {
  background-color: rgba(255, 152, 0, 0.2);
}

.item-status.pending .status-text {
  color: #ff9800;
}

.status-text {
  font-size: 20px;
}
</style>
