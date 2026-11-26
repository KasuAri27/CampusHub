<template>
  <div class="dashboard" v-loading="loading">
    <PageHeader title="概览" description="先处理待办，再看平台数据。" />

    <!-- 待办：每一项都直接通往处理页面 -->
    <section class="todo" aria-label="待处理事项">
      <router-link
        v-for="item in todos"
        :key="item.key"
        :to="item.to"
        class="todo-item"
        :class="{ hot: item.count > 0 }"
      >
        <span class="todo-count num">{{ item.count }}</span>
        <span class="todo-label">{{ item.label }}</span>
        <span class="todo-note">{{ item.note }}</span>
      </router-link>
    </section>
    <p class="todo-foot">争议、逾期与结算按最近 50 笔订单统计，完整列表见“交易与结算”。</p>

    <!-- 审核队列：一次处理一条 -->
    <section class="queue panel" aria-label="待审核需求">
      <header class="queue-head">
        <h2>审核队列</h2>
        <span v-if="pendingTasks.length" class="queue-pos num">{{ currentIndex + 1 }} / {{ pendingTasks.length }}</span>
        <button v-if="pendingTasks.length > 1" type="button" class="skip" @click="skip">先看下一条</button>
      </header>

      <div v-if="currentTask" :key="currentTask.id" class="review">
        <div class="review-tags">
          <span class="type-plate">{{ typeName(currentTask) }}</span>
          <span v-if="TASK_TYPE[currentTask.taskType]" class="muted">{{ currentTask.categoryName }}</span>
        </div>
        <h3>{{ currentTask.title }}</h3>
        <p class="review-desc">{{ currentTask.description }}</p>
        <dl class="review-facts">
          <dt>发布者</dt><dd>{{ currentTask.requesterName || '—' }}<span v-if="currentTask.requesterEmail" class="muted">（{{ currentTask.requesterEmail }}）</span></dd>
          <dt>校区与地点</dt><dd>{{ [currentTask.campus, currentTask.location].filter(Boolean).join('，') || '未填写' }}</dd>
          <dt>金额</dt><dd>{{ currentTask.taskType === 'TEAM' ? '组队，不涉及金额' : (Number(currentTask.reward) > 0 ? `¥${formatMoney(currentTask.reward)}` : '免费') }}</dd>
          <dt>截止时间</dt><dd>{{ formatDateTime(currentTask.deadline) || '—' }}</dd>
          <dt>提交时间</dt><dd>{{ formatDateTime(currentTask.publishedAt) }}</dd>
        </dl>
        <div class="review-actions">
          <el-button type="primary" :loading="actionLoading" @click="approve">通过，发布到广场</el-button>
          <el-button type="danger" plain :disabled="actionLoading" @click="openReject">驳回</el-button>
          <router-link :to="`/tasks/${currentTask.id}`" class="detail-link">查看类型详情</router-link>
        </div>
      </div>
      <EmptyState v-else title="没有待审核的需求" description="新提交的需求会进入这里，审核通过后才会出现在需求广场。" />
    </section>

    <!-- 平台数据 -->
    <section class="stats" aria-label="平台数据">
      <div v-for="group in statGroups" :key="group.title" class="stat-group panel">
        <h2>{{ group.title }}</h2>
        <div class="stat-row">
          <div v-for="s in group.items" :key="s.label" class="stat">
            <span class="stat-value num">{{ s.value }}</span>
            <span class="stat-label">{{ s.label }}</span>
          </div>
        </div>
      </div>
    </section>

    <el-dialog v-model="rejectDialog.visible" title="驳回需求" width="460px">
      <p class="dialog-sub">驳回「{{ currentTask?.title }}」后，理由会通过系统通知发给发布者，发布者可以修改后重新提交审核。</p>
      <el-input v-model.trim="rejectDialog.reason" type="textarea" :rows="4" maxlength="200" show-word-limit placeholder="说明需要修改的地方，例如：描述涉及代写作业，不符合平台规则" />
      <div class="quick-reasons">
        <button v-for="r in quickReasons" :key="r" type="button" @click="rejectDialog.reason = r">{{ r }}</button>
      </div>
      <template #footer>
        <el-button @click="rejectDialog.visible = false">取消</el-button>
        <el-button type="danger" :loading="rejectDialog.submitting" @click="confirmReject">驳回需求</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import adminApi from '@/admin/adminApi'
