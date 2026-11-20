<template>
  <header class="page-header">
    <div class="page-header-text">
      <h1>{{ title }}</h1>
      <p v-if="description || $slots.description" class="page-header-desc">
        <slot name="description">{{ description }}</slot>
      </p>
    </div>
    <div v-if="$slots.actions" class="page-header-actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup>
/** 页面标题区：左侧标题与一句说明，右侧放本页主操作。所有页面统一左对齐。 */
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' }
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
}
.page-header h1 {
  font-size: var(--fs-2xl);
  font-weight: 700;
}
.page-header-desc {
  margin-top: 4px;
  color: var(--ink-2);
  font-size: var(--fs-md);
  max-width: 60ch;
}
.page-header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
@media (max-width: 640px) {
  .page-header { flex-direction: column; align-items: stretch; }
}
</style>
