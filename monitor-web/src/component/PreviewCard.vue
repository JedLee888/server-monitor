<script setup>
import { computed } from 'vue' // 确保导入 computed
import {copyIp, fitByUnit, osNameToIcon, percentageToStatus, rename} from '@/tools'

const props = defineProps({
  data: Object,
  update: Function
})

// 新增计算属性
const isOverloaded = computed(() => {
  if (!props.data.online) return false;
  // CPU 使用率 (props.data.cpuUsage 已经是 0-1 的小数, 直接乘以 100)
  const cpuUsagePercentage = props.data.cpuUsage * 100;
  // 内存使用率 (props.data.memoryUsage 是已用GB, props.data.memory 是总GB)
  const memoryUsagePercentage = (props.data.memoryUsage / props.data.memory) * 100;
  return cpuUsagePercentage > 80 || memoryUsagePercentage > 80;
});

const statusText = computed(() => {
  if (!props.data.online) return '离线';
  if (isOverloaded.value) return '负载告警';
  return '运行中'; // 原模板中“运行中”没有附加信息
});

const statusIconClass = computed(() => {
  if (!props.data.online) return 'fa-solid fa-circle-stop';
  // “负载告警”时也用播放图标，但颜色会变
  return 'fa-solid fa-circle-play';
});

const statusColor = computed(() => {
  if (!props.data.online) return '#8a8a8a'; // 离线颜色
  if (isOverloaded.value) return 'red';    // 过载颜色
  return '#18cb18'; // 正常运行颜色
});
</script>

<template>
  <div class="instance-card">
    <div style="display: flex;justify-content: space-between">
      <div>
        <div class="name">
<!--          <span :class="`flag-icon flag-icon-${data.location}`"></span>-->
          <span style="margin: 0 0px">{{ data.name }}</span>&nbsp;
          <i class="fa-solid fa-pen-to-square interact-item" @click.stop="rename(data.id, data.name, update)"></i>
        </div>
        <div class="os">
          操作系统:
          <i :style="{color: osNameToIcon(data.osName).color}"
             :class="`fa-brands ${osNameToIcon(data.osName).icon}`"></i>
          {{`${data.osName} ${data.osVersion}`}}
        </div>
      </div>

      <div class="status">
        <i :class="statusIconClass" :style="{ color: statusColor }"></i>
        <span :style="{ marginLeft: '5px', color: statusColor }">{{ statusText }}</span>
      </div>

    </div>
    <el-divider style="margin: 10px 0"/>
    <div class="network">
      <span style="margin-right: 10px">公网IP: {{data.ip}}</span>
      <i class="fa-solid fa-copy interact-item" @click.stop="copyIp(data.ip)" style="color: dodgerblue"></i>
    </div>
    <div class="cpu">
      <span style="margin-right: 10px">处理器: {{data.cpuName}}</span>
    </div>
    <div class="hardware">
      <i class="fa-solid fa-microchip"></i>
      <span style="margin-right: 10px">{{` ${data.cpuCore} CPU`}}</span>
      <i class="fa-solid fa-memory"></i>
      <span>{{` ${data.memory.toFixed(1)} GB`}}</span>
    </div>
    <div class="progress">
      <span>{{`CPU: ${(data.cpuUsage * 100).toFixed(1)}%`}}</span>
      <el-progress :status="percentageToStatus(data.cpuUsage * 100)"
                   :percentage="data.cpuUsage * 100" :stroke-width="5" :show-text="false"/>
    </div>
    <div class="progress">
      <span>内存: <b>{{data.memoryUsage.toFixed(1)}}</b> GB</span>
      <el-progress :status="percentageToStatus(data.memoryUsage/data.memory * 100)"
                   :percentage="data.memoryUsage/data.memory * 100" :stroke-width="5" :show-text="false"/>
    </div>
    <div class="network-flow">
      <div>网络流量</div>
      <div>
        <i class="fa-solid fa-arrow-up"></i>
        <span>{{` ${fitByUnit(data.networkUpload, 'KB')}/s`}}</span>
        <el-divider direction="vertical"/>
        <i class="fa-solid fa-arrow-down"></i>
        <span>{{` ${fitByUnit(data.networkDownload, 'KB')}/s`}}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dark .instance-card { color: #d9d9d9 }

.interact-item {
  transition: .3s;

  &:hover {
    cursor: pointer;
    scale: 1.1;
    opacity: 0.8;
  }
}

.instance-card {
  width: 320px;
  padding: 15px;
  background-color: var(--el-bg-color);
  border-radius: 5px;
  box-sizing: border-box;
  color: #606060;
  transition: .3s;

  &:hover {
    cursor: pointer;
    scale: 1.02;
  }

  .name {
    font-size: 15px;
    font-weight: bold;
  }

  .status {
    font-size: 14px;
  }

  .os {
    font-size: 13px;
    color: grey;
  }

  .network {
    font-size: 13px;
  }

  .hardware {
    margin-top: 5px;
    font-size: 13px;
  }

  .progress {
    margin-top: 10px;
    font-size: 12px;
  }

  .cpu {
    font-size: 13px;
  }

  .network-flow {
    margin-top: 10px;
    font-size: 12px;
    display: flex;
    justify-content: space-between;
  }
}
</style>
