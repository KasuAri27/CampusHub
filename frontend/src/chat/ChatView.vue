<template>
  <div class="chat-page">
    <div class="chat-layout">
      <ConversationList
        :conversations="store.conversations"
        :active-id="activeConvId"
        @select="selectConv"
        @dismiss="handleConvDismiss"
      />
      <div class="chat-main">
        <template v-if="activeConv">
          <ChatWindow
            :conversation="activeConv"
            :messages="store.messages[activeConvId] || []"
            @send="handleSend"
            @loadMore="loadMore"
            @keepGroup="handleKeepGroup"
            @dismissGroup="handleDismissGroup"
            @recall="handleRecall"
          />
        </template>
        <EmptyChat v-else />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useChatStore } from '@/chat/chatStore'
import chatApi from '@/chat/chatApi'
import { ElMessage } from 'element-plus'
import ConversationList from '@/chat/ConversationList.vue'
import ChatWindow from '@/chat/ChatWindow.vue'
import EmptyChat from '@/chat/EmptyChat.vue'

const route = useRoute()
const store = useChatStore()
const activeConvId = ref(null)

const activeConv = computed(() =>
  store.conversations.find(c => c.id === activeConvId.value) || null
)

async function selectConv(id) {
  activeConvId.value = id
  if (!store.messages[id]) {
    await store.fetchMessages(id)
  }
  await store.markConversationRead(id)
}

function handleSend(payload) {
  if (!activeConvId.value) return
  // 适配字符串或对象载荷
  const content = typeof payload === 'string' ? payload : payload.content;
  const msgType = typeof payload === 'string' ? 'TEXT' : payload.msgType;
  if (!content || !content.trim()) return
  store.sendMessage(activeConvId.value, content.trim(), msgType)
}

function loadMore() {
  if (!activeConvId.value) return
  const msgs = store.messages[activeConvId.value] || []
  const page = Math.ceil(msgs.length / 30) + 1
  store.fetchMessages(activeConvId.value, page)
}

async function handleKeepGroup() {
  if (!activeConvId.value) return
  try {
    await chatApi.keepGroup(activeConvId.value)
    ElMessage.success('已保留群聊')
    store.fetchConversations()
  } catch { ElMessage.error('操作失败') }
}

async function handleDismissGroup() {
  if (!activeConvId.value) return
  await dismissConv(activeConvId.value)
}

async function handleConvDismiss(convId) {
  await dismissConv(convId)
}

async function dismissConv(convId) {
  try {
    await chatApi.dismissGroup(convId)
    ElMessage.success('已删除')
    if (activeConvId.value === convId) activeConvId.value = null
    store.fetchConversations()
  } catch { ElMessage.error('操作失败') }
}

function handleRecall(msg) {
  store.recallMessage(msg)
}

onMounted(async () => {
  await store.fetchConversations()
  store.connectWebSocket()
  if (route.params.convId) {
    await selectConv(Number(route.params.convId))
  }
})

onUnmounted(() => {})

watch(() => route.params.convId, (val) => {
  if (val) {
    selectConv(Number(val))
  }
})
</script>

<style scoped>
.chat-page {
  max-width: 1100px;
  margin: 0 auto;
  height: calc(100vh - 120px);
}

.chat-layout {
  display: flex;
  height: 100%;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-xl);
  overflow: hidden;
  background: var(--bg-secondary);
  box-shadow: var(--shadow-md);
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}
</style>
