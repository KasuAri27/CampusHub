<template>
  <div class="detail page" v-loading="loading">
    <button class="back-link" type="button" @click="goBack">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M15 18l-6-6 6-6"/></svg>
      返回
    </button>

    <EmptyState v-if="!loading && !task" title="找不到这条需求" description="它可能已被发布者撤回或被管理员删除。">
      <el-button @click="$router.push('/')">回到需求广场</el-button>
    </EmptyState>

    <div v-if="task" class="layout">
      <article class="main">
        <div class="headline">
          <div class="tags">
            <span class="type-plate">{{ typeMeta.name }}</span>
            <StatusTag :label="statusMeta.label" :tone="statusMeta.tone" />
          </div>
          <h1>{{ task.title }}</h1>
          <p class="sub">
            <span>{{ task.publisher?.nickname || '匿名同学' }} 发布于 {{ relativeTime(task.publishedAt) }}</span>
            <span class="num">{{ task.viewCount || 0 }} 次浏览</span>
          </p>
        </div>

        <section class="block">
          <h2>需求说明</h2>
          <p class="description">{{ task.description }}</p>
        </section>

        <section class="block">
          <h2>{{ typeMeta.name }}信息</h2>
          <dl class="facts">
            <template v-for="f in facts" :key="f.label">
              <dt>{{ f.label }}</dt>
              <dd :class="{ num: f.num }">{{ f.value }}</dd>
            </template>
          </dl>
        </section>

        <section v-if="reviewDetail" class="block">
          <h2>服务评价</h2>
          <div v-if="reviewDetail.automatic" class="review auto">验收后 7 天内未评价，系统已按默认四星记录。</div>
          <div v-else class="review">
            <el-rate :model-value="reviewDetail.score" disabled />
            <p v-if="reviewDetail.comment" class="review-text">{{ reviewDetail.comment }}</p>
            <p class="review-time">{{ formatDateTime(reviewDetail.createdAt) }}</p>
          </div>
        </section>
      </article>

      <aside class="side">
        <div class="panel offer">
          <p class="offer-label">{{ priceLabel }}</p>
          <p class="offer-price">
            <template v-if="priceAmount"><span class="yen">¥</span><span class="num">{{ priceAmount }}</span></template>
            <template v-else>{{ priceText }}</template>
          </p>
          <p class="offer-deadline" :class="{ urgent: deadline.urgent, expired: deadline.expired }">
            {{ task.deadline ? `截止 ${formatDateTime(task.deadline)}` : '未设置截止时间' }}
            <span v-if="deadline.text && task.status === 'published'">（{{ deadline.text }}）</span>
          </p>

          <div class="offer-actions">
            <template v-if="!userStore.isLoggedIn && task.status === 'published'">
              <el-button type="primary" size="large" class="wide" @click="$router.push({ path: '/login', query: { redirect: $route.fullPath } })">登录后接单</el-button>
            </template>
            <template v-else-if="acceptState.show">
              <el-button type="primary" size="large" class="wide" :disabled="!acceptState.allowed" :loading="actionLoading" @click="handleAccept">
                {{ task.taskType === 'TEAM' ? '加入组队' : '接下这单' }}
              </el-button>
              <p class="hint" :class="{ blocked: !acceptState.allowed }">{{ acceptState.hint }}</p>
            </template>
            <template v-if="isOwner && task.status === 'rejected'">
              <div class="reject-box" role="status">
                <strong>需求被驳回</strong>
                <p>{{ task.rejectReason || '管理员没有填写具体理由。' }}</p>
              </div>
              <el-button type="primary" size="large" class="wide" @click="$router.push(`/tasks/${task.id}/edit`)">修改后重新提交</el-button>
              <el-button size="large" class="wide" :loading="actionLoading" @click="handleWithdraw">删除需求</el-button>
            </template>
            <template v-if="isOwner && task.status === 'published' && !orderDetail">
              <el-button size="large" class="wide" :loading="actionLoading" @click="handleWithdraw">撤回需求</el-button>
            </template>
            <template v-if="isOwner && task.status === 'pending_review'">
              <p class="hint">需求正在等待管理员审核，通过后会出现在需求广场。审核前仍可修改。</p>
              <el-button size="large" class="wide" @click="$router.push(`/tasks/${task.id}/edit`)">修改内容</el-button>
            </template>
            <p v-if="userStore.isAdmin" class="hint">管理员只负责审核与监管，不参与接单。<router-link v-if="task.status === 'pending_review'" to="/admin/tasks?status=pending_review">去审核</router-link></p>
            <router-link v-if="task.taskType === 'TEAM' && task.chatGroupId && (isOwner || orderDetail)" :to="`/chat/${task.chatGroupId}`" class="text-link">进入组队群聊</router-link>
            <router-link v-if="task.taskType === 'TEAM' && isOwner" to="/my-orders" class="text-link">查看成员订单</router-link>
          </div>
        </div>

        <div v-if="orderDetail" class="panel order-panel">
          <h2>我的订单</h2>
          <OrderActions :order="orderDetail" compact @updated="refreshOrder" />
        </div>

        <div v-if="task.publisher" class="panel publisher">
          <span class="avatar">
            <img v-if="publisherAvatar" :src="publisherAvatar" alt="" />
            <span v-else>{{ task.publisher.nickname?.charAt(0) || '同' }}</span>
          </span>
          <div>
            <p class="pub-name">{{ task.publisher.nickname }}</p>
            <p class="pub-credit">信用分 <span class="num">{{ task.publisher.creditScore ?? '—' }}</span></p>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import taskApi from '@/task/taskApi'
