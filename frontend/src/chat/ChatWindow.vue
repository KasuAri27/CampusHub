<template>
  <div class="chat-window">
    <div class="chat-header">
      <span class="chat-title">{{ title }}</span>
      <span class="chat-meta" v-if="conversation.convType === 'TEAM_GROUP'">
        {{ conversation.memberCount }}/{{ conversation.maxMembers }} 人
      </span>
    </div>
    <GroupDismissBar
      v-if="conversation.canDismiss"
      :conv-type="conversation.convType"
      @keep="$emit('keepGroup')"
      @dismiss="$emit('dismissGroup')"
    />
    <MessageList
        :messages="messages"
        @loadMore="$emit('loadMore')"
        @recall="$emit('recall', $event)"
    />
    <MessageInput v-if="conversation.convType !== 'SYSTEM_NOTIFY'" @send="$emit('send', $event)" />
    <div v-else class="system-notice-bar">系统通知，仅可查看</div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import MessageList from '@/chat/MessageList.vue'
import MessageInput from '@/chat/MessageInput.vue'
import GroupDismissBar from '@/chat/GroupDismissBar.vue'

const props = defineProps({
  conversation: Object,
  messages: { type: Array, default: () => [] }
})

defineEmits(['send', 'loadMore', 'keepGroup', 'dismissGroup', 'recall'])
const title = computed(() => {
  if (props.conversation.convType === 'SYSTEM_NOTIFY') return '系统通知'
  if (props.conversation.convType === 'TEAM_GROUP') return props.conversation.groupName || '群聊'
  if (props.conversation.convType === 'TASK_PRIVATE') {
    const name = props.conversation.otherUserName || '对方'
    const task = props.conversation.taskTitle || ''
    return task ? `${name} · ${task}` : name
  }
  return '私聊'
})
</script>

<style scoped>
.chat-window {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.chat-header {
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 10px;
}
.chat-title { font-size: 16px; font-weight: 600; color: var(--text-primary); }
.chat-meta { font-size: 12px; color: var(--text-muted); }
.system-notice-bar { padding: 12px 20px; text-align: center; color: var(--text-muted); font-size: 13px; border-top: 1px solid var(--border-color); background: var(--bg-tertiary); }
</style>
