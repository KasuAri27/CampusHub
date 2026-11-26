<template>
  <div>
    <PageHeader title="需求管理" description="审核待发布的需求，查看全部需求的履约情况，必要时删除违规需求。" />

    <form class="filters" @submit.prevent="search">
      <div class="status-chips" role="radiogroup" aria-label="状态">
        <button
          v-for="opt in statusOptions"
          :key="opt.value"
          type="button"
          role="radio"
          class="chip"
          :class="{ on: filters.status === opt.value }"
          :aria-checked="filters.status === opt.value"
          @click="setStatus(opt.value)"
        >{{ opt.label }}</button>
      </div>
      <el-input v-model.trim="filters.keyword" placeholder="按标题搜索" clearable class="kw" @clear="search" />
      <el-button native-type="submit">搜索</el-button>
    </form>

    <div class="panel table-wrap">
      <el-table :data="tasks" v-loading="loading" row-key="id">
        <el-table-column label="需求" min-width="240">
          <template #default="{ row }">
            <div class="cell-title">
              <span class="type-plate">{{ typeShort(row) }}</span>
              <router-link :to="`/tasks/${row.id}`">{{ row.title }}</router-link>
            </div>
            <div class="cell-sub">#{{ row.id }}<template v-if="row.campus">，{{ row.campus }}</template>，{{ formatDateTime(row.publishedAt) }} 提交</div>
            <div v-if="row.status === 'rejected' && row.rejectReason" class="cell-reject">驳回理由：{{ row.rejectReason }}</div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="96" align="right">
          <template #default="{ row }">
            <span v-if="row.taskType === 'TEAM'" class="muted">组队</span>
            <span v-else-if="Number(row.reward) > 0" class="num money">¥{{ formatMoney(row.reward) }}</span>
            <span v-else class="muted">免费</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <StatusTag :label="statusOf(row).label" :tone="statusOf(row).tone" />
          </template>
        </el-table-column>
        <el-table-column label="发布者 / 接单者" min-width="150">
          <template #default="{ row }">
            <div>{{ row.requesterName || '—' }}</div>
            <div class="cell-sub">接单：{{ row.providerStudentId || '暂无' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="评价" min-width="140" show-overflow-tooltip>
          <template #default="{ row }"><span :class="{ muted: !row.requesterComment }">{{ row.requesterComment || '暂无' }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="right">
          <template #default="{ row }">
            <template v-if="row.status === 'pending_review'">
              <el-button link type="primary" @click="approve(row)">通过</el-button>
              <el-button link type="danger" @click="openReject(row)">驳回</el-button>
            </template>
            <el-button v-else link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <Pagination :page="pagination.page" :page-size="pagination.pageSize" :total="pagination.total" @page-change="p => { pagination.page = p; fetchTasks() }" />

    <el-dialog v-model="reject.visible" title="驳回需求" width="440px">
      <p class="dialog-sub">驳回「{{ reject.row?.title }}」后，理由会通过系统通知发给发布者，发布者可以修改后重新提交审核。</p>
      <el-input v-model.trim="reject.reason" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="说明驳回原因" />
      <template #footer>
        <el-button @click="reject.visible = false">取消</el-button>
        <el-button type="danger" :loading="reject.submitting" @click="confirmReject">驳回需求</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import adminApi from '@/admin/adminApi'
import PageHeader from '@/common/ui/PageHeader.vue'
import StatusTag from '@/common/ui/StatusTag.vue'
import Pagination from '@/common/ui/Pagination.vue'
import { TASK_STATUS, TASK_TYPE, lookup } from '@/common/labels'
import { formatDateTime, formatMoney } from '@/common/format'

/**
 * 需求管理（GET /v1/admin/tasks，支持 status、keyword）。
 * 状态筛选与地址栏 ?status= 同步，概览页的待办可直接跳到待审核列表；
 * 待审核行就地通过/驳回，其余行可删除（后端拒绝删除已有有效付款或争议订单的需求）。
 */
const emit = defineEmits(['pending-changed'])
const route = useRoute()
const router = useRouter()

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待审核', value: 'pending_review' },
  { label: '待接单', value: 'published' },
  { label: '进行中', value: 'in_progress' },
  { label: '已完成', value: 'completed' },
  { label: '被驳回', value: 'rejected' },
  { label: '已取消', value: 'cancelled' }
]

const loading = ref(false)
const tasks = ref([])
const filters = reactive({ status: '', keyword: '' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const reject = reactive({ visible: false, row: null, reason: '', submitting: false })

function statusOf(row) { return lookup(TASK_STATUS, row.status) }
/** 管理端 taskType 只区分 TEAM / FLEA / SERVICE，SERVICE 用分类名展示。 */
function typeShort(row) { return TASK_TYPE[row.taskType]?.short || row.categoryName || '需求' }

async function fetchTasks() {
  loading.value = true
  try {
    const data = (await adminApi.listTasks({
      page: pagination.page,
      page_size: pagination.pageSize,
      status: filters.status || undefined,
      keyword: filters.keyword || undefined
    })).data.data
    tasks.value = data.items || []
    pagination.total = data.pagination?.total || 0
  } catch {
    tasks.value = []
  } finally {
    loading.value = false
  }
}

function setStatus(value) {
  router.replace({ query: value ? { status: value } : {} })
}

function search() {
  pagination.page = 1
  fetchTasks()
}

async function approve(row) {
  try {
    await adminApi.approveTask(row.id)
    ElMessage.success('已通过，需求已发布到广场')
    emit('pending-changed')
    fetchTasks()
  } catch { /* 拦截器已提示 */ }
}

function openReject(row) {
  Object.assign(reject, { visible: true, row, reason: '', submitting: false })
}

async function confirmReject() {
  reject.submitting = true
  try {
    await adminApi.rejectTask(reject.row.id, reject.reason || null)
    reject.visible = false
    ElMessage.success('已驳回，并通知了发布者')
    emit('pending-changed')
    fetchTasks()
  } catch {
    // 保留理由便于重试
  } finally {
    reject.submitting = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`删除需求「${row.title}」？此操作不可恢复；已有付款或争议订单的需求无法删除。`, '删除需求', { type: 'warning', confirmButtonText: '删除需求', cancelButtonText: '取消' })
  } catch { return }
  try {
    await adminApi.deleteTask(row.id)
    ElMessage.success('需求已删除')
    fetchTasks()
  } catch { /* 拦截器已提示 */ }
}

watch(() => route.query.status, value => {
  filters.status = typeof value === 'string' ? value : ''
  search()
}, { immediate: true })
</script>

<style scoped>
.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.status-chips { display: flex; flex-wrap: wrap; gap: 6px; margin-right: auto; }
.chip {
  padding: 4px 12px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-pill);
  background: var(--surface);
  color: var(--ink-2);
  font: inherit;
  font-size: var(--fs-sm);
  cursor: pointer;
}
.chip:hover { border-color: var(--mint); color: var(--mint); }
.chip.on { background: var(--mint); border-color: var(--mint); color: #fff; }
.kw { width: 220px; }
.table-wrap { padding: 4px 8px; }
.cell-title { display: flex; align-items: center; gap: 8px; }
.cell-title a { color: var(--ink); font-weight: 600; text-decoration: none; }
.cell-title a:hover { color: var(--mint); }
.type-plate {
  flex-shrink: 0;
  padding: 0 6px;
  background: var(--pine);
  color: #fff;
  border-radius: var(--r-sign);
  font-size: 11px;
  font-weight: 600;
}
.cell-sub { color: var(--ink-3); font-size: var(--fs-xs); }
.cell-reject { color: var(--danger); font-size: var(--fs-xs); }
.muted { color: var(--ink-3); }
.money { color: var(--amber-ink); font-weight: 600; }
.dialog-sub { color: var(--ink-2); margin-bottom: 10px; }
</style>
