<template>
  <div class="conv-item" :class="{ active }" @click="$emit('click')" @contextmenu.prevent="showMenu = true">
    <div class="conv-icon">{{ icon }}</div>
    <div class="conv-body">
      <div class="conv-top">
        <span class="conv-name">{{ displayName }}</span>
        <span class="conv-time">{{ timeText }}</span>
      </div>
      <div class="conv-bottom">
        <span class="conv-last-msg">{{ previewText }}</span>
        <span v-if="conv.unreadCount" class="conv-badge">{{ conv.unreadCount > 99 ? '99+' : conv.unreadCount }}</span>
      </div>
    </div>
    <div v-if="showMenu && canDismissAny" class="ctx-menu" @click.stop>
      <div class="ctx-item danger" @click="handleDismiss">解散{{ conv.convType === 'TEAM_GROUP' ? '群聊' : '私聊' }}</div>
      <div class="ctx-item" @click="showMenu = false">取消</div>
    </div>
    <div v-if="showMenu" class="ctx-overlay" @click="showMenu = false"></div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useUserStore } from '@/user/userStore'

const props = defineProps({
  conv: Object,
  active: Boolean
})

const emit = defineEmits(['click', 'dismiss'])
const userStore = useUserStore()
const showMenu = ref(false)

const icon = computed(() => {
  if (props.conv.convType === 'SYSTEM_NOTIFY') return '🔔'
  if (props.conv.convType === 'TEAM_GROUP') return '👥'
  return '💬'
})

const displayName = computed(() => {
  if (props.conv.convType === 'SYSTEM_NOTIFY') return '系统通知'
  if (props.conv.convType === 'TEAM_GROUP') return props.conv.groupName || '群聊'
  if (props.conv.convType === 'TASK_PRIVATE') return props.conv.otherUserName || '私聊'
  return '私聊'
})

const timeText = computed(() => {
  const t = props.conv.lastMessage?.createdAt
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth()+1}/${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
})

// 【核心修改点：根据消息类型动态生成预览文本】
const previewText = computed(() => {
  const msg = props.conv.lastMessage
  if (!msg) return '暂无消息'

  if (msg.isRecalled) {
    const isMine = msg.senderId === userStore.userId;
    return isMine ? '你撤回了一条消息' : `${msg.senderName || '对方'} 撤回了一条消息`
  }

  if (msg.msgType === 'IMAGE') {
    return '[图片]'
  }

  return msg.content || '暂无消息'
})

const canDismissAny = computed(() => {
  if (props.conv.convType === 'SYSTEM_NOTIFY') return false
  if (props.conv.convType === 'TEAM_GROUP') {
    return props.conv.ownerId != null && props.conv.ownerId === userStore.userId
  }
  return props.conv.isOwner === true
})

function handleDismiss() {
  showMenu.value = false
  emit('dismiss', props.conv.id)
}
</script>

<style scoped>
.conv-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px;
  cursor: pointer;
  transition: background var(--transition-fast);
  border-bottom: 1px solid rgba(0,0,0,0.03);
  position: relative;
}
.conv-item:hover { background: rgba(16,185,129,0.05); }
.conv-item.active { background: rgba(16,185,129,0.1); }
.conv-icon { font-size: 32px; flex-shrink: 0; }
.conv-body { flex: 1; min-width: 0; }
.conv-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.conv-name { font-size: 14px; font-weight: 600; color: var(--text-primary); }
.conv-time { font-size: 11px; color: var(--text-muted); }
.conv-bottom { display: flex; justify-content: space-between; align-items: center; }
.conv-last-msg { font-size: 13px; color: var(--text-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 180px; }
.conv-badge { background: var(--error); color: #fff; font-size: 11px; font-weight: 600; padding: 1px 6px; border-radius: var(--radius-full); min-width: 18px; text-align: center; }
.ctx-overlay { position: fixed; inset: 0; z-index: 99; }
.ctx-menu { position: absolute; right: 8px; top: 40px; z-index: 100; background: var(--bg-secondary); border: 1px solid var(--border-color); border-radius: var(--radius-md); box-shadow: var(--shadow-lg); min-width: 120px; overflow: hidden; }
.ctx-item { padding: 8px 14px; font-size: 13px; cursor: pointer; transition: background var(--transition-fast); }
.ctx-item:hover { background: var(--bg-tertiary); }
.ctx-item.danger { color: var(--error); }
.ctx-item.danger:hover { background: rgba(239,68,68,0.08); }
</style>