<template>
  <div class="user-management" v-loading="loading">
    <h2>用户管理</h2>

    <el-row :gutter="12" style="margin-bottom:16px">
      <el-col :span="8">
        <el-input v-model="filters.keyword" placeholder="搜索姓名/邮箱" clearable @keyup.enter="search" />
      </el-col>
      <el-col :span="4">
        <el-select v-model="filters.role" placeholder="角色" clearable @change="search">
          <el-option label="需求方" value="requester" />
          <el-option label="服务方" value="provider" />
          <el-option label="管理员" value="admin" />
        </el-select>
      </el-col>
      <el-col :span="4">
        <el-button type="primary" @click="search">搜索</el-button>
      </el-col>
    </el-row>

    <el-table :data="users" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column prop="email" label="邮箱" min-width="180" />
      <el-table-column prop="phoneMasked" label="手机号" width="130" />
      <el-table-column prop="role" label="角色" width="80">
        <template #default="{ row }">
          <el-tag :type="row.role === 'admin' ? 'danger' : row.role === 'requester' ? 'primary' : 'warning'" size="small">
            {{ roleLabel(row.role) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="creditScore" label="信用分" width="80" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button
            :type="row.status === 1 ? 'warning' : 'success'"
            size="small"
            @click="toggleStatus(row)"
          >
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button type="success" size="small" plain @click="showCreditDialog(row, 'add')">
            加分
          </el-button>
          <el-button type="danger" size="small" plain @click="showCreditDialog(row, 'deduct')">
            扣分
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="text-align:center;margin-top:16px">
      <el-pagination
        v-model:current-page="pagination.page"
        :page-size="pagination.pageSize"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="fetchUsers"
      />
    </div>

    <!-- 信用分调整弹窗 -->
    <el-dialog v-model="creditDialog.visible" :title="creditDialog.mode === 'add' ? '增加信用分' : '扣除信用分'" width="400px">
      <p>用户：<strong>{{ creditDialog.user?.name }}</strong>（当前信用分：{{ creditDialog.user?.creditScore }}）</p>
      <el-input-number
        v-model="creditDialog.amount"
        :min="1"
        :max="100"
        :placeholder="creditDialog.mode === 'add' ? '请输入增加分数' : '请输入扣除分数'"
        style="width:100%"
      />
      <template #footer>
        <el-button @click="creditDialog.visible = false">取消</el-button>
        <el-button :type="creditDialog.mode === 'add' ? 'success' : 'danger'" @click="confirmCreditAdjust">
          {{ creditDialog.mode === 'add' ? '确认增加' : '确认扣除' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import adminApi from '@/admin/adminApi'

const loading = ref(false)
const users = reactive([])
const filters = reactive({ keyword: '', role: '' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const creditDialog = reactive({
  visible: false,
  mode: 'deduct',
  user: null,
  amount: 1
})

function roleLabel(r) {
  if (r === 'requester') return '需求方'
  if (r === 'provider') return '服务方'
  if (r === 'admin') return '管理员'
  return r
}

async function fetchUsers() {
  loading.value = true
  try {
    const res = await adminApi.listUsers({
      page: pagination.page, page_size: pagination.pageSize,
      keyword: filters.keyword || undefined,
      role: filters.role || undefined
    })
    const data = res.data.data
    users.splice(0, users.length, ...(data.items || []))
    pagination.total = data.pagination?.total || 0
  } catch {
    users.splice(0, users.length)
  } finally {
    loading.value = false
  }
}

function search() { pagination.page = 1; fetchUsers() }

async function toggleStatus(row) {
  try {
    await ElMessageBox.confirm(`确定要${row.status === 1 ? '禁用' : '启用'}用户「${row.name}」吗？`, '确认操作')
    await adminApi.toggleUserStatus(row.id)
    ElMessage.success('操作成功')
    row.status = row.status === 1 ? 0 : 1
  } catch { /* 取消 */ }
}

function showCreditDialog(row, mode) {
  creditDialog.mode = mode
  creditDialog.user = row
  creditDialog.amount = 1
  creditDialog.visible = true
}

async function confirmCreditAdjust() {
  const delta = creditDialog.mode === 'add' ? creditDialog.amount : -creditDialog.amount
  const label = creditDialog.mode === 'add' ? '增加' : '扣除'
  try {
    await adminApi.adjustCredit(creditDialog.user.id, delta)
    ElMessage.success(`已${label} ${creditDialog.amount} 分`)
    creditDialog.user.creditScore = Math.max(0, creditDialog.user.creditScore + delta)
    creditDialog.visible = false
  } catch { /* 忽略 */ }
}

onMounted(fetchUsers)
</script>
