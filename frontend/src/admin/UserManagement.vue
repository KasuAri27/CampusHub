<template>
  <div>
    <PageHeader title="用户管理" description="查看账号与信用分，处理禁用和信用分调整。信用分低于 70 的用户不能发布和接单。" />

    <form class="filters" @submit.prevent="search">
      <el-input v-model.trim="filters.keyword" placeholder="学号、邮箱或姓名" clearable class="kw" @clear="search" />
      <el-select v-model="filters.role" placeholder="全部角色" clearable class="role" @change="search">
        <el-option label="普通用户" value="user" />
        <el-option label="管理员" value="admin" />
      </el-select>
      <el-button native-type="submit">搜索</el-button>
    </form>

    <div class="panel table-wrap">
      <el-table :data="users" v-loading="loading" row-key="id">
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="cell-name">{{ row.nickname || row.name }}<span v-if="row.role === 'admin'" class="admin-tag">管理员</span></div>
            <div class="cell-sub">#{{ row.id }}，{{ row.name }}</div>
          </template>
        </el-table-column>
        <el-table-column label="联系方式" min-width="200">
          <template #default="{ row }">
            <div>{{ row.email || '未绑定邮箱' }}</div>
            <div class="cell-sub">{{ row.phoneMasked || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="信用分" width="96" align="right">
          <template #default="{ row }">
            <span class="num credit" :class="creditTone(row.creditScore)">{{ row.creditScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <StatusTag :label="row.status === 1 ? '正常' : '已禁用'" :tone="row.status === 1 ? 'brand' : 'danger'" />
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="128">
          <template #default="{ row }"><span class="cell-sub">{{ formatDateTime(row.createTime) }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="right">
          <template #default="{ row }">
            <template v-if="row.role !== 'admin'">
              <el-button link type="primary" @click="openCredit(row)">调整信用分</el-button>
              <el-button link :type="row.status === 1 ? 'danger' : 'primary'" @click="toggleStatus(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <Pagination :page="pagination.page" :page-size="pagination.pageSize" :total="pagination.total" @page-change="p => { pagination.page = p; fetchUsers() }" />

    <el-dialog v-model="credit.visible" title="调整信用分" width="420px">
      <p class="dialog-sub">{{ credit.user?.nickname || credit.user?.name }}，当前 <span class="num">{{ credit.user?.creditScore }}</span> 分</p>
      <el-radio-group v-model="credit.mode" class="mode" :disabled="credit.saving">
        <el-radio-button value="deduct">扣分</el-radio-button>
        <el-radio-button value="add">加分</el-radio-button>
      </el-radio-group>
      <el-input-number v-model="credit.amount" :min="1" :max="100" :disabled="credit.saving" class="amount" />
      <p class="preview">调整后 <span class="num">{{ previewScore }}</span> 分<span v-if="previewScore < 70" class="warn">，低于 70 分将不能发布和接单</span></p>
      <p class="note">信用分范围 0–100，变动会记入信用流水并通知该用户。</p>
      <template #footer>
        <el-button @click="credit.visible = false">取消</el-button>
        <el-button :type="credit.mode === 'add' ? 'primary' : 'danger'" :loading="credit.saving" @click="confirmCredit">
          {{ credit.mode === 'add' ? `加 ${credit.amount} 分` : `扣 ${credit.amount} 分` }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import adminApi from '@/admin/adminApi'
import PageHeader from '@/common/ui/PageHeader.vue'
import StatusTag from '@/common/ui/StatusTag.vue'
import Pagination from '@/common/ui/Pagination.vue'
import { formatDateTime } from '@/common/format'

/**
 * 用户管理（/v1/admin/users）。
 * 信用分调整携带 operationId：打开弹窗时生成一次，同一次调整的重试复用该编号，
 * 后端据此去重（模块契约 §3.2），网络超时后再点一次不会重复扣分；
 * 修改方向或分值视为新的操作，重新生成编号。
 */
const loading = ref(false)
const users = ref([])
const filters = reactive({ keyword: '', role: '' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })
const credit = reactive({ visible: false, saving: false, user: null, mode: 'deduct', amount: 5, operationId: '' })

const previewScore = computed(() => {
  const base = Number(credit.user?.creditScore ?? 0)
  const next = credit.mode === 'add' ? base + credit.amount : base - credit.amount
  return Math.min(100, Math.max(0, next))
})

function creditTone(score) {
  if (score < 70) return 'low'
  if (score < 90) return 'mid'
  return 'ok'
}

/** 生成符合后端规则的操作编号（字母、数字、连字符，≤36 位）。 */
function newOperationId() {
  return globalThis.crypto?.randomUUID?.() || `op-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

async function fetchUsers() {
  loading.value = true
  try {
    const data = (await adminApi.listUsers({
      page: pagination.page,
      page_size: pagination.pageSize,
      keyword: filters.keyword || undefined,
      role: filters.role || undefined
    })).data.data
    users.value = data.items || []
    pagination.total = data.pagination?.total || 0
  } catch {
    users.value = []
  } finally {
    loading.value = false
  }
}

function search() {
  pagination.page = 1
  fetchUsers()
}

async function toggleStatus(row) {
  const disabling = row.status === 1
  try {
    await ElMessageBox.confirm(
      disabling ? `禁用「${row.nickname || row.name}」？禁用后只能处理本人订单、付款和申诉，不能发布或接单。` : `恢复「${row.nickname || row.name}」的正常使用？`,
      disabling ? '禁用账号' : '启用账号',
      { type: disabling ? 'warning' : 'info', confirmButtonText: disabling ? '禁用账号' : '启用账号', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    await adminApi.toggleUserStatus(row.id)
    ElMessage.success(disabling ? '账号已禁用' : '账号已启用')
    fetchUsers()
  } catch { /* 拦截器已提示 */ }
}

function openCredit(row) {
  Object.assign(credit, { visible: true, saving: false, user: row, mode: 'deduct', amount: 5, operationId: newOperationId() })
}

// 方向或分值改变即为另一笔调整，换新编号，避免被后端当作“同编号参数不一致”拒绝
watch(() => [credit.mode, credit.amount], () => {
  if (credit.visible && !credit.saving) credit.operationId = newOperationId()
})

async function confirmCredit() {
  credit.saving = true
  const delta = credit.mode === 'add' ? credit.amount : -credit.amount
  try {
    await adminApi.adjustCredit(credit.user.id, delta, credit.operationId)
    ElMessage.success(`已${credit.mode === 'add' ? '加' : '扣'} ${credit.amount} 分`)
    credit.visible = false
    fetchUsers()
  } catch {
    // 保留同一 operationId，重试不会重复调整
  } finally {
    credit.saving = false
  }
}

onMounted(fetchUsers)
</script>

<style scoped>
.filters { display: flex; gap: 10px; margin-bottom: 14px; }
.kw { width: 260px; }
.role { width: 130px; }
.table-wrap { padding: 4px 8px; }
.cell-name { font-weight: 600; display: flex; align-items: center; gap: 8px; }
.cell-sub { color: var(--ink-3); font-size: var(--fs-xs); }
.admin-tag {
  padding: 0 6px;
  border-radius: var(--r-sign);
  background: var(--pine);
  color: #fff;
  font-size: 11px;
}
.credit { font-size: 17px; font-weight: 600; }
.credit.ok { color: var(--mint-hover); }
.credit.mid { color: var(--amber-ink); }
.credit.low { color: var(--danger); }
.dialog-sub { color: var(--ink-2); margin-bottom: 14px; }
.mode { margin-right: 12px; }
.amount { width: 140px; }
.preview { margin-top: 14px; }
.preview .num { font-size: 18px; font-weight: 600; }
.warn { color: var(--danger); }
.note { margin-top: 6px; color: var(--ink-3); font-size: var(--fs-xs); }
</style>
