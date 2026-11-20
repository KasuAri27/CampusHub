<template>
  <section class="order-actions" :class="{ compact }" v-loading="busy">
    <OrderProgress v-if="steps.length" :steps="steps" :vertical="compact" />

    <p class="hint" :class="{ need: hint.need }">{{ hint.text }}</p>

    <p v-if="review" class="review-line">
      <template v-if="review.automatic">已按默认四星评价</template>
      <template v-else>你的评价：<span class="num">{{ review.score }}</span> 星<span v-if="review.comment">，{{ review.comment }}</span></template>
    </p>

    <div v-if="hasButtons" class="buttons">
      <el-button v-if="can.submit" type="primary" @click="act('submit')">提交完成</el-button>
      <el-button v-if="can.accept" type="primary" @click="act('accept')">确认验收</el-button>
      <el-button v-if="can.pay" type="primary" @click="paymentVisible = true">去付款</el-button>
      <el-button v-if="can.review" @click="reviewVisible = true">评价服务方</el-button>
      <el-button v-if="can.cancel" @click="act('cancel')">取消订单</el-button>
      <el-button v-if="can.complain" link type="danger" class="complain-btn" @click="openComplaint">提出异议或投诉</el-button>
    </div>

    <PaymentDialog v-model:visible="paymentVisible" :order="order" @paid="emit('updated')" />
    <ReviewDialog v-model:visible="reviewVisible" :order-id="order.orderId" @submitted="reviewed" />

    <el-dialog v-model="disputeVisible" title="提出异议或投诉" width="460px" append-to-body>
      <el-form label-position="top" @submit.prevent>
        <el-form-item label="原因" required>
          <el-radio-group v-model="reason" class="reasons">
            <el-radio-button v-for="item in reasons" :key="item" :value="item">{{ item }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="具体情况" required>
          <el-input v-model="description" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="说明发生了什么，管理员会据此核查" />
        </el-form-item>
      </el-form>
      <p class="dialog-note">{{ disputeNote }}</p>
      <template #footer>
        <el-button @click="disputeVisible = false">取消</el-button>
        <el-button type="primary" :loading="busy" @click="complain">提交给管理员</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/user/userStore'
import http from '@/common/http'
import orderApi from '@/order/orderApi'
import reviewApi from '@/review/reviewApi'
import PaymentDialog from '@/order/PaymentDialog.vue'
import ReviewDialog from '@/review/reviewDialog.vue'
import OrderProgress from '@/order/OrderProgress.vue'
import { canReviewOrder } from '@/order/orderPolicy'
import { orderSteps, nextHint, orderRole } from '@/order/orderFlow'

/**
 * 订单操作区：步骤条 + 下一步提示 + 操作按钮，订单页与需求详情页共用（compact 为详情页右栏竖排）。
 * 操作资格按订单 payerId / payeeId 判断，不依赖页面角色；受限账号只保留付款与投诉。
 */
const props = defineProps({
  order: { type: Object, required: true },
  compact: { type: Boolean, default: false }
})
const emit = defineEmits(['updated'])
const user = useUserStore()

const busy = ref(false)
const paymentVisible = ref(false)
const reviewVisible = ref(false)
const disputeVisible = ref(false)
const reason = ref('')
const description = ref('')
const review = ref(null)
const reasons = ['服务未完成', '质量不符', '付款纠纷', '其他']

const steps = computed(() => orderSteps(props.order))
const hint = computed(() => nextHint(props.order, user.userId))
const role = computed(() => orderRole(props.order, user.userId).key)

const can = computed(() => {
  const o = props.order
  const free = !user.restricted && !o.disputed
  return {
    submit: free && role.value === 'payee' && o.orderStatus === 'in_progress',
    accept: free && role.value === 'payer' && o.orderStatus === 'awaiting_acceptance',
    pay: !o.disputed && role.value === 'payer' && o.orderStatus === 'completed' && o.paymentStatus === 'unpaid',
    review: !user.restricted && !review.value && canReviewOrder(o, user.userId),
    cancel: free && o.orderStatus === 'in_progress',
    complain: o.orderStatus !== 'cancelled'
  }
})
const hasButtons = computed(() => Object.values(can.value).some(Boolean))

const disputeNote = computed(() => role.value === 'payer'
  ? '以“服务未完成”或“质量不符”提出异议后，自动验收、扣分和升级计时会暂停，由管理员裁决付款义务。'
  : '服务方的催款投诉会转给管理员处理，不会暂停对方的付款期限与逾期处理。')

async function act(action) {
  const confirmText = {
    submit: ['确认已经完成服务？提交后等待对方验收。', '提交完成'],
    accept: ['确认对方已完成服务？验收后进入付款环节，无法撤销。', '确认验收'],
    cancel: ['确定取消这笔订单？', '取消订单']
  }[action]
  try {
    await ElMessageBox.confirm(confirmText[0], confirmText[1], { confirmButtonText: confirmText[1], cancelButtonText: '再想想' })
  } catch { return }
  busy.value = true
  try {
    if (action === 'submit') await orderApi.submitCompletion(props.order.orderId)
    else if (action === 'accept') await orderApi.complete(props.order.orderId)
    else await orderApi.cancel(props.order.orderId)
    ElMessage.success({ submit: '已提交完成，等待对方验收', accept: '已确认验收', cancel: '订单已取消' }[action])
    emit('updated')
  } catch {
    // 拦截器已提示失败，保留原订单状态
  } finally {
    busy.value = false
  }
}

async function loadReview() {
  review.value = null
  if (!props.order.reviewEligibleAt || !props.order.hasReview) return
  try { review.value = (await reviewApi.getByOrderId(props.order.orderId)).data.data }
  catch { review.value = null }
}
async function reviewed() { await loadReview(); emit('updated') }

function openComplaint() {
  reason.value = role.value === 'payee' ? '付款纠纷' : ''
  description.value = ''
  disputeVisible.value = true
}

async function complain() {
  if (!reason.value || !description.value.trim()) { ElMessage.warning('请选择原因并说明具体情况'); return }
  busy.value = true
  try {
    await http.post('/complaints', { orderId: props.order.orderId, reason: reason.value, description: description.value.trim() })
    disputeVisible.value = false
    ElMessage.success('已提交给管理员，处理结果会通过系统通知告诉你')
    emit('updated')
  } catch {
    // 保留填写内容，网络恢复后可直接重试
  } finally {
    busy.value = false
  }
}

watch(() => [props.order.orderId, props.order.reviewEligibleAt, props.order.hasReview], loadReview, { immediate: true })
</script>

<style scoped>
.order-actions { display: flex; flex-direction: column; gap: 14px; }
.hint {
  font-size: var(--fs-sm);
  color: var(--ink-2);
  line-height: 1.6;
}
/* 轮到我操作：杏黄提示条 */
.hint.need {
  padding: 8px 12px;
  border-left: 3px solid var(--apricot);
  background: var(--apricot-wash);
  color: var(--amber-ink);
  font-weight: 500;
}
.review-line { font-size: var(--fs-sm); color: var(--ink-2); }
.buttons { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.buttons .el-button + .el-button { margin-left: 0; }
.complain-btn { margin-left: auto !important; }
.compact .buttons { flex-direction: column; align-items: stretch; }
.compact .complain-btn { margin-left: 0 !important; align-self: flex-start; }
.reasons { flex-wrap: wrap; }
.dialog-note { color: var(--ink-2); font-size: var(--fs-sm); }
</style>