import orderApi from '@/order/orderApi'
import reviewApi from '@/review/reviewApi'
import OrderActions from '@/order/OrderActions.vue'
import StatusTag from '@/common/ui/StatusTag.vue'
import EmptyState from '@/common/ui/EmptyState.vue'
import { useUserStore } from '@/user/userStore'
import { TASK_STATUS, TASK_TYPE, lookup } from '@/common/labels'
import { deadlineInfo, formatDateTime, formatMoney, relativeTime } from '@/common/format'
import { assetUrl } from '@/common/assetUrl'

/**
 * 需求详情。
 * 右栏只放与“我”相关的操作：接单/加入组队、撤回、本人订单的履约操作；
 * 所有按钮条件只是提示，信用分、身份与状态最终由后端接单与订单接口再次校验。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const actionLoading = ref(false)
const task = ref(null)
const orderDetail = ref(null)
const reviewDetail = ref(null)

const typeMeta = computed(() => TASK_TYPE[task.value?.taskType] || { name: task.value?.categoryName || '需求' })
const statusMeta = computed(() => lookup(TASK_STATUS, task.value?.status))
const deadline = computed(() => deadlineInfo(task.value?.deadline))
const isOwner = computed(() => userStore.isLoggedIn && Number(task.value?.publisher?.userId) === Number(userStore.userId))
const publisherAvatar = computed(() => assetUrl(task.value?.publisher?.avatar))

/** 价格区：组队不涉及金额；二手区分出售价与求购预算；咨询可为免费。 */
const priceLabel = computed(() => {
  const t = task.value
  if (t.taskType === 'TEAM') return '组队人数'
  if (t.taskType === 'FLEA') return t.tradeMode === 'BUY' ? '求购预算' : '出售价格'
  if (t.taskType === 'TUTORING') return '辅导总价'
  return '报酬'
})
const priceAmount = computed(() => {
  const t = task.value
  if (t.taskType === 'TEAM') return ''
  const v = formatMoney(t.taskType === 'FLEA' ? (t.itemPrice ?? t.reward) : t.reward)
  return v && Number(v) > 0 ? v : ''
})
const priceText = computed(() => (task.value.taskType === 'TEAM' ? `最多 ${task.value.maxMembers || '—'} 人` : '免费互助'))

