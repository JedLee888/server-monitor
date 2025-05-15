<script setup>
import { computed, reactive, watch, onUnmounted, ref } from "vue";
import { get, post } from "@/net";
import { copyIp, cpuNameToImage, fitByUnit, osNameToIcon, percentageToStatus, rename } from "@/tools";
import { ElMessage, ElMessageBox } from "element-plus";
import RuntimeHistory from "@/component/RuntimeHistory.vue"; // 检查此路径是否正确，可能是 @/components/
import { Connection, Delete } from "@element-plus/icons-vue";

const locations = [
  {name: 'bj', desc: '北京'}, {name: 'gz', desc: '广州'}, {name: 'sh', desc: '上海'},
  {name: 'sz', desc: '深圳'}, {name: 'hz', desc: '杭州'}, {name: 'hk', desc: '香港'},
  {name: 'zq', desc: '肇庆'}
];

const props = defineProps({
  id: Number, // 当前选中的客户端 ID
  update: Function // 由 Manage.vue 传入的 onClientDelete 函数
});
// emits 中不再需要 'delete'，因为删除操作通过 props.update 回调到 Manage.vue
const emits = defineEmits(['terminal']);

const details = reactive({
  base: {},       // 存储客户端基本信息，来自 /api/monitor/details
  runtime: {      // 存储运行时信息
    list: [],     // 历史运行时数据点列表，来自 /api/monitor/runtime-history 和 /runtime-now
    memory: 0,    // 总内存 (GB)，应由 /api/monitor/runtime-history 提供
    disk: 0       // 总磁盘 (GB)，应由 /api/monitor/runtime-history 提供
  },
  editNode: false // 控制节点编辑 UI
});

const nodeEdit = reactive({ // 节点编辑表单数据
  name: '',
  location: ''
});

const runtimeIntervalId = ref(null); // 存储 setInterval 的 ID

// ----- 数据获取和初始化核心函数 -----
const initClientData = (clientId) => {
  // 清除可能存在的旧定时器
  if (runtimeIntervalId.value) {
    clearInterval(runtimeIntervalId.value);
    runtimeIntervalId.value = null;
  }

  if (clientId !== -1 && clientId != null) { // 确保 clientId 有效
    details.base = {}; // 重置基础信息，显示加载状态
    details.runtime = { list: [], memory: 0, disk: 0 }; // 重置运行时信息

    // 1. 获取客户端基本详情
    get(`/api/monitor/details?clientId=${clientId}`, baseData => {
      if (baseData) {
        Object.assign(details.base, baseData);
      } else {
        ElMessage.error(`无法加载客户端 ${clientId} 的基本信息`);
        details.base = { name: '加载失败', id: clientId }; // 提供错误状态
      }
    });

    // 2. 获取客户端历史运行时数据 (包含总内存和总磁盘)
    get(`/api/monitor/runtime-history?clientId=${clientId}`, historyData => {
      if (historyData) {
        Object.assign(details.runtime, historyData); // historyData 应包含 list, memory, disk
        // 如果 historyData.memory 或 historyData.disk 未定义，尝试从 baseData 获取或设置默认值
        if (details.runtime.memory === undefined && details.base.memory) {
          details.runtime.memory = details.base.memory;
        }
        if (details.runtime.disk === undefined && details.base.disk) {
          details.runtime.disk = details.base.disk;
        }

      } else {
        ElMessage.error(`无法加载客户端 ${clientId} 的历史运行时信息`);
      }

      // 3. 设置定时器获取最新的运行时数据 (在历史数据获取成功后再设置，或独立设置)
      // 确保在此之前 details.runtime 结构已存在
      runtimeIntervalId.value = setInterval(() => {
        // 仅当客户端ID仍然是当前选中的ID时才获取数据
        if (props.id === clientId && details.runtime) {
          get(`/api/monitor/runtime-now?clientId=${clientId}`, currentRuntimeData => {
            if (currentRuntimeData) {
              if (details.runtime.list.length >= 360) { // 保持最多360个数据点
                details.runtime.list.splice(0, 1);
              }
              details.runtime.list.push(currentRuntimeData);
            }
          });
        } else {
          // 如果 props.id 已经改变，或者 details.runtime 不存在，清除此定时器
          if(runtimeIntervalId.value) clearInterval(runtimeIntervalId.value);
        }
      }, 10000); // 10秒轮询
    });

  } else {
    // 如果 clientId 无效 (例如 -1)，清空所有数据
    details.base = {};
    details.runtime = { list: [], memory: 0, disk: 0 };
  }
};

