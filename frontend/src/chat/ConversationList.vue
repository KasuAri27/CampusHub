<template>
  <div class="conv-list">
    <div class="conv-list-header">
      <h3>消息</h3>
    </div>
    <div class="conv-items" v-if="conversations.length">
      <ConversationItem
        v-for="c in conversations"
        :key="c.id"
        :conv="c"
        :active="c.id === activeId"
        @click="$emit('select', c.id)"
        @dismiss="id => $emit('dismiss', id)"
      />
    </div>
    <div class="conv-empty" v-else>
      <p>暂无消息</p>
    </div>
  </div>
</template>

<script setup>
import ConversationItem from '@/chat/ConversationItem.vue'

defineProps({
  conversations: { type: Array, default: () => [] },
  activeId: Number
})

defineEmits(['select'])
</script>

<style scoped>
.conv-list {
  width: 320px;
  border-right: 1px solid var(--border-color);
  display: flex;
  flex-direction: column;
  background: var(--bg-tertiary);
}
.conv-list-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
}
.conv-list-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
}
.conv-items {
  flex: 1;
  overflow-y: auto;
}
.conv-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-muted);
  font-size: 14px;
}
</style>