/** 类型专属字段，按后端 TaskDTO 子类字段展示；取件码只给发布者和接单人看。 */
const facts = computed(() => {
  const t = task.value
  const list = [{ label: '校区', value: t.campus || '未填写' }]
  const add = (label, value, num = false) => { if (value !== null && value !== undefined && value !== '') list.push({ label, value, num }) }
  switch (t.taskType) {
    case 'RUNNING':
      add('取件地点', t.pickupLocation)
      add('送达地点', t.deliveryLocation)
      if (isOwner.value || orderDetail.value) add('取件码 / 备注', t.pickupCode)
      else if (t.pickupCode) add('取件码 / 备注', '接单后可见')
      break
    case 'TUTORING':
      add('科目', t.subject)
      add('每小时报酬', t.pricePerHour != null ? `¥${formatMoney(t.pricePerHour)}` : '')
      add('约定时长', t.durationHours != null ? `${formatMoney(t.durationHours)} 小时` : '')
      add('辅导地点', t.tutoringLocation)
      break
    case 'FLEA':
      add('交易方式', t.tradeMode === 'BUY' ? '求购：接单人把物品卖给发布者' : '出售：接单人向发布者购买')
      add('付款方向', t.tradeMode === 'BUY' ? '发布者验收后付款给接单人' : '接单人验收后付款给发布者')
      break
    case 'TEAM':
      add('人数上限', t.maxMembers ? `${t.maxMembers} 人（含发起人）` : '')
      add('活动地点', t.location)
      break
    case 'CONSULTING':
      add('咨询方式', t.consultingMode === 'OFFLINE' ? '线下' : '线上')
      if (t.consultingMode === 'OFFLINE') add('地点', t.location)
      break
    default:
      add('地点', t.location)
  }
  add('截止时间', formatDateTime(t.deadline))
  add('发布时间', formatDateTime(t.publishedAt))
  return list
})

/**
 * 接单按钮状态：普通用户、未受限、需求待接单、不是本人发布、本人在该需求上没有未取消订单时显示；
 * 信用分低于 70 时按钮置灰并说明原因（后端同样拒绝）。
 */
const acceptState = computed(() => {
  const t = task.value
  const hidden = { show: false }
  if (!t || !userStore.isLoggedIn || !userStore.isUser || userStore.restricted) return hidden
  if (t.status !== 'published' || isOwner.value) return hidden
  if (orderDetail.value && orderDetail.value.orderStatus !== 'cancelled') return hidden
  if (userStore.creditScore < 70) {
    return { show: true, allowed: false, hint: `你的信用分为 ${userStore.creditScore}，低于 70 分暂时不能接单。信用分每日零点逐步恢复。` }
  }
  return {
    show: true,
    allowed: true,
    hint: t.taskType === 'TEAM' ? '加入后进入组队群聊，满员后需求自动进入进行中。' : '接单后需求进入进行中，完成服务后在订单里提交完成。'
  }
})

function goBack() {
  if (window.history.state?.back) router.back()
  else router.push('/')
}

async function loadTask() {
  try {
    task.value = (await taskApi.getById(route.params.id)).data.data
  } catch {
    task.value = null
  }
}

/** 从本人订单列表定位记录，不对无关用户发起订单详情请求。 */
async function refreshOrder() {
  if (!userStore.isLoggedIn || userStore.isAdmin) return
  try {
    const { data } = await orderApi.mine()
    const mine = (data.data || []).filter(o => o.taskId === Number(route.params.id))
    // 组队需求的发起人可能对应多张成员订单，此处优先展示未取消的那一张
    orderDetail.value = mine.find(o => o.orderStatus !== 'cancelled') || mine[0] || null
  } catch {
    orderDetail.value = null
  }
  reviewDetail.value = null
  const o = orderDetail.value
  if (o) {
    if (task.value && o.taskStatus) task.value.status = o.taskStatus
    if (o.reviewEligibleAt && o.hasReview) {
      try { reviewDetail.value = (await reviewApi.getByOrderId(o.orderId)).data.data } catch { reviewDetail.value = null }
    }
  }
}

async function handleAccept() {
  actionLoading.value = true
  try {
    const res = await orderApi.accept(task.value.id)
    ElMessage.success(task.value.taskType === 'TEAM' ? '已加入组队' : '已接单，记得按约定完成服务')
    if (res.data?.data?.taskStatus) task.value.status = res.data.data.taskStatus
    await Promise.all([loadTask(), refreshOrder()])
  } catch {
    // 拦截器已提示（例如信用分不足、需求已被他人接走）
  } finally {
    actionLoading.value = false
  }
}

/** 撤回需求：后端在已有订单历史时改为取消以保留审计，否则物理删除。 */
async function handleWithdraw() {
  try {
    const rejected = task.value.status === 'rejected'
    await ElMessageBox.confirm(
      rejected ? '删除后这条需求无法恢复。' : '撤回后需求将从广场下架，无法恢复。',
      rejected ? '删除需求' : '撤回需求',
      { type: 'warning', confirmButtonText: rejected ? '删除需求' : '撤回需求', cancelButtonText: '保留' }
    )
  } catch { return }
  actionLoading.value = true
  try {
    await taskApi.deleteTask(task.value.id)
    ElMessage.success('需求已撤回')
    router.push('/my-tasks')
  } catch {
    // 拦截器已提示
  } finally {
    actionLoading.value = false
  }
}

onMounted(async () => {
  await loadTask()
  if (task.value) await refreshOrder()
  loading.value = false
})
</script>

<style scoped>
.detail { min-height: 300px; }
.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 28px;
  margin-top: 12px;
  align-items: start;
}

.headline { padding-bottom: 20px; border-bottom: 1px solid var(--line); }
.tags { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; }
.type-plate {
  padding: 2px 10px;
  background: var(--pine);
  color: #fff;
  border-radius: var(--r-sign);
  font-size: var(--fs-xs);
  font-weight: 600;
  letter-spacing: 0.05em;
  box-shadow: inset 0 -2px 0 var(--apricot);
}
.headline h1 { font-size: 28px; font-weight: 700; line-height: 1.35; }
.sub {
  display: flex;
  gap: 16px;
  margin-top: 10px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.block { padding: 22px 0; border-bottom: 1px solid var(--line); }
.block:last-child { border-bottom: 0; }
.block h2 { font-size: var(--fs-lg); font-weight: 600; margin-bottom: 12px; }
.description {
  max-width: 68ch;
  white-space: pre-wrap;
  font-size: 15px;
  line-height: 1.8;
}

.facts {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  margin: 0;
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  background: var(--surface);
  overflow: hidden;
}
.facts dt, .facts dd {
  margin: 0;
  padding: 11px 16px;
  border-bottom: 1px solid #edf1ef;
}
.facts dt { color: var(--ink-2); background: #f7f9f8; font-size: var(--fs-sm); }
.facts dd { color: var(--ink); }
.facts dt:nth-last-of-type(1), .facts dd:last-of-type { border-bottom: 0; }

.review { display: flex; flex-direction: column; gap: 6px; }
.review.auto { color: var(--ink-2); font-size: var(--fs-sm); }
.review-text { max-width: 60ch; }
.review-time { color: var(--ink-3); font-size: var(--fs-xs); }

/* —— 右栏 —— */
.side { display: flex; flex-direction: column; gap: 16px; position: sticky; top: calc(var(--header-h) + 20px); }
.offer { padding: 20px; }
.offer-label { color: var(--ink-2); font-size: var(--fs-sm); }
.offer-price {
  margin-top: 2px;
  color: var(--amber-ink);
  font-size: var(--fs-xl);
  font-weight: 600;
}
.offer-price .yen { font-size: var(--fs-lg); margin-right: 2px; }
.offer-price .num { font-size: 40px; line-height: 1.1; }
.offer-deadline { margin-top: 8px; color: var(--ink-2); font-size: var(--fs-sm); }
.offer-deadline.urgent { color: var(--amber-ink); font-weight: 600; }
.offer-deadline.expired { color: var(--danger); }
.offer-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--line);
}
.offer-actions:empty { display: none; }
.wide { width: 100%; margin: 0 !important; }
.hint { color: var(--ink-2); font-size: var(--fs-sm); }
.reject-box {
  padding: 10px 12px;
  border-left: 3px solid var(--danger);
  background: var(--danger-wash);
  border-radius: 0 6px 6px 0;
}
.reject-box strong { color: var(--danger); font-size: var(--fs-sm); }
.reject-box p { margin-top: 2px; white-space: pre-wrap; font-size: var(--fs-sm); }
.hint.blocked { color: var(--danger); }
.hint a { margin-left: 6px; }
.text-link { color: var(--mint); font-size: var(--fs-sm); font-weight: 500; }

.order-panel { padding: 18px 20px; }
.order-panel h2 { font-size: var(--fs-md); font-weight: 600; margin-bottom: 10px; }

.publisher { display: flex; align-items: center; gap: 12px; padding: 14px 20px; }
.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: var(--mint-wash);
  color: var(--pine);
  font-weight: 600;
  flex-shrink: 0;
}
.avatar img { width: 100%; height: 100%; object-fit: cover; }
.pub-name { font-weight: 600; }
.pub-credit { color: var(--ink-2); font-size: var(--fs-sm); }

@media (max-width: 900px) {
  .layout { grid-template-columns: 1fr; }
  .side { position: static; order: -1; }
}
</style>
