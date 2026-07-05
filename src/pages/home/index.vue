<template>
  <div class="home-root">
    <qt-nav-bar
      ref="navBar"
      class="home-nav-bar"
      @tab-select="onTabSelect">
    </qt-nav-bar>
    
    <div class="home-content">
      <div v-show="currentTab === 0">
        <InstallPage />
      </div>
      <div v-show="currentTab === 1">
        <RemotePage />
      </div>
      <div v-show="currentTab === 2">
        <ManagerPage />
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent, ref, onMounted } from "@vue/runtime-core";
import { QTNavBarItem, QTNavBar, QTNavBarItemType, QTINavBar } from "@quicktvui/quicktvui3";
import InstallPage from "./install/InstallPage.vue";
import RemotePage from "./remote/RemotePage.vue";
import ManagerPage from "./manager/ManagerPage.vue";

export default defineComponent({
  name: 'HomePage',
  components: {
    InstallPage,
    RemotePage,
    ManagerPage
  },
  setup() {
    const navBar = ref<QTINavBar>();
    const currentTab = ref(0);

    onMounted(() => {
      const tabItems: Array<QTNavBarItem> = [
        {
          type: QTNavBarItemType.QT_NAV_BAR_ITEM_TYPE_TEXT,
          text: '口令安装',
          titleSize: 26,
          decoration: {
            left: 50,
            right: 50,
          }
        },
        {
          type: QTNavBarItemType.QT_NAV_BAR_ITEM_TYPE_TEXT,
          text: '远程推送',
          titleSize: 26,
          decoration: {
            left: 50,
            right: 50,
          }
        },
        {
          type: QTNavBarItemType.QT_NAV_BAR_ITEM_TYPE_TEXT,
          text: '应用管理',
          titleSize: 26,
          decoration: {
            left: 50,
            right: 50,
          }
        }
      ];

      const navBarData: QTNavBar = {
        data: tabItems
      };

      navBar.value?.init(navBarData);
    });

    const onTabSelect = (e: { position: number }) => {
      currentTab.value = e.position;
    };

    const onESCreate = (params: any) => {
      const tabItems: Array<QTNavBarItem> = [
        {
          type: QTNavBarItemType.QT_NAV_BAR_ITEM_TYPE_TEXT,
          text: '口令安装',
          titleSize: 26,
          decoration: {
            left: 50,
            right: 50,
          }
        },
        {
          type: QTNavBarItemType.QT_NAV_BAR_ITEM_TYPE_TEXT,
          text: '远程推送',
          titleSize: 26,
          decoration: {
            left: 50,
            right: 50,
          }
        },
        {
          type: QTNavBarItemType.QT_NAV_BAR_ITEM_TYPE_TEXT,
          text: '应用管理',
          titleSize: 26,
          decoration: {
            left: 50,
            right: 50,
          }
        }
      ];

      const navBarData: QTNavBar = {
        data: tabItems
      };

      navBar.value?.init(navBarData);
    };

    return {
      navBar,
      currentTab,
      onTabSelect,
      onESCreate
    };
  }
});
</script>

<style scoped>
.home-root {
  width: 1920px;
  height: 1080px;
  display: flex;
  flex-direction: column;
  background-color: #0a0a0f;
}

.home-nav-bar {
  width: 1920px;
  height: 100px;
  background-color: #12121f;
}

.home-content {
  flex: 1;
  width: 1920px;
  overflow: hidden;
}
</style>
