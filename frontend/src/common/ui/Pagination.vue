<template>
  <div class="pagination-wrapper" v-if="total > 0">
    <el-pagination
      v-model:current-page="current"
      :page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      background
      @current-change="$emit('page-change', $event)"
      @size-change="$emit('size-change', $event)"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  total: { type: Number, default: 0 }
})

defineEmits(['page-change', 'size-change'])

const current = computed({
  get: () => props.page,
  set: val => {}
})
</script>

<style scoped>
.pagination-wrapper { 
  display: flex; 
  justify-content: center; 
  margin-top: 24px; 
}

/* Element Plus 分页样式覆盖 */
:deep(.el-pagination) {
  --el-pagination-button-bg-color: var(--bg-secondary);
  --el-pagination-hover-color: var(--primary);
}

:deep(.el-pagination.is-background .el-pager li) {
  border-radius: var(--radius-md);
  font-weight: 500;
  transition: all var(--transition-base);
}

:deep(.el-pagination.is-background .el-pager li:not(.is-disabled).is-active) {
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.25);
}

:deep(.el-pagination.is-background .btn-prev),
:deep(.el-pagination.is-background .btn-next) {
  border-radius: var(--radius-md);
  transition: all var(--transition-base);
}

:deep(.el-pagination__sizes .el-select) {
  width: 110px;
}

:deep(.el-select__wrapper) {
  border-radius: var(--radius-md) !important;
}
</style>
