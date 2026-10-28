<template>
  <div>
    <div
        class="msg-bubble"
        :class="{ mine: isMine, system: message.msgType === 'SYSTEM' || message.isRecalled }"
        @contextmenu.prevent="onRightClick"
    >
      <template v-if="message.isRecalled">
        <span class="system-text">{{ isMine ? '你撤回了一条消息' : (message.senderName || '对方') + '撤回了一条消息' }}</span>
      </template>

      <template v-else-if="message.msgType === 'SYSTEM'">
        <span class="system-text">{{ message.content }}</span>
      </template>

      <template v-else>
        <div class="bubble-sender" v-if="!isMine">{{ message.senderName }}</div>
        <div class="bubble-content" :class="{ 'is-image': message.msgType === 'IMAGE' }">
          <span v-if="message.msgType === 'TEXT'">{{ message.content }}</span>

          <el-image
              v-else-if="message.msgType === 'IMAGE'"
              :src="normalizeFileUrl(message.content)"
              :preview-src-list="[normalizeFileUrl(message.content)]"
              fit="cover"
              class="chat-image"
              preview-teleported
              hide-on-click-modal
          >
            <template #placeholder>
              <div class="image-slot">加载中...</div>
            </template>
            <template #error>
              <div class="image-slot">加载失败</div>
            </template>
          </el-image>

          <span v-else>{{ message.content }}</span>
        </div>
        <div class="bubble-time">{{ formatTime(message.createdAt) }}</div>
      </template>
    </div>

    <ul
        v-show="showMenu"
        class="context-menu"
        :style="{ top: menuY + 'px', left: menuX + 'px' }"
    >
      <li @click="handleRecall">撤回消息</li>
    </ul>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { ElImage, ElMessage } from 'element-plus'
import { useUserStore } from '@/user/userStore'

const props = defineProps({ message: Object })
const emit = defineEmits(['recall'])
const userStore = useUserStore()

const isMine = computed(() => props.message.senderId !== 0 && props.message.senderId === userStore.userId)

// --- 右键菜单相关状态 ---
const showMenu = ref(false)
const menuX = ref(0)
const menuY = ref(0)

function onRightClick(e) {
  // 1. 只有点击自己的消息，且不是系统消息/已撤回消息，才会弹出右键菜单
  if (!isMine.value || props.message.msgType === 'SYSTEM' || props.message.isRecalled) {
    return
  }

  // 只控制菜单展示，不再在这里校验时间
  showMenu.value = true
  menuX.value = e.clientX
  menuY.value = e.clientY
}

function handleRecall() {
  // 2. 在用户真正点击“撤回消息”按钮时，才进行时间校验
  const msgTime = new Date(props.message.createdAt).getTime()
  const nowTime = Date.now()
  if (nowTime - msgTime > 2 * 60 * 1000) {
    ElMessage.warning('只能撤回2分钟内的消息')
    showMenu.value = false // 隐藏菜单
    return
  }

  // 3. 校验通过，发射撤回事件
  emit('recall', props.message)
  showMenu.value = false
}

function closeMenu() {
  showMenu.value = false
}

// 监听全局点击事件，点击其他区域关闭菜单
onMounted(() => {
  window.addEventListener('click', closeMenu)
  // 如果在滚动时也希望关闭菜单，可以取消下面这行的注释：
  // window.addEventListener('scroll', closeMenu, true)
})
onBeforeUnmount(() => {
  window.removeEventListener('click', closeMenu)
  // window.removeEventListener('scroll', closeMenu, true)
})
// -----------------------

const API_ORIGIN = 'http://localhost:8080'
function normalizeFileUrl(url) {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  return `${API_ORIGIN}${url}`
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  return `${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}
</script>

<style scoped>
.msg-bubble { margin-bottom: 14px; display: flex; flex-direction: column; position: relative;}
.msg-bubble.mine { align-items: flex-end; }
.msg-bubble.system { align-items: center; }
.system-text { font-size: 12px; color: var(--text-muted); background: var(--bg-tertiary); padding: 4px 12px; border-radius: var(--radius-full); }
.bubble-sender { font-size: 12px; color: var(--text-muted); margin-bottom: 2px; padding-left: 4px; }
.bubble-content { max-width: 70%; padding: 10px 14px; border-radius: var(--radius-lg); font-size: 14px; line-height: 1.5; background: var(--bg-tertiary); color: var(--text-primary); }
.mine .bubble-content { background: var(--primary); color: #fff; }
.bubble-time { font-size: 11px; color: var(--text-muted); margin-top: 2px; }
.bubble-content.is-image { padding: 0; background: transparent !important; }

/* 图像相关样式... (保留原样) */
.chat-image {
  max-width: 220px;
  max-height: 220px;
  border-radius: 8px;
  display: block;
  border: 1px solid var(--border-color);
  cursor: pointer;
  overflow: hidden;
  transition: opacity var(--transition-fast);
}
.chat-image:hover { opacity: 0.9; }
.image-slot { display: flex; justify-content: center; align-items: center; width: 150px; height: 150px; background: var(--bg-tertiary); color: var(--text-muted); font-size: 12px; }

/* 右键菜单样式 */
.context-menu {
  position: fixed; /* 使用 fixed 定位脱离文档流跟随鼠标 */
  z-index: 9999;
  background: white;
  border: 1px solid #ebeef5;
  box-shadow: 0 2px 12px 0 rgba(0,0,0,0.1);
  border-radius: 4px;
  padding: 5px 0;
  margin: 0;
  list-style: none;
  min-width: 100px;
}
.context-menu li {
  padding: 8px 20px;
  font-size: 13px;
  cursor: pointer;
  color: #606266;
  text-align: center;
}
.context-menu li:hover {
  background-color: #f5f7fa;
  color: var(--primary);
}
</style>