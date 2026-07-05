<template>
  <div class='app-root-css' :style="{ backgroundColor: '#0a0a0f' }">
    <es-router-view></es-router-view>
  </div>
</template>

<script lang='ts'>
import { defineComponent } from 'vue'
import { Native } from '@extscreen/es3-vue'
import {
  ESLogLevel,
  useESDevice,
  useESLog,
} from '@extscreen/es3-core'
import { ESPlayerLogLevel, useESPlayerLog, useESPlayer } from '@extscreen/es3-player'
import { useESRuntime } from '@extscreen/es3-core'

import BuildConfig from './config/build-config'

export default defineComponent({
  name: 'App',
  emits: [],
  setup() {
    const log = useESLog()
    const playerLog = useESPlayerLog()
    const device = useESDevice()
    const runtime = useESRuntime()
    const playerManager = useESPlayer()

    function onESCreate() {
      initESLog()
      initTheme()
      return Promise.resolve()
        .then(() => {
          playerManager.init({
            debug: BuildConfig.DEBUG,
            display: {
              screenWidth: device.getScreenWidth(),
              screenHeight: device.getScreenHeight()
            },
            device: {
              deviceType: runtime.getRuntimeDeviceType() ?? ''
            }
          })
        })
    }

    function initTheme() {
      Native.callNative('FastListModule', 'setFadeEnabled', true)
      Native.callNative('FastListModule', 'setFadeDuration', 500)
      Native.callNative('FocusModule', 'setDefaultFocusBorderCorner', 12)
      Native.callNative('FocusModule', 'setDefaultFocusBorderWidth', 3)
      Native.callNative('FocusModule', 'setFocusBorderInsetValue', -5)
      Native.callNative('FocusModule', 'setDefaultFocusInnerBorderEnable', true)
      Native.callNative('FocusModule', 'setDefaultPlaceholderFocusScale', 1.05)
    }

    function initESLog() {
      if (BuildConfig.DEBUG) {
        log.setMinimumLoggingLevel(ESLogLevel.DEBUG)
        playerLog.setMinimumLoggingLevel(ESPlayerLogLevel.DEBUG)
      } else {
        log.setMinimumLoggingLevel(ESLogLevel.WARN)
        playerLog.setMinimumLoggingLevel(ESPlayerLogLevel.WARN)
      }
    }

    function onESDestroy() {
    }

    return {
      onESCreate,
      onESDestroy
    }
  }
})
</script>

<style scoped>
.app-root-css {
  width: 1920px;
  height: 1080px;
  flex: 1;
  display: flex;
  flex-direction: column;
}
</style>
