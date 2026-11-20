<template>
  <div class="empty-state">
    <h3>{{ title }}</h3>
    <p v-if="description">{{ description }}</p>
    <div v-if="$slots.default" class="empty-action"><slot /></div>
  </div>
</template>

<script setup>
/** 空状态：说明为什么是空的，并给出下一步操作入口。 */
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' }
})
</script>

<style scoped>
.empty-state {
  padding: 56px 24px;
  text-align: center;
  border: 1px dashed var(--line-strong);
  border-radius: var(--r-card);
  background: var(--surface);
}
.empty-state h3 {
  font-size: var(--fs-lg);
  font-weight: 600;
}
.empty-state p {
  margin: 6px auto 0;
  max-width: 40ch;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}
.empty-action { margin-top: 16px; }
</style>
