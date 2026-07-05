<template>
  <div class='app-root-css' :gradientBackground="{colors:bgGradientColor,orientation: 4}">
    <es-router-view></es-router-view>
  </div>
</template>

<script lang='ts'>
import { useESNativeRouter, useESRouter } from '@extscreen/es3-router'
import { defineComponent } from 'vue'
import { Native } from '@extscreen/es3-vue'
import {
  ESLogLevel,
  useES,
  useESDevelop,
  useESDevice, useESEventBus, useESLocalStorage,
  useESLog, useESNetwork,
  useESRuntime
} from '@extscreen/es3-core'
import { ESPlayerLogLevel, useESPlayerLog, useESPlayer} from '@extscreen/es3-player'
import BuildConfig from './config/build-config'
import ThemeConfig from './config/theme-config'
import launch from './tools/launch'

export default defineComponent({
  name: 'App',
  emits: [],
  setup() {
    //全局渐进背景色
    const bgGradientColor = ThemeConfig.bgGradientColor

    const router = useESRouter()
    const nativeRouter = useESNativeRouter()
    const network = useESNetwork()
    const eventBus = useESEventBus()
    const localStore = useESLocalStorage()
    const log = useESLog()
    const playerLog = useESPlayerLog()

    const es = useES()
    const develop = useESDevelop()
    const device = useESDevice()
    const runtime = useESRuntime()
    const playerManager = useESPlayer()

    function onESCreate() {
      initESLog()
      initTheme()
      switchDev()
      return Promise.resolve()
        .then(() => launch.init(router, nativeRouter, develop))
        .then(() => {
          try {
            network.addListener(connectivityChangeListener)
          } catch (e) {
            console.log('network listener error:', e)
          }
        })
        .catch((e) => {
          console.log('init error:', e)
        })
    }

    /**
     * 初始化整体样式
     */
    function initTheme() {
      //淡入淡出启用
      Native.callNative('FastListModule', 'setFadeEnabled', true)
      //淡入淡出持续时间
      Native.callNative('FastListModule', 'setFadeDuration', 500)
      if (ThemeConfig.focusBorderCornerEnable) {
        //设置默认焦点边框圆角
        Native.callNative('FocusModule', 'setDefaultFocusBorderCorner', ThemeConfig.focusBorderCorner)
      }
      if (ThemeConfig.focusBorderColorEnable) {
        //设置默认焦点颜色
        Native.callNative('FocusModule', 'setDefaultFocusBorderColor', ThemeConfig.focusBorderColor)
      }
      if (ThemeConfig.focusBorderWidthEnable) {
        //设置外边框宽度
        Native.callNative('FocusModule', 'setDefaultFocusBorderWidth', ThemeConfig.focusBorderWidth)
      }
      if (ThemeConfig.focusBorderInsetEnable) {
        //设置焦点边框向内移动距离
        Native.callNative('FocusModule', 'setFocusBorderInsetValue', ThemeConfig.focusBorderInsetValue)
      }
      //设置焦点框是否有内里黑色边框
      Native.callNative('FocusModule', 'setDefaultFocusInnerBorderEnable', ThemeConfig.focusInnerBorderEnable)
      //设置焦点放大倍数
      Native.callNative('FocusModule', 'setDefaultPlaceholderFocusScale', ThemeConfig.placeHolderFocusScale);
    }

    /**
     * 设置日志级别控制
     */
    function initESLog() {
      if (BuildConfig.DEBUG) { //调试
        log.setMinimumLoggingLevel(ESLogLevel.DEBUG)
        playerLog.setMinimumLoggingLevel(ESPlayerLogLevel.DEBUG)
      } else { // 生产
        log.setMinimumLoggingLevel(ESLogLevel.WARN)
        playerLog.setMinimumLoggingLevel(ESPlayerLogLevel.WARN)
      }
    }

    /**
     * 网络监听
     */
    const connectivityChangeListener = {
      onConnectivityChange() {
        // 网络变化时不强制跳转，应用支持离线使用
      },
    }


    /**
     * 低端机判断
     */
    const switchDev = () => {
      let devTotalMemory = device.getDeviceTotalMemory()
      let devAndroidLevel = Number(device.getAndroidAPILevel())
      //内存小于1G 或者 Android版本小于 6.0的走低端机逻辑
      if (devTotalMemory <= 1024 || devAndroidLevel < 23) {
        BuildConfig.isLowEndDev = true
      }
    }

    function onESDestroy() {
      try {
        network.removeListener(connectivityChangeListener)
      } catch (e) {
        console.log('destroy error:', e)
      }
    }

    return {
      bgGradientColor,
      onESCreate,
      onESDestroy
    }
  }
})
</script>

<style src='./app.scss'>
</style>
