<template>
  <div class="dismiss-bar">
    <span class="dismiss-text">{{ props.convType === 'TEAM_GROUP' ? '任务已完成，是否解散群聊？' : '任务已完成，是否删除私聊？' }}</span>
    <div class="dismiss-actions">
      <button class="btn-keep" @click="$emit('keep')">{{ props.convType === 'TEAM_GROUP' ? '保留群聊' : '保留私聊' }}</button>
      <button class="btn-dismiss" @click="$emit('dismiss')">{{ props.convType === 'TEAM_GROUP' ? '解散群聊' : '删除私聊' }}</button>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({ convType: String })
defineEmits(['keep', 'dismiss'])
</script>

<style scoped>
.dismiss-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  background: rgba(245, 158, 11, 0.1);
  border-bottom: 1px solid rgba(245, 158, 11, 0.2);
}
.dismiss-text {
  font-size: 13px;
  color: var(--accent-dark);
  font-weight: 500;
}
.dismiss-actions {
  display: flex;
  gap: 8px;
}
.btn-keep, .btn-dismiss {
  padding: 4px 14px;
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all var(--transition-fast);
}
.btn-keep {
  background: var(--primary);
  color: #fff;
}
.btn-keep:hover {
  opacity: 0.9;
}
.btn-dismiss {
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
}
.btn-dismiss:hover {
  border-color: var(--error);
  color: var(--error);
}
</style>
