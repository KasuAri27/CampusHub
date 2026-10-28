<template>
  <div class="msg-list" ref="listRef">
    <div v-if="hasMore" class="load-more" @click="$emit('loadMore')">加载更多</div>
    <MessageBubble
        v-for="msg in messages"
        :key="msg.id"
        :message="msg"
        @recall="$emit('recall', $event)"
    />
    <div v-if="!messages.length" class="msg-empty">暂无消息</div>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useUserStore } from '@/user/userStore'
import MessageBubble from '@/chat/MessageBubble.vue'

const props = defineProps({
  messages: { type: Array, default: () => [] }
})

defineEmits(['loadMore', 'recall'])

const userStore = useUserStore()
const listRef = ref(null)
const hasMore = computed(() => props.messages.length >= 30)

watch(() => props.messages.length + '_' + (props.messages.length > 0 ? props.messages[props.messages.length - 1]?.id : 0), async () => {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}, { immediate: true })
</script>

<style scoped>
.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}
.load-more {
  text-align: center;
  color: var(--primary);
  font-size: 12px;
  cursor: pointer;
  padding: 8px;
}
.msg-empty {
  text-align: center;
  color: var(--text-muted);
  margin-top: 60px;
}
</style>
