<template>
  <div v-if="total > 0" class="pagination">
    <el-pagination
      :current-page="page"
      :page-size="pageSize"
      :total="total"
      :page-sizes="pageSizes"
      :layout="pageSizes.length ? 'total, sizes, prev, pager, next' : 'total, prev, pager, next'"
      background
      @current-change="$emit('page-change', $event)"
      @size-change="$emit('size-change', $event)"
    />
  </div>
</template>

<script setup>
/** 分页器：页码由父组件持有，本组件只发出翻页事件；传入 pageSizes 时显示每页条数选择。 */
defineProps({
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 20 },
  total: { type: Number, default: 0 },
  pageSizes: { type: Array, default: () => [] }
})
defineEmits(['page-change', 'size-change'])
</script>

<style scoped>
.pagination {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}
</style>
