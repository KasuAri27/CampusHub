<template>
  <ol class="progress" :class="{ vertical }" aria-label="订单进度">
    <li
      v-for="(step, i) in steps"
      :key="step.key"
      :class="{ done: step.done, active: step.active }"
      :aria-current="step.active ? 'step' : undefined"
    >
      <span class="dot num" aria-hidden="true">
        <svg v-if="step.done" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><path d="M5 12l5 5 9-10"/></svg>
        <template v-else>{{ i + 1 }}</template>
      </span>
      <span class="label">{{ step.label }}</span>
      <span v-if="step.done && step.time" class="time">{{ formatDateTime(step.time) }}</span>
    </li>
  </ol>
</template>

<script setup>
import { formatDateTime } from '@/common/format'

/** 订单步骤条：履约本身是有先后顺序的流程，因此用编号表示。 */
defineProps({
  steps: { type: Array, required: true },
  vertical: { type: Boolean, default: false }
})
</script>

<style scoped>
.progress {
  display: flex;
  margin: 0;
  padding: 0;
  list-style: none;
}
.progress li {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
}
/* 连接线 */
.progress li:not(:last-child)::after {
  content: "";
  position: absolute;
  top: 11px;
  left: 28px;
  right: 6px;
  height: 2px;
  background: var(--line);
}
.progress li.done:not(:last-child)::after { background: var(--mint); }

.dot {
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  border: 2px solid var(--line-strong);
  background: var(--surface);
  color: var(--ink-3);
  font-size: var(--fs-xs);
  font-weight: 700;
}
.dot svg { width: 13px; height: 13px; }
.done .dot { background: var(--mint); border-color: var(--mint); color: #fff; }
.active .dot { border-color: var(--apricot); background: var(--apricot); color: var(--ink); }
.label { font-size: var(--fs-sm); color: var(--ink-2); }
.done .label { color: var(--ink); }
.active .label { color: var(--ink); font-weight: 700; }
.time { font-size: 11px; color: var(--ink-3); }

/* 竖排：用于详情页右栏 */
.vertical { flex-direction: column; gap: 10px; }
.vertical li { flex-direction: row; align-items: center; gap: 10px; }
.vertical li:not(:last-child)::after {
  top: 26px;
  left: 11px;
  right: auto;
  width: 2px;
  height: 8px;
}
.vertical .time { margin-left: auto; }
</style>
