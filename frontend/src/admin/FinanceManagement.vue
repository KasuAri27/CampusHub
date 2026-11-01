<template>
  <div class="finance-page">
    <h1>交易与人工结算</h1>
    <p>核对实际收款、争议与逾期记录。仅在真实向收款方转账后登记结算，登记不会代替转账。</p>
    <el-table :data="orders" v-loading="loading">
      <el-table-column prop="orderId" label="订单" width="80" />
      <el-table-column prop="title" label="任务" />
      <el-table-column prop="payerId" label="付款人ID" width="100" />
      <el-table-column prop="payeeId" label="收款人ID" width="100" />
      <el-table-column label="金额" width="100"><template #default="{ row }">{{ row.taskType === 'TEAM' ? '无偿组队' : row.amountDue }}</template></el-table-column>
      <el-table-column prop="paymentStatus" label="支付状态" width="110" />
      <el-table-column prop="settlementStatus" label="结算状态" width="110" />
      <el-table-column label="操作" width="270"><template #default="{ row }">
        <el-button v-if="row.disputed" size="small" @click="open(row, 'resolve')">处理异议</el-button>
        <el-button v-if="row.escalatedAt && row.paymentStatus === 'unpaid' && !row.disputed" size="small" @click="open(row, 'penalty')">逾期处置</el-button>
        <el-button v-if="row.paymentStatus === 'paid' && row.settlementStatus !== 'recorded' && !row.disputed" size="small" @click="open(row, 'settle')">登记实际转账</el-button>
        <el-button v-if="row.settlementStatus === 'recorded'" size="small" @click="download(row)">下载凭证</el-button>
      </template></el-table-column>
    </el-table>
    <div class="paging"><el-button :disabled="page === 1" @click="page--; load()">上一页</el-button><span>第{{ page }}页</span><el-button :disabled="orders.length < 50" @click="page++; load()">下一页</el-button></div>
    <el-dialog v-model="visible" :title="titles[mode]" width="480px">
      <p>订单#{{ selected?.orderId }}，付款人#{{ selected?.payerId }}，收款人#{{ selected?.payeeId }}</p>
      <p v-if="selected?.disputeReason">异议说明：{{ selected.disputeReason }}</p>
      <ul v-if="history.length"><li v-for="payment in history" :key="payment.id">{{ payment.channel }} · ¥{{ payment.amount }} · {{ payment.status }} · {{ payment.providerTransactionId || payment.outTradeNo }}</li></ul>
      <template v-if="mode === 'resolve'">
        <el-input-number v-model="form.amount" :precision="2" :min="0" :max="Number(selected.amountDue)" /> 元应付款
        <p>裁决后付款期限：<el-input-number v-model="form.paymentHours" :min="1" :max="168" /> 小时</p>
        <el-input v-model="form.reason" type="textarea" maxlength="500" placeholder="填写核查证据及裁决理由" />
      </template>
      <template v-if="mode === 'penalty'">
        <p>额外扣分：<el-input-number v-model="form.deduction" :min="0" :max="100" /></p>
        <el-checkbox v-model="form.ban">封禁账号</el-checkbox>
        <el-input v-model="form.reason" type="textarea" maxlength="500" placeholder="说明金额、核查情况和处罚理由" />
      </template>
      <template v-if="mode === 'settle'">
        <p>须已实际向收款人转账 ¥{{ selected.amountDue }}。</p>
        <el-input v-model="form.reference" maxlength="100" placeholder="实际转账流水号" />
        <input type="file" accept="image/png,image/jpeg,application/pdf" @change="file = $event.target.files[0]" />
        <el-checkbox v-model="form.confirmed">已核实收款人、金额和真实转账结果</el-checkbox>
      </template>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="historyLoading" @click="submit">提交</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import http from '@/common/http'
const orders = ref([]), loading = ref(false), page = ref(1), visible = ref(false), saving = ref(false)
const selected = ref(null), mode = ref(''), file = ref(null)
const history = ref([])
const historyLoading = ref(false)
const titles = { resolve: '管理员裁决', penalty: '逾期付款处置', settle: '登记人工结算' }
const form = reactive({ amount: 0, paymentHours: 1, reason: '', deduction: 0, ban: false, reference: '', confirmed: false, operationId: '' })
async function load() {
  loading.value = true
  try { orders.value = (await http.get('/admin/finance/orders', { params: { page: page.value } })).data.data }
  catch { orders.value = [] }
  finally { loading.value = false }
}
async function open(order, action) {
  selected.value = order; mode.value = action; file.value = null
  Object.assign(form, { amount: Number(order.amountDue), paymentHours: 1, reason: '', deduction: 0, ban: false, reference: '', confirmed: false, operationId: crypto.randomUUID() })
  visible.value = true; history.value = []; historyLoading.value = true
  try { history.value = (await http.get(`/admin/finance/orders/${order.orderId}/payments`)).data.data }
  catch { visible.value = false }
  finally { historyLoading.value = false }
}
// 同一打开的处罚表单复用操作编号，网络重试不会重复扣分。
async function submit() {
  if (mode.value !== 'settle' && !form.reason.trim()) { ElMessage.warning('请填写理由'); return }
  if (mode.value === 'settle' && (!form.confirmed || !file.value || !form.reference.trim())) { ElMessage.warning('请核实真实转账并提供流水及凭证'); return }
  saving.value = true
  try {
    const base = `/admin/finance/orders/${selected.value.orderId}`
    if (mode.value === 'settle') {
      const body = new FormData(); body.append('amount', selected.value.amountDue); body.append('reference', form.reference); body.append('evidence', file.value)
      await http.post(`${base}/settlement`, body)
    } else if (mode.value === 'resolve') await http.post(`${base}/resolve`, { amount: form.amount, paymentHours: form.paymentHours, reason: form.reason })
    else await http.post(`${base}/penalty`, { operationId: form.operationId, deduction: form.deduction, ban: form.ban, reason: form.reason })
    visible.value = false; ElMessage.success('已记录'); await load()
  } catch {
    // 表单与操作编号保持不变，重试同一请求不会重复扣分或登记结算。
  } finally { saving.value = false }
}
async function download(order) {
  try {
    const response = await http.get(`/admin/finance/orders/${order.orderId}/evidence`, { responseType: 'blob' })
    const url = URL.createObjectURL(response.data), link = document.createElement('a')
    link.href = url; link.download = `settlement-${order.orderId}`; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch {
    // 下载失败由请求层提示；只有成功响应才创建可下载文件。
  }
}
onMounted(load)
</script>
<style scoped>
.finance-page { padding: 24px; }
.paging { display: flex; align-items: center; gap: 12px; margin-top: 16px; }
input[type=file] { display: block; margin: 16px 0; }
</style>
