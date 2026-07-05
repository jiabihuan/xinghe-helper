<template>
  <div class="install-page">
    <div class="install-title">口令安装</div>
    <div class="install-subtitle">请输入6位口令下载应用</div>
    
    <div class="code-input-container">
      <div 
        v-for="(digit, index) in code" 
        :key="index" 
        class="code-digit"
        :class="{ 'code-digit-active': index === currentIndex, 'code-digit-filled': digit !== '' }"
      >
        <text class="code-digit-text">{{ digit }}</text>
      </div>
    </div>

    <div class="status-text">{{ statusText }}</div>

    <div class="keyboard">
      <div class="keyboard-row">
        <div 
          v-for="num in 3" 
          :key="num" 
          class="keyboard-key"
          @click="onKeyClick(num.toString())"
        >
          <text class="key-text">{{ num }}</text>
        </div>
      </div>
      <div class="keyboard-row">
        <div 
          v-for="num in 3" 
          :key="num + 3" 
          class="keyboard-key"
          @click="onKeyClick((num + 3).toString())"
        >
          <text class="key-text">{{ num + 3 }}</text>
        </div>
      </div>
      <div class="keyboard-row">
        <div 
          v-for="num in 3" 
          :key="num + 6" 
          class="keyboard-key"
          @click="onKeyClick((num + 6).toString())"
        >
          <text class="key-text">{{ num + 6 }}</text>
        </div>
      </div>
      <div class="keyboard-row">
        <div class="keyboard-key keyboard-action" @click="onClearClick">
          <text class="key-text">清除</text>
        </div>
        <div class="keyboard-key" @click="onKeyClick('0')">
          <text class="key-text">0</text>
        </div>
        <div class="keyboard-key keyboard-action" @click="onBackspaceClick">
          <text class="key-text">删除</text>
        </div>
      </div>
    </div>

    <div class="confirm-btn" @click="onConfirmClick">
      <text class="confirm-text">确认下载</text>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref } from "@vue/runtime-core";
import { useESToast, useESDownload } from "@extscreen/es3-core";
import BuildConfig from "../../../config/build-config";

export default defineComponent({
  name: 'InstallPage',
  setup() {
    const code = ref<string[]>(['', '', '', '', '', '']);
    const currentIndex = ref(0);
    const statusText = ref('');
    const toast = useESToast();
    const download = useESDownload();

    const onKeyClick = (key: string) => {
      if (currentIndex.value < 6) {
        code.value[currentIndex.value] = key;
        currentIndex.value++;
        statusText.value = '';
      }
    };

    const onBackspaceClick = () => {
      if (currentIndex.value > 0) {
        currentIndex.value--;
        code.value[currentIndex.value] = '';
        statusText.value = '';
      }
    };

    const onClearClick = () => {
      code.value = ['', '', '', '', '', ''];
      currentIndex.value = 0;
      statusText.value = '';
    };

    const onConfirmClick = () => {
      if (!code.value.every(c => c !== '')) {
        statusText.value = '请输入完整的6位口令';
        return;
      }
      const password = code.value.join('');
      statusText.value = '正在验证口令...';
      
      fetch(`${BuildConfig.requestBaseUrl}/api/apps/by-code?code=${password}`)
        .then(response => response.json())
        .then(data => {
          if (data && data.downloadUrl) {
            statusText.value = '开始下载：' + (data.name || '应用');
            toast.show('开始下载');
            download.download({ 
              url: data.downloadUrl, 
              fileName: `${data.name || 'app'}.apk` 
            });
          } else {
            statusText.value = data.message || '口令无效，请重试';
          }
        })
        .catch(() => {
          statusText.value = '网络错误，请检查网络连接';
        });
    };

    return {
      code,
      currentIndex,
      statusText,
      onKeyClick,
      onBackspaceClick,
      onClearClick,
      onConfirmClick
    };
  }
});
</script>

<style scoped>
.install-page {
  width: 1920px;
  height: 1080px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background-color: #0a0a0f;
  padding-top: 120px;
}

.install-title {
  font-size: 48px;
  color: #ffffff;
  font-weight: bold;
  margin-bottom: 12px;
}

.install-subtitle {
  font-size: 24px;
  color: #888888;
  margin-bottom: 60px;
}

.code-input-container {
  display: flex;
  flex-direction: row;
  gap: 20px;
  margin-bottom: 30px;
}

.code-digit {
  width: 80px;
  height: 100px;
  background-color: #1a1a2e;
  border: 2px solid #333355;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.code-digit-active {
  border-color: #4a9eff;
  box-shadow: 0 0 20px rgba(74, 158, 255, 0.3);
}

.code-digit-filled {
  background-color: #252545;
}

.code-digit-text {
  font-size: 48px;
  color: #ffffff;
  font-weight: bold;
}

.status-text {
  font-size: 20px;
  color: #ff6b6b;
  height: 30px;
  margin-bottom: 40px;
}

.keyboard {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 50px;
}

.keyboard-row {
  display: flex;
  flex-direction: row;
  gap: 16px;
  justify-content: center;
}

.keyboard-key {
  width: 80px;
  height: 80px;
  background-color: #1a1a2e;
  border: 2px solid #333355;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.keyboard-action {
  background-color: #2a2a4e;
}

.key-text {
  font-size: 32px;
  color: #ffffff;
}

.confirm-btn {
  width: 300px;
  height: 70px;
  background: linear-gradient(135deg, #4a9eff 0%, #2d7dd2 100%);
  border-radius: 35px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.confirm-text {
  font-size: 28px;
  color: #ffffff;
  font-weight: bold;
}
</style>