import http from '@/common/http'
import PageHeader from '@/common/ui/PageHeader.vue'
import EmptyState from '@/common/ui/EmptyState.vue'
import { TASK_TYPE } from '@/common/labels'
import { formatDateTime, formatMoney } from '@/common/format'

/**
 * 管理概览。
 * 待办：待审核数取自需求列表分页总数；争议、逾期、待结算取自交易接口最近 50 笔订单（该接口不返回总数）。
 * 审核队列一次展示一条，通过或驳回后通知外层 AdminLayout 刷新侧栏待审核计数。
 */
const emit = defineEmits(['pending-changed'])

const loading = ref(false)
const actionLoading = ref(false)
const stats = ref({})
const pendingTasks = ref([])
const pendingTotal = ref(0)
const recentOrders = ref([])
const currentIndex = ref(0)
const rejectDialog = reactive({ visible: false, reason: '', submitting: false })
const quickReasons = ['描述不清楚，请补充时间、地点和具体要求', '内容涉及代写作业或考试作弊', '金额明显不合理', '重复发布']

const currentTask = computed(() => pendingTasks.value[currentIndex.value] || null)

const todos = computed(() => {
  const o = recentOrders.value
  return [
    { key: 'review', label: '待审核需求', count: pendingTotal.value, note: '审核后才会公开', to: '/admin/tasks?status=pending_review' },
    { key: 'dispute', label: '争议待裁决', count: o.filter(x => x.disputed).length, note: '计时已暂停', to: '/admin/finance?view=dispute' },
    { key: 'overdue', label: '逾期待处置', count: o.filter(x => x.escalatedAt && x.paymentStatus === 'unpaid' && !x.disputed).length, note: '超过付款期限 24 小时', to: '/admin/finance?view=overdue' },
    { key: 'settle', label: '待登记结算', count: o.filter(x => x.paymentStatus === 'paid' && x.settlementStatus !== 'recorded' && !x.disputed).length, note: '转账后登记凭证', to: '/admin/finance?view=settle' }
  ]
})

/** totalOrders 字段在后端实际统计的是已取消需求数（AdminService.getStats），这里按含义展示。 */
const statGroups = computed(() => {
  const s = stats.value
  return [
    { title: '用户', items: [{ label: '注册用户', value: s.totalUsers ?? 0 }, { label: '已禁用', value: s.bannedUsers ?? 0 }] },
    {
      title: '需求',
      items: [
        { label: '全部需求', value: s.totalTasks ?? 0 },
        { label: '今日新增', value: s.todayNewTasks ?? 0 },
        { label: '待接单', value: s.publishedTasks ?? 0 },
        { label: '进行中', value: s.inProgressTasks ?? 0 },
        { label: '已完成', value: s.completedTasks ?? 0 },
        { label: '已取消', value: s.totalOrders ?? 0 }
      ]
    },
    { title: '消息', items: [{ label: '会话', value: s.totalConversations ?? 0 }, { label: '消息总数', value: s.totalMessages ?? 0 }, { label: '今日消息', value: s.todaysMessages ?? 0 }] }
  ]
})

/** 管理端 taskType 只区分 TEAM / FLEA / SERVICE，SERVICE 用分类名展示（跑腿、辅导、咨询）。 */
function typeName(task) { return TASK_TYPE[task.taskType]?.name || task.categoryName || '需求' }

async function load() {
  loading.value = true
  const [statsRes, tasksRes, ordersRes] = await Promise.allSettled([
    adminApi.getStats(),
    adminApi.listTasks({ status: 'pending_review', page: 1, page_size: 50 }),
    http.get('/admin/finance/orders', { params: { page: 1 } })
  ])
  if (statsRes.status === 'fulfilled') stats.value = statsRes.value.data?.data || {}
  if (tasksRes.status === 'fulfilled') {
    pendingTasks.value = tasksRes.value.data?.data?.items || []
    pendingTotal.value = tasksRes.value.data?.data?.pagination?.total || pendingTasks.value.length
  }
  if (ordersRes.status === 'fulfilled') recentOrders.value = ordersRes.value.data?.data || []
  currentIndex.value = 0
  loading.value = false
}

function skip() {
  currentIndex.value = (currentIndex.value + 1) % pendingTasks.value.length
}

