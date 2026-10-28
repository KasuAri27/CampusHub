<template>
  <div class="task-management" v-loading="loading">
    <h2>需求管理</h2>

    <el-row :gutter="12" style="margin-bottom:16px">
      <el-col :span="6">
        <el-select v-model="filters.status" placeholder="按状态筛选" clearable @change="search">
          <el-option label="审核中" value="pending_review" />
          <el-option label="已发布" value="published" />
          <el-option label="进行中" value="in_progress" />
          <el-option label="已完成" value="completed" />
          <el-option label="已取消" value="cancelled" />
        </el-select>
      </el-col>
    </el-row>

    <el-table :data="tasks" border stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column prop="categoryName" label="分类" width="100" />
      <el-table-column prop="reward" label="报酬" width="80">
        <template #default="{ row }">¥{{ row.reward || 0 }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="requesterName" label="发布者" width="100" />

      <!-- ================= 新增的两列 开始 ================= -->
      <el-table-column prop="providerStudentId" label="接单者" width="110">
        <template #default="{ row }">
          {{ row.providerStudentId || '-' }}
        </template>
      </el-table-column>

      <el-table-column prop="requesterComment" label="评价" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.requesterComment || '-' }}
        </template>
      </el-table-column>
      <!-- ================= 新增的两列 结束 ================= -->

      <el-table-column prop="publishedAt" label="发布时间" width="160">
        <template #default="{ row }">{{ formatTime(row.publishedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="80">
        <template #default="{ row }">
          <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="text-align:center;margin-top:16px">
      <el-pagination
        v-model:current-page="pagination.page"
        :page-size="pagination.pageSize"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="fetchTasks"
      />
    </div>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import adminApi from '@/admin/adminApi'

const loading = ref(false)
const tasks = reactive([])
const filters = reactive({ status: '' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

const statusMap = { pending_review: ['审核中', 'warning'], published: ['待接单', 'primary'], in_progress: ['进行中', 'primary'], completed: ['已完成', 'info'], cancelled: ['已取消', 'info'] }
function statusLabel(s) { return statusMap[s]?.[0] || s }
function statusType(s) { return statusMap[s]?.[1] || 'info' }
function formatTime(d) { return d ? new Date(d).toLocaleString('zh-CN') : '' }

async function fetchTasks() {
  loading.value = true
  try {
    const res = await adminApi.listTasks({
      page: pagination.page, page_size: pagination.pageSize,
      status: filters.status || undefined
    })
    const data = res.data.data
    tasks.splice(0, tasks.length, ...(data.items || []))
    pagination.total = data.pagination?.total || 0
  } catch {
    tasks.splice(0, tasks.length)
  } finally {
    loading.value = false
  }
}

function search() { pagination.page = 1; fetchTasks() }

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除需求「${row.title}」吗？此操作不可恢复。`, '确认删除', { type: 'warning' })
    await adminApi.deleteTask(row.id)
    ElMessage.success('已删除')
    fetchTasks()
  } catch { /* 取消 */ }
}

onMounted(fetchTasks)
</script>