// ----- 监听 props.id 的变化来重新加载数据 -----
watch(() => props.id, (newId, oldId) => {
  // console.log(`ClientDetails: ID changed from ${oldId} to ${newId}`);
  initClientData(newId);
}, { immediate: true }); // immediate: true 确保组件挂载时或 id 初始赋值时执行

// ----- 组件卸载时清除定时器 -----
onUnmounted(() => {
  if (runtimeIntervalId.value) {
    clearInterval(runtimeIntervalId.value);
  }
});

// ----- UI交互方法 -----
const afterRenameUpdate = () => {
  initClientData(props.id); // 重命名成功后，重新加载当前客户端的最新数据
  // 如果需要 Manage.vue 中的 PreviewCard 上的名字也更新，Manage.vue 需要调用其 updateList
  // 可以考虑让 rename 工具函数成功后，props.update() (即 Manage.vue 的 onClientDelete/updateList) 被调用
  // 但目前 rename 工具的 after 回调是本地的。
  // 若 Manage.vue 的 onClientDelete 兼具刷新列表功能，可以考虑调用。
  // if (typeof props.update === 'function') {
  //   props.update(); // 这会调用 Manage.vue 的 onClientDelete
  // }
};

const enableNodeEdit = () => {
  details.editNode = true;
  nodeEdit.name = details.base.node;
  nodeEdit.location = details.base.location;
};

const submitNodeEdit = () => {
  post('/api/monitor/node', {
    id: props.id,
    node: nodeEdit.name,
    location: nodeEdit.location
  }, () => {
    details.editNode = false;
    initClientData(props.id); // 节点编辑成功后也重新加载数据
    ElMessage.success('节点信息已更新');
    // 同样，如果 Manage.vue 列表需要更新，需要相应机制
  });
};

function deleteClientInternal() {
  ElMessageBox.confirm('删除此主机后所有统计数据都将丢失，您确定要这样做吗？', '删除主机', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    get(`/api/monitor/delete?clientId=${props.id}`, () => {
      ElMessage.success('主机已成功移除');
      // 调用 Manage.vue 传来的 onClientDelete 方法，让 Manage.vue 处理后续（如清空 selectedClientId, 刷新列表）
      if (typeof props.update === 'function') {
        props.update();
      }
    });
  }).catch(() => {});
}

// ----- 计算属性，用于状态显示和过载告警 -----
const now = computed(() => {
  if (details.runtime && details.runtime.list && details.runtime.list.length > 0) {
    return details.runtime.list[details.runtime.list.length - 1];
  }
  return { cpuUsage: 0, memoryUsage: 0, networkUpload: 0, networkDownload: 0, diskUsage: 0, timestamp: Date.now() };
});

const isClientOverloaded = computed(() => {
  if (!details.base.online || !now.value) return false;
  // 确保 details.runtime.memory (总内存) 是有效的大于0的数值
  // now.value.memoryUsage 是已用内存 (GB)
  // details.base.memory 存储的是总内存 (GB)，从 /api/monitor/details 获取
  // details.runtime.memory 也存储总内存 (GB)，从 /api/monitor/runtime-history 获取
  // 应优先使用 details.runtime.memory，如果它被正确填充
  const totalMemoryForCalc = details.runtime.memory > 0 ? details.runtime.memory : (details.base.memory || 0);

  if (totalMemoryForCalc <= 0) return false; // 没有有效的总内存数据

  const cpuUsagePercentage = (now.value.cpuUsage || 0) * 100;
  const memoryUsagePercentage = (now.value.memoryUsage / totalMemoryForCalc) * 100;

  return cpuUsagePercentage > 80 || memoryUsagePercentage > 80;
});