/** 处理完一条后从队列移除，并刷新统计与侧栏计数。 */
function removeCurrent() {
  pendingTasks.value.splice(currentIndex.value, 1)
  pendingTotal.value = Math.max(0, pendingTotal.value - 1)
  if (currentIndex.value >= pendingTasks.value.length) currentIndex.value = 0
  emit('pending-changed')
  adminApi.getStats().then(res => { stats.value = res.data?.data || stats.value }).catch(() => {})
}

async function approve() {
  if (!currentTask.value) return
  actionLoading.value = true
  try {
    await adminApi.approveTask(currentTask.value.id)
    ElMessage.success('已通过，需求已发布到广场')
    removeCurrent()
  } catch {
    // 拦截器已提示
  } finally {
    actionLoading.value = false
  }
}

function openReject() {
  rejectDialog.reason = ''
  rejectDialog.visible = true
}

async function confirmReject() {
  if (!currentTask.value) return
  rejectDialog.submitting = true
  try {
    await adminApi.rejectTask(currentTask.value.id, rejectDialog.reason || null)
    rejectDialog.visible = false
    ElMessage.success('已驳回，并通知了发布者')
    removeCurrent()
  } catch {
    // 拦截器已提示，保留理由便于重试
  } finally {
    rejectDialog.submitting = false
  }
}

onMounted(load)
</script>

<style scoped>
.todo {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.todo-item {
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto auto;
  column-gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  background: var(--surface);
  color: inherit;
  text-decoration: none;
}
.todo-item:hover { border-color: var(--mint-line); }
.todo-count {
  grid-row: span 2;
  font-size: 30px;
  font-weight: 600;
  line-height: 1;
  color: var(--ink-3);
}
.todo-label { font-weight: 600; }
.todo-note { color: var(--ink-3); font-size: var(--fs-xs); }
/* 有待办时整块变成杏黄提示牌 */
.todo-item.hot { background: var(--apricot-wash); border-color: #efd9a4; }
.todo-item.hot .todo-count { color: var(--amber-ink); }
.todo-foot { margin: 8px 0 24px; color: var(--ink-3); font-size: var(--fs-xs); }

.queue { padding: 20px 22px 22px; }
.queue-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; }
.queue-head h2 { font-size: var(--fs-lg); }
.queue-pos { color: var(--ink-2); font-size: var(--fs-sm); }
.skip {
  margin-left: auto;
  padding: 0;
  border: 0;
  background: none;
  color: var(--mint);
  font: inherit;
  font-size: var(--fs-sm);
  cursor: pointer;
}
.review-tags { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.type-plate {
  padding: 1px 8px;
  background: var(--pine);
  color: #fff;
  border-radius: var(--r-sign);
  font-size: var(--fs-xs);
  font-weight: 600;
}
.muted { color: var(--ink-3); font-size: var(--fs-sm); }
.review h3 { font-size: var(--fs-xl); }
.review-desc { margin-top: 8px; max-width: 70ch; white-space: pre-wrap; color: var(--ink); }
.review-facts {
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 6px 12px;
  margin: 16px 0 0;
  padding: 14px 16px;
  background: #f7f9f8;
  border-radius: 8px;
  font-size: var(--fs-sm);
}
.review-facts dt { color: var(--ink-2); }
.review-facts dd { margin: 0; }
.review-actions { display: flex; align-items: center; gap: 10px; margin-top: 18px; }
.detail-link { margin-left: auto; color: var(--mint); font-size: var(--fs-sm); }

.stats {
  display: grid;
  grid-template-columns: 1fr 2fr 1.4fr;
  gap: 12px;
  margin-top: 24px;
}
.stat-group { padding: 16px 18px; }
.stat-group h2 { font-size: var(--fs-md); color: var(--ink-2); font-weight: 600; margin-bottom: 10px; }
.stat-row { display: flex; flex-wrap: wrap; gap: 12px 24px; }
.stat { display: flex; flex-direction: column; }
.stat-value { font-size: 24px; font-weight: 600; line-height: 1.2; }
.stat-label { color: var(--ink-3); font-size: var(--fs-xs); }

.dialog-sub { color: var(--ink-2); margin-bottom: 10px; }
.quick-reasons { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
.quick-reasons button {
  padding: 3px 10px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-pill);
  background: var(--surface);
  color: var(--ink-2);
  font: inherit;
  font-size: var(--fs-xs);
  cursor: pointer;
}
.quick-reasons button:hover { border-color: var(--danger); color: var(--danger); }

@media (max-width: 1100px) {
  .todo { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .stats { grid-template-columns: 1fr; }
}
</style>
