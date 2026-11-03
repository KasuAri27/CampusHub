<template>
  <section class="order-actions" v-loading="busy">
    <p><strong>{{ orderStatusLabel(order.orderStatus) }}</strong><span v-if="order.taskType !== 'TEAM'"> · 约定金额 ¥{{ order.amountDue ?? '待核对' }}</span></p>
    <p v-if="order.disputed">存在待处理异议，相关计时和自动处罚已暂停。</p>
    <p v-if="order.acceptanceDueAt && order.orderStatus === 'awaiting_acceptance'">自动验收时间：{{ format(order.acceptanceDueAt) }}</p>
    <p v-if="order.paymentDueAt && order.paymentStatus === 'unpaid'">付款期限：{{ format(order.paymentDueAt) }}</p>
    <p v-if="order.escalationDueAt && order.paymentStatus === 'unpaid'">管理员介入时间：{{ format(order.escalationDueAt) }}</p>
    <p v-if="order.paymentStatus === 'paid'">付款已确认 · {{ order.settlementStatus === 'recorded' ? '已登记人工结算' : '等待人工结算' }}</p>
    <p v-if="review">{{ review.automatic ? '系统默认四星' : `评价：${review.score}星` }} {{ review.comment }}</p>
    <div class="buttons">
      <el-button v-if="!user.restricted && order.payeeId === user.userId && order.orderStatus === 'in_progress' && !order.disputed" @click="act('submit')">提交服务完成</el-button>
      <el-button v-if="!user.restricted && order.payerId === user.userId && order.orderStatus === 'awaiting_acceptance' && !order.disputed" type="success" @click="act('accept')">确认验收</el-button>
      <el-button v-if="order.payerId === user.userId && order.orderStatus === 'completed' && order.paymentStatus === 'unpaid' && !order.disputed" type="primary" @click="paymentVisible = true">立即付款</el-button>
      <el-button v-if="!user.restricted && order.orderStatus === 'in_progress' && !order.disputed" @click="act('cancel')">取消订单</el-button>
      <el-button v-if="!user.restricted && canReview && !review" @click="reviewVisible = true">评价服务方</el-button>
      <el-button v-if="order.orderStatus !== 'cancelled'" @click="disputeVisible = true">异议或投诉</el-button>
    </div>
    <PaymentDialog v-model:visible="paymentVisible" :order="order" @paid="emit('updated')" />
    <ReviewDialog v-model:visible="reviewVisible" :order-id="order.orderId" @submitted="reviewed" />
    <el-dialog v-model="disputeVisible" title="异议或投诉" width="440px">
      <el-select v-model="reason" placeholder="选择理由">
        <el-option v-for="item in ['服务未完成', '质量不符', '付款纠纷', '其他']" :key="item" :label="item" :value="item" />
      </el-select>
      <el-input v-model="description" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="说明具体问题，供管理员核查" />
      <p>需求验收方提出服务未完成或质量异议后，相关计时暂停，等待管理员裁决。</p>
      <template #footer><el-button @click="disputeVisible = false">取消</el-button><el-button type="primary" :loading="busy" @click="complain">提交</el-button></template>
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
import { canReviewOrder, businessTime, orderStatusLabel } from '@/order/orderPolicy'

const props = defineProps({ order: { type: Object, required: true } })
const emit = defineEmits(['updated'])
const user = useUserStore()
const busy = ref(false)
const paymentVisible = ref(false)
const reviewVisible = ref(false)
const disputeVisible = ref(false)
const reason = ref('')
const description = ref('')
const review = ref(null)
const canReview = computed(() => canReviewOrder(props.order, user.userId))
const format = value => new Date(businessTime(value)).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' })

// 操作资格使用订单双方ID，切换页面角色不会改变既有订单的付款人或验收人。
async function act(action) {
  const labels = { submit: '提交服务完成', accept: '确认服务已完成并验收', cancel: '取消订单' }
  try { await ElMessageBox.confirm(`确定${labels[action]}？`, '确认操作') } catch { return }
  busy.value = true
  try {
    if (action === 'submit') await orderApi.submitCompletion(props.order.orderId)
    else if (action === 'accept') await orderApi.complete(props.order.orderId)
    else await orderApi.cancel(props.order.orderId)
    ElMessage.success('操作成功')
    emit('updated')
  } catch {
    // HTTP拦截器已提示失败，保留原订单，不让事件处理器留下未捕获的Promise。
  } finally { busy.value = false }
}
async function loadReview() {
  if (!props.order.reviewEligibleAt || !props.order.hasReview) return
  try { review.value = (await reviewApi.getByOrderId(props.order.orderId)).data.data }
  catch { review.value = null }
}
async function reviewed() { await loadReview(); emit('updated') }
async function complain() {
  if (!reason.value || !description.value.trim()) { ElMessage.warning('请填写理由和具体说明'); return }
  busy.value = true
  try {
    await http.post('/complaints', { orderId: props.order.orderId, reason: reason.value, description: description.value })
    disputeVisible.value = false
    ElMessage.success('已提交，等待管理员处理')
    emit('updated')
  } catch {
    // 保留投诉内容，允许用户在网络恢复后重试。
  } finally { busy.value = false }
}
watch(() => [props.order.orderId, props.order.reviewEligibleAt], loadReview, { immediate: true })
</script>

<style scoped>
.order-actions { padding: 12px 0; }
.order-actions p { margin: 8px 0; font-size: 14px; }
.buttons { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.buttons :deep(.el-button) { margin-left: 0; }
</style>
