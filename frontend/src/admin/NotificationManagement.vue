<template>
  <div>
    <PageHeader title="系统通知" description="向单个用户或全体用户发送站内通知，用户在“消息”的系统通知里查看。" />

    <div class="layout">
      <form class="panel composer" @submit.prevent="send">
        <h2>发送通知</h2>
        <div class="target" role="radiogroup" aria-label="发送对象">
          <label :class="{ on: target === 'one' }"><input v-model="target" type="radio" value="one" /> 指定用户</label>
          <label :class="{ on: target === 'all' }"><input v-model="target" type="radio" value="all" /> 全体用户</label>
        </div>
        <el-input-number
          v-if="target === 'one'"
          v-model="form.receiverId"
          :min="1"
          :controls="false"
          placeholder="用户 ID（在用户管理中查看）"
          class="receiver"
        />
        <el-input v-model.trim="form.title" maxlength="50" show-word-limit placeholder="标题" />
        <el-input v-model.trim="form.content" type="textarea" :rows="5" maxlength="500" show-word-limit placeholder="通知内容" />
        <p v-if="target === 'all'" class="warn">全体广播会给每个账号各写一条通知，发出后无法撤回。</p>
        <el-button type="primary" native-type="submit" :loading="sending">{{ target === 'all' ? '广播给全体用户' : '发送给该用户' }}</el-button>
      </form>

      <div class="panel history">
        <div class="history-head">
          <h2>通知记录</h2>
          <span class="muted">包含系统自动发出的审核、订单等通知</span>
        </div>
        <el-table :data="records" v-loading="loading" size="small">
          <el-table-column label="接收人" width="80">
            <template #default="{ row }"><span class="num">#{{ row.receiverId }}</span></template>
          </el-table-column>
          <el-table-column label="内容" min-width="260">
            <template #default="{ row }">
              <div class="rec-title">{{ row.title }}</div>
              <div class="rec-content">{{ row.content }}</div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="76">
            <template #default="{ row }">
              <StatusTag :label="row.isRead ? '已读' : '未读'" :tone="row.isRead ? 'muted' : 'warn'" />
            </template>
          </el-table-column>
          <el-table-column label="时间" width="128">
            <template #default="{ row }"><span class="time">{{ formatDateTime(row.createTime) }}</span></template>
          </el-table-column>
        </el-table>
        <Pagination :page="page" :page-size="pageSize" :total="total" @page-change="p => { page = p; load() }" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import adminApi from '@/admin/adminApi'
import PageHeader from '@/common/ui/PageHeader.vue'
import StatusTag from '@/common/ui/StatusTag.vue'
import Pagination from '@/common/ui/Pagination.vue'
import { formatDateTime } from '@/common/format'

/**
 * 系统通知（GET/POST /v1/admin/notifications）。
 * receiverId 为空时后端向全部账号逐条写入通知，属于不可撤回的群发，发送前二次确认。
 */
const target = ref('one')
const form = reactive({ receiverId: undefined, title: '', content: '' })
const sending = ref(false)
const records = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = 15
const total = ref(0)

async function load() {
  loading.value = true
  try {
    const data = (await adminApi.listNotifications({ page: page.value, page_size: pageSize })).data.data
    records.value = data.items || []
    total.value = data.pagination?.total || 0
  } catch {
    records.value = []
  } finally {
    loading.value = false
  }
}

async function send() {
  if (target.value === 'one' && !form.receiverId) { ElMessage.warning('请填写接收人的用户 ID'); return }
  if (!form.title || !form.content) { ElMessage.warning('请填写标题和内容'); return }
  if (target.value === 'all') {
    try {
      await ElMessageBox.confirm('确定向全体用户广播这条通知？发出后无法撤回。', '全体广播', { type: 'warning', confirmButtonText: '广播', cancelButtonText: '再想想' })
    } catch { return }
  }
  sending.value = true
  try {
    await adminApi.sendNotification({
      receiverId: target.value === 'one' ? form.receiverId : null,
      title: form.title,
      content: form.content,
      type: 'SYSTEM'
    })
    ElMessage.success(target.value === 'all' ? '已广播给全体用户' : '通知已发送')
    form.title = ''
    form.content = ''
    page.value = 1
    await load()
  } catch {
    // 拦截器已提示错误，保留已填写内容便于重试
  } finally {
    sending.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 20px;
  align-items: start;
}
.composer {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
}
.composer h2, .history h2 { font-size: var(--fs-lg); font-weight: 600; }
.target { display: flex; gap: 8px; }
.target label {
  flex: 1;
  padding: 7px 10px;
  border: 1px solid var(--line-strong);
  border-radius: 6px;
  font-size: var(--fs-sm);
  text-align: center;
  cursor: pointer;
}
.target label.on { border-color: var(--mint); background: var(--mint-wash); color: var(--pine); font-weight: 600; }
.target input { position: absolute; opacity: 0; pointer-events: none; }
.target label:focus-within { outline: 2px solid var(--mint); outline-offset: 2px; }
.receiver { width: 100%; }
.warn {
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--apricot-wash);
  color: var(--amber-ink);
  font-size: var(--fs-sm);
}
.composer .el-button { align-self: flex-start; }
.history { padding: 16px 16px 20px; }
.history-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 8px; }
.muted { color: var(--ink-3); font-size: var(--fs-sm); }
.rec-title { font-weight: 600; color: var(--ink); }
.rec-content { color: var(--ink-2); white-space: pre-line; }
.time { color: var(--ink-2); font-size: var(--fs-xs); }
@media (max-width: 1000px) { .layout { grid-template-columns: 1fr; } }
</style>