const clientStatusText = computed(() => {
  if (!Object.keys(details.base).length && props.id !== -1 && props.id != null) return '加载中...';
  if (!details.base.online) return '离线';
  if (isClientOverloaded.value) return '负载告警';
  return '运行中';
});

const clientStatusIconClass = computed(() => {
  if (!details.base.online) return 'fa-solid fa-circle-stop';
  return 'fa-solid fa-circle-play';
});

const clientStatusColor = computed(() => {
  if (!details.base.online) return '#8a8a8a';
  if (isClientOverloaded.value) return 'red';
  return '#18cb18';
});

</script>

<template>
  <el-scrollbar>
    <div class="client-details" v-loading="props.id !== -1 && props.id != null && Object.keys(details.base).length === 0">
      <div v-if="props.id !== -1 && props.id != null && Object.keys(details.base).length > 0">
        <div style="display: flex;justify-content: space-between">
          <div class="title">
            <i class="fa-solid fa-server"></i>
            服务器信息
          </div>
          <div>
            <el-button :icon="Connection" type="info"
                       @click="emits('terminal', props.id)" plain text>SSH远程连接</el-button>
            <el-button :icon="Delete" type="danger" style="margin-left: 0"
                       @click="deleteClientInternal" plain text>删除此主机</el-button>
          </div>
        </div>
        <el-divider style="margin: 10px 0"/>
        <div class="details-list">
          <div>
            <span>服务器ID</span>
            <span>{{details.base.id}}</span>
          </div>
          <div>
            <span>服务器名称</span>
            <span>{{details.base.name}}</span>&nbsp;
            <i @click.stop="rename(details.base.id, details.base.name, afterRenameUpdate)"
               class="fa-solid fa-pen-to-square interact-item"/>
          </div>
          <div>
            <span>运行状态</span>
            <span>
              <i :class="clientStatusIconClass" :style="{ color: clientStatusColor }"></i>
              <span :style="{ color: clientStatusColor, marginLeft: '5px' }">{{ clientStatusText }}</span>
            </span>
          </div>
          <div v-if="!details.editNode">
            <span>服务器节点</span>
            <span>{{details.base.node}}</span>&nbsp;
            <i @click.stop="enableNodeEdit"
               class="fa-solid fa-pen-to-square interact-item"/>
          </div>
          <div v-else>
            <span>服务器节点</span>
            <div style="display: inline-block;height: 15px">
              <div style="display: flex">
                <el-select v-model="nodeEdit.location" style="width: 80px" size="small">
                  <el-option v-for="item in locations" :key="item.name" :value="item.name">
                    {{item.desc}}
                  </el-option>
                </el-select>
                <el-input v-model="nodeEdit.name" style="margin-left: 10px"
                          size="small" placeholder="请输入节点名称..."/>
                <div style="margin-left: 10px">
                  <i @click.stop="submitNodeEdit" class="fa-solid fa-check interact-item"/>
                </div>
              </div>
            </div>
          </div>
          <div>
            <span>公网IP地址</span>
            <span>
            {{details.base.ip}}
            <i class="fa-solid fa-copy interact-item" style="color: dodgerblue" @click.stop="copyIp(details.base.ip)"></i>
          </span>
          </div>
          <div style="display: flex">
            <span>处理器</span>
            <span>{{details.base.cpuName}}</span>
            <el-image style="height: 20px;margin-left: 10px" v-if="details.base.cpuName"
                      :src="`/cpu-icons/${cpuNameToImage(details.base.cpuName)}`"/>
          </div>
          <div>
            <span>硬件配置信息</span>
            <span>
              <i class="fa-solid fa-microchip"></i>
              <span style="margin-right: 10px">{{` ${details.base.cpuCore || 'N/A'} CPU 核心数 /`}}</span>
              <i class="fa-solid fa-memory"></i>
              <span>{{` ${(details.base.memory || 0).toFixed(1)} GB 内存容量`}}</span>
            </span>
          </div>
          <div>
            <span>操作系统</span>
            <i v-if="details.base.osName" :style="{color: osNameToIcon(details.base.osName).color}"
               :class="`fa-brands ${osNameToIcon(details.base.osName).icon}`"></i>
            <span style="margin-left: 10px">{{`${details.base.osName || 'N/A'} ${details.base.osVersion || ''}`}}</span>
          </div>
        </div>
        <div class="title" style="margin-top: 20px">
          <i class="fa-solid fa-gauge-high"></i>
          实时监控
        </div>
        <el-divider style="margin: 10px 0"/>
        <div v-if="details.base.online"
             v-loading="!details.runtime.list || details.runtime.list.length === 0"
             style="min-height: 200px">
          <div style="display: flex" v-if="details.runtime.list && details.runtime.list.length > 0 && now">
            <el-progress type="dashboard" :width="100" :percentage="(now.cpuUsage || 0) * 100"
                         :status="percentageToStatus((now.cpuUsage || 0) * 100)">
              <div style="font-size: 17px;font-weight: bold;color: initial">CPU</div>
              <div style="font-size: 13px;color: grey;margin-top: 5px">{{ ((now.cpuUsage || 0) * 100).toFixed(1) }}%</div>
            </el-progress>
            <el-progress style="margin-left: 20px" type="dashboard" :width="100"
                         :percentage="details.runtime.memory > 0 ? (now.memoryUsage || 0) / details.runtime.memory * 100 : 0"
                         :status="percentageToStatus(details.runtime.memory > 0 ? (now.memoryUsage || 0) / details.runtime.memory * 100 : 0)">
              <div style="font-size: 16px;font-weight: bold;color: initial">内存</div>
              <div style="font-size: 13px;color: grey;margin-top: 5px">{{ (now.memoryUsage || 0).toFixed(1) }} GB</div>
            </el-progress>
            <div style="flex: 1;margin-left: 30px;display: flex;flex-direction: column;min-height: 100px;"> <div style="flex: 1;font-size: 14px">
              <div>实时网络速度</div>
              <div>
                <i style="color: orange" class="fa-solid fa-arrow-up"></i>
                <span>{{` ${fitByUnit(now.networkUpload || 0, 'KB')}/s`}}</span>
                <el-divider direction="vertical"/>
                <i style="color: dodgerblue" class="fa-solid fa-arrow-down"></i>
                <span>{{` ${fitByUnit(now.networkDownload || 0, 'KB')}/s`}}</span>
              </div>
            </div>
              <div v-if="details.runtime.disk != null && details.runtime.disk > 0"> <div style="font-size: 13px;display: flex;justify-content: space-between">
                <div>
                  <i class="fa-solid fa-hard-drive"></i>
                  <span> 磁盘总容量</span>
                </div>
                <div>{{(now.diskUsage || 0).toFixed(1)}} GB / {{details.runtime.disk.toFixed(1)}} GB</div>
              </div>
                <el-progress type="line" :show-text="false"
                             :status="percentageToStatus((now.diskUsage || 0) / details.runtime.disk * 100)"
                             :percentage="(now.diskUsage || 0) / details.runtime.disk * 100" />
              </div>
            </div>
          </div>
          <runtime-history style="margin-top: 20px" v-if="details.runtime.list && details.runtime.list.length > 0" :data="details.runtime.list"/>
        </div>
        <el-empty description="服务器处于离线状态，请检查服务器是否正常运行" v-else-if="Object.keys(details.base).length > 0 && !details.base.online"/>
      </div>
    </div>
  </el-scrollbar>
</template>

<style scoped>
/* 样式保持不变 */
.interact-item {
  transition: .3s;
  &:hover {
    cursor: pointer;
    scale: 1.1;
    opacity: 0.8;
  }
}
.client-details {
  height: 100%;
  padding: 20px;
  .title {
    color: dodgerblue;
    font-size: 18px;
    font-weight: bold;
  }
  .details-list {
    font-size: 14px;
    & div {
      margin-bottom: 10px;
      & span:first-child {
        color: gray;
        font-size: 13px;
        font-weight: normal;
        width: 120px;
        display: inline-block;
      }
      & span {
        font-weight: bold;
      }
    }
  }
}
</style>

