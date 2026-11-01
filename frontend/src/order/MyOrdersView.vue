<template>
  <div class="my-orders-page">
    <h1>我的订单</h1>
    <el-alert v-if="user.restricted" title="账号受限：仍可处理欠款和申诉，暂不能发布或接取新任务。" type="warning" :closable="false" />
    <p>查看本人参与的服务、验收、付款和结算状态。</p>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="进行中" name="in_progress" />
      <el-tab-pane label="待验收" name="awaiting_acceptance" />
      <el-tab-pane label="已验收" name="completed" />
      <el-tab-pane label="已取消" name="cancelled" />
    </el-tabs>
    <div v-loading="loading">
      <el-empty v-if="!filtered.length" description="暂无相关订单" />
      <article v-for="order in filtered" :key="order.orderId" class="order-card">
        <h3><router-link :to="`/tasks/${order.taskId}`">{{ order.title }}</router-link></h3>
        <p>{{ order.description }}</p>
        <OrderActions :order="order" @updated="load" />
      </article>
    </div>
  </div>
</template>
<script setup>
import { ref, computed, onMounted } from 'vue'
import orderApi from '@/order/orderApi'
import OrderActions from '@/order/OrderActions.vue'
import { useUserStore } from '@/user/userStore'
const user = useUserStore()
const loading = ref(false)
const orders = ref([])
const activeTab = ref('all')
const filtered = computed(() => orders.value.filter(order => activeTab.value === 'all' || order.orderStatus === activeTab.value))
// 同时覆盖双方订单，切换身份不会丢失验收和付款入口。
async function load() {
  loading.value = true
  try { orders.value = (await orderApi.mine()).data.data }
  catch {
    // 请求层已提示错误；保留已有列表，避免加载失败被误当成订单消失。
  }
  finally { loading.value = false }
}
onMounted(load)
</script>
<style scoped>
.my-orders-page { max-width: 1000px; margin: auto; padding: 24px; }
.order-card { padding: 20px; margin: 16px 0; border: 1px solid var(--border-color); border-radius: var(--radius-lg); }
</style>
