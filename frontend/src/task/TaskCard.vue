<template>
  <router-link :to="`/tasks/${task.id}`" class="task-card">
    <div class="card-top">
      <span class="type-plate">{{ typeMeta.short }}</span>
      <span v-if="task.campus" class="campus">{{ task.campus }}</span>
      <span class="price" :class="{ 'is-free': priceTag.free }">
        <template v-if="priceTag.prefix">{{ priceTag.prefix }}</template>
        <template v-if="priceTag.amount"><span class="yen">¥</span><span class="num">{{ priceTag.amount }}</span></template>
        <template v-else>{{ priceTag.text }}</template>
      </span>
    </div>

    <h3 class="title">{{ task.title }}</h3>
    <p class="desc">{{ task.description }}</p>

    <p v-if="typeLine" class="type-line">{{ typeLine }}</p>
    <p v-if="task.status === 'rejected'" class="reject-line">驳回理由：{{ task.rejectReason || '管理员未填写' }}</p>

    <div class="card-foot">
      <span class="publisher">
        {{ task.publisher?.nickname || '匿名同学' }}
        <span v-if="task.publisher?.creditScore != null" class="credit num" title="发布者信用分">{{ task.publisher.creditScore }}</span>
      </span>
      <StatusTag v-if="task.status !== 'published'" :label="status.label" :tone="status.tone" />
      <span v-else-if="deadline.text" class="deadline" :class="{ urgent: deadline.urgent }">{{ deadline.text }}</span>
    </div>
  </router-link>
</template>

<script setup>
import { computed } from 'vue'
import StatusTag from '@/common/ui/StatusTag.vue'
import { TASK_STATUS, TASK_TYPE, lookup } from '@/common/labels'
import { deadlineInfo, formatMoney } from '@/common/format'

/**
 * 需求卡片：广场、我的需求共用。
 * 待接单的卡片显示截止提示，其余状态显示状态标签；类型专属的关键信息压成一行，
 * 让同学不点进详情也能判断能不能接（取送地点、科目时长、组队人数等）。
 */
const props = defineProps({ task: { type: Object, required: true } })

const typeMeta = computed(() => TASK_TYPE[props.task.taskType] || { short: props.task.categoryName || '需求' })
const status = computed(() => lookup(TASK_STATUS, props.task.status))
const deadline = computed(() => deadlineInfo(props.task.deadline))

/** 价格牌：组队不涉及金额；二手求购显示预算；报酬为 0 显示免费。 */
const priceTag = computed(() => {
  const t = props.task
  if (t.taskType === 'TEAM') return { text: t.maxMembers ? `限 ${t.maxMembers} 人` : '组队', free: true }
  const amount = formatMoney(t.taskType === 'FLEA' ? (t.itemPrice ?? t.reward) : t.reward)
  if (!amount || Number(amount) === 0) return { text: '免费', free: true }
  return { prefix: t.taskType === 'FLEA' && t.tradeMode === 'BUY' ? '预算 ' : '', amount }
})

/** 类型专属一行摘要。 */
const typeLine = computed(() => {
  const t = props.task
  switch (t.taskType) {
    case 'RUNNING':
      return t.pickupLocation && t.deliveryLocation ? `${t.pickupLocation} 送到 ${t.deliveryLocation}` : ''
    case 'TUTORING': {
      const parts = [t.subject, t.durationHours ? `${formatMoney(t.durationHours)} 小时` : '', t.pricePerHour ? `每小时 ¥${formatMoney(t.pricePerHour)}` : '']
      return parts.filter(Boolean).join('，')
    }
    case 'FLEA':
      return t.tradeMode === 'BUY' ? '求购：接单人卖给发布者' : '出售：接单人向发布者购买'
    case 'CONSULTING':
      return t.consultingMode === 'OFFLINE' ? `线下咨询${t.location ? `，${t.location}` : ''}` : '线上咨询'
    case 'TEAM':
      return t.location ? `活动地点：${t.location}` : ''
    default:
      return t.location || ''
  }
})
</script>

<style scoped>
.task-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 16px 18px 14px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-card);
  color: inherit;
  text-decoration: none;
  transition: border-color var(--transition-fast), box-shadow var(--transition-fast);
}
.task-card:hover {
  border-color: var(--mint-line);
  box-shadow: 0 2px 10px rgba(15, 91, 70, 0.08);
}

.card-top {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--fs-xs);
}
/* 类型牌：小号导视牌 */
.type-plate {
  padding: 1px 7px;
  background: var(--pine);
  color: #fff;
  border-radius: var(--r-sign);
  font-weight: 600;
  letter-spacing: 0.05em;
}
.campus { color: var(--ink-2); }

/* 价格牌：杏黄底，金额用 DIN 数字 */
.price {
  margin-left: auto;
  padding: 1px 8px;
  border-radius: var(--r-sign);
  background: var(--apricot-wash);
  color: var(--amber-ink);
  font-weight: 600;
  font-size: var(--fs-sm);
  white-space: nowrap;
}
.price .yen { font-size: var(--fs-xs); margin-right: 1px; }
.price .num { font-size: 17px; }
.price.is-free { background: #eef2f0; color: var(--ink-2); font-size: var(--fs-xs); }

.title {
  font-size: var(--fs-lg);
  font-weight: 600;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.desc {
  color: var(--ink-2);
  font-size: var(--fs-sm);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.type-line {
  font-size: var(--fs-sm);
  color: var(--pine);
  padding-left: 8px;
  border-left: 2px solid var(--mint-line);
}

/* 被驳回理由：只有发布者本人能拿到 rejectReason（API 契约 3.3） */
.reject-line {
  font-size: var(--fs-sm);
  color: var(--danger);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-foot {
  margin-top: auto;
  padding-top: 10px;
  border-top: 1px solid #edf1ef;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: var(--fs-xs);
  color: var(--ink-2);
}
.publisher {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.credit {
  padding: 0 5px;
  border: 1px solid var(--line);
  border-radius: var(--r-sign);
  color: var(--ink-2);
}
.deadline { white-space: nowrap; }
.deadline.urgent { color: var(--amber-ink); font-weight: 600; }
</style>
