<template>
  <div class="app-root">
    <es-router-view></es-router-view>
  </div>
</template>

<script lang="ts">
import { defineComponent } from '@vue/runtime-core'
import { useESDevice, useESLog, useESNetwork, ESLogLevel } from '@extscreen/es3-core'
import { useESPlayer } from '@extscreen/es3-player'
import BuildConfig from './config/build-config'

export default defineComponent({
  name: 'App',
  setup() {
    const log = useESLog()
    const device = useESDevice()
    const network = useESNetwork()
    const playerManager = useESPlayer()

    function onESCreate() {
      if (BuildConfig.debug) {
        log.setMinimumLoggingLevel(ESLogLevel.DEBUG)
      } else {
        log.setMinimumLoggingLevel(ESLogLevel.WARN)
      }
      
      return Promise.resolve()
        .then(() => {
          playerManager.init({
            debug: BuildConfig.debug,
            display: {
              screenWidth: device.getScreenWidth(),
              screenHeight: device.getScreenHeight()
            },
            device: {
              deviceType: ''
            }
          })
        })
    }

    return {
      onESCreate
    }
  }
})
</script>

<style scoped>
.app-root {
  width: 1920px;
  height: 1080px;
  flex: 1;
  display: flex;
  flex-direction: column;
  background-color: #0a0a0f;
}
</style>
