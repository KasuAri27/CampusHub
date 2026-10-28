<template>
  <div class="my-orders-page">
    <div class="page-header">
      <h1>我的订单</h1>
      <p>查看你接过和处理过的所有订单</p>
    </div>

    <el-tabs v-model="activeTab" @tab-change="fetchOrders">
      <el-tab-pane label="进行中" name="in_progress" />
      <el-tab-pane label="已完成" name="completed" />
      <el-tab-pane label="已取消" name="cancelled" />
    </el-tabs>

    <div v-if="loading" class="loading-state">加载中...</div>
    <div v-else-if="orders.length === 0" class="empty-state">
      <p>暂无相关订单</p>
    </div>
    <div v-else class="order-grid">
      <div v-for="order in orders" :key="order.orderId" class="order-card">
        <!-- 卡片内容，风格与 TaskCard 保持一致 -->
        <div class="order-header">
          <span class="status" :class="order.orderStatus">{{ order.orderStatus }}</span>
          <span class="reward">¥{{ order.reward || '免费' }}</span>
        </div>
        <h4>{{ order.title }}</h4>
        <p class="desc">{{ order.description }}</p>

        <div class="order-actions" v-if="userStore.isRequester && order.orderStatus === 'accepted'">
          <el-button type="success" @click="handleComplete(order.orderId)">确认完成</el-button>
          <el-button @click="handleCancel(order.orderId)">取消订单</el-button>
        </div>

        <div class="order-actions" v-if="userStore.isRequester && order.orderStatus === 'completed' && canReview(order)">
          <el-button
              v-if="!order.reviewDetail && !isOrderReviewExpired(order)"
              type="primary"
              @click="openReviewDialog(order.orderId)"
          >
            评价服务方
          </el-button>
          <span
              v-else-if="(!order.reviewDetail && isOrderReviewExpired(order)) || isAutoReviewed(order)"
              style="color: #94a3b8; font-size: 12px; margin-left: 10px;"
          >
            7天内没有评价服务方，默认五星好评
          </span>
        </div>
      </div>
    </div>

    <ReviewDialog
        v-model:visible="reviewDialogVisible"
        :orderId="currentReviewOrderId"
        @submitted="fetchOrders"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import orderApi from '@/order/orderApi'
import reviewApi from '@/review/reviewApi'
import { useUserStore } from '@/user/userStore'
import ReviewDialog from '@/review/reviewDialog.vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const userStore = useUserStore()
const activeTab = ref('in_progress')
const orders = ref([])
const loading = ref(false)
const reviewDialogVisible = ref(false)
const currentReviewOrderId = ref(null)

async function fetchOrders() {
  loading.value = true
  try {
    const res = await orderApi.myAccepted(activeTab.value)
    orders.value = res.data?.data || []
  } finally {
    loading.value = false
  }
}

async function handleComplete(orderId) {
  await ElMessageBox.confirm('确认该订单已完成？', '提示')
  await orderApi.complete(orderId)
  ElMessage.success('已确认完成')
  fetchOrders()
}

async function handleCancel(orderId) {
  await ElMessageBox.confirm('确定要取消该订单吗？', '提示', { type: 'warning' })
  await orderApi.cancel(orderId)
  ElMessage.success('订单已取消')
  fetchOrders()
}

function canReview(order) {
  // 这里可以根据 task 的 category 判断，简单版先放开
  return true
}

function openReviewDialog(orderId) {
  currentReviewOrderId.value = orderId
  reviewDialogVisible.value = true
}

async function fetchOrders() {
  loading.value = true
  try {
    const res = await orderApi.myAccepted(activeTab.value)
    const list = res.data?.data || []

    // 如果查询的是已完成订单，顺便获取它们的评价状态
    if (activeTab.value === 'completed') {
      for (const order of list) {
        try {
          const reviewRes = await reviewApi.getByOrderId(order.orderId)
          order.reviewDetail = reviewRes.data?.data
        } catch(e) {}
      }
    }
    orders.value = list
  } finally {
    loading.value = false
  }
}

function isOrderReviewExpired(order) {
  if (!order.completedAt) return false;
  const completedTime = new Date(order.completedAt).getTime();
  return (Date.now() - completedTime) >= 7 * 24 * 60 * 60 * 1000;
}

function isAutoReviewed(order) {
  return order.reviewDetail && order.reviewDetail.comment === '默认好评';
}

onMounted(fetchOrders)
</script>