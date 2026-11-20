<template>
  <span class="status-tag" :class="`tone-${tone}`">
    <span class="status-dot" aria-hidden="true"></span>
    <slot>{{ label }}</slot>
  </span>
</template>

<script setup>
/** 状态胶囊：色调取自 common/labels 的 tone，颜色语义全站一致。 */
defineProps({
  label: { type: String, default: '' },
  tone: { type: String, default: 'muted' }
})
</script>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: var(--r-pill);
  font-size: var(--fs-xs);
  font-weight: 600;
  line-height: 20px;
  white-space: nowrap;
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.tone-brand { background: var(--mint-wash); color: var(--mint-hover); }
.tone-info { background: var(--info-wash); color: var(--info); }
.tone-warn { background: var(--apricot-wash); color: var(--amber-ink); }
.tone-danger { background: var(--danger-wash); color: var(--danger); }
.tone-muted { background: #eef2f0; color: var(--ink-2); }
</style>
