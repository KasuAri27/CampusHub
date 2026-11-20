<template>
  <div>
    <PageHeader title="交易与结算" description="裁决争议、处置逾期付款，并在真实转账给收款方后登记人工结算。登记不会代替转账。" />

    <div class="views" role="tablist" aria-label="订单视图">
      <button
        v-for="v in views"
        :key="v.key"
        type="button"
        role="tab"
        class="view"
        :class="{ on: view === v.key }"
        :aria-selected="view === v.key"
        @click="setView(v.key)"
      >
        {{ v.label }}<span v-if="v.key !== 'all' && counts[v.key]" class="count num">{{ counts[v.key] }}</span>
      </button>
    </div>

    <div class="panel table-wrap">
      <el-table :data="visibleOrders" v-loading="loading" row-key="orderId" empty-text="这一页没有符合条件的订单">
        <el-table-column label="订单" min-width="220">
          <template #default="{ row }">
            <router-link :to="`/tasks/${row.taskId}`" class="title-link">{{ row.title }}</router-link>
            <div class="cell-sub">#{{ row.orderId }}，付款人 #{{ row.payerId }}，收款人 #{{ row.payeeId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.paymentStatus === 'not_required'" class="muted">无偿</span>
            <span v-else-if="row.amountDue == null" class="muted">待核对</span>
            <span v-else class="num money">¥{{ formatMoney(row.amountDue) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="履约" width="104">
          <template #default="{ row }"><StatusTag v-bind="tag(ORDER_STATUS, row.orderStatus)" /></template>
        </el-table-column>
        <el-table-column label="付款" width="104">
          <template #default="{ row }">
            <StatusTag v-if="row.disputed" label="争议中" tone="danger" />
            <StatusTag v-else-if="row.escalatedAt && row.paymentStatus === 'unpaid'" label="逾期转交" tone="danger" />
            <StatusTag v-else v-bind="tag(PAYMENT_STATUS, row.paymentStatus)" />
          </template>
        </el-table-column>
        <el-table-column label="结算" width="110">
          <template #default="{ row }"><StatusTag v-bind="tag(SETTLEMENT_STATUS, row.settlementStatus)" /></template>
        </el-table-column>
        <el-table-column label="操作" width="128" align="right">
          <template #default="{ row }">
            <el-button v-if="row.disputed" link type="danger" @click="open(row, 'resolve')">裁决争议</el-button>
            <el-button v-else-if="row.escalatedAt && row.paymentStatus === 'unpaid'" link type="danger" @click="open(row, 'penalty')">逾期处置</el-button>
            <el-button v-else-if="row.paymentStatus === 'paid' && row.settlementStatus !== 'recorded'" link type="primary" @click="open(row, 'settle')">登记结算</el-button>
            <el-button v-else-if="row.settlementStatus === 'recorded'" link @click="download(row)">下载凭证</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="paging">
      <el-button :disabled="page === 1 || loading" @click="go(page - 1)">上一页</el-button>
      <span>第 <span class="num">{{ page }}</span> 页，每页 50 笔</span>
      <el-button :disabled="orders.length < 50 || loading" @click="go(page + 1)">下一页</el-button>
    </div>

    <el-dialog v-model="visible" :title="titles[mode]" width="520px">
      <dl class="order-facts">
        <dt>订单</dt><dd>#{{ selected?.orderId }} {{ selected?.title }}</dd>
        <dt>付款 / 收款</dt><dd>#{{ selected?.payerId }} 付给 #{{ selected?.payeeId }}</dd>
        <dt>约定金额</dt><dd class="num">¥{{ formatMoney(selected?.amountDue) }}</dd>
        <template v-if="selected?.disputeReason"><dt>异议说明</dt><dd>{{ selected.disputeReason }}</dd></template>
      </dl>
      <div v-if="history.length" class="history">
        <p class="history-title">支付记录</p>
        <p v-for="payment in history" :key="payment.id" class="history-row">
          {{ PAYMENT_CHANNEL[payment.channel] || payment.channel }}，¥{{ formatMoney(payment.amount) }}，{{ PAYMENT_ATTEMPT_STATUS[payment.status] || payment.status }}
          <span class="cell-sub">{{ payment.providerTransactionId || payment.outTradeNo }}</span>
        </p>
      </div>

      <el-form label-position="top" class="dialog-form" @submit.prevent>
        <template v-if="mode === 'resolve'">
          <div class="row2">
            <el-form-item label="裁定应付金额（元）">
              <el-input-number v-model="form.amount" :precision="2" :min="0" :max="Number(selected.amountDue)" />
            </el-form-item>
            <el-form-item label="裁决后付款期限（小时）">
              <el-input-number v-model="form.paymentHours" :min="1" :max="168" />
            </el-form-item>
          </div>
          <el-form-item label="核查证据与裁决理由" required>
            <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
          </el-form-item>
        </template>
        <template v-if="mode === 'penalty'">
          <el-form-item label="额外扣分（逾期时已自动扣 10 分）">
            <el-input-number v-model="form.deduction" :min="0" :max="100" />
          </el-form-item>
          <el-checkbox v-model="form.ban">同时禁用付款人账号</el-checkbox>
          <el-form-item label="处罚理由" required class="mt">
            <el-input v-model="form.reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="说明金额、核查情况和处罚理由" />
          </el-form-item>
        </template>
        <template v-if="mode === 'settle'">
          <p class="settle-warn">只有在已经实际向收款人转账 ¥{{ formatMoney(selected.amountDue) }} 之后才能登记。</p>
          <el-form-item label="转账流水号" required>
            <el-input v-model.trim="form.reference" maxlength="100" />
          </el-form-item>
          <el-form-item label="转账凭证（图片或 PDF）" required>
            <input type="file" accept="image/png,image/jpeg,application/pdf" @change="file = $event.target.files[0]" />
          </el-form-item>
          <el-checkbox v-model="form.confirmed">我已核实收款人、金额和真实转账结果</el-checkbox>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button :type="mode === 'settle' ? 'primary' : 'danger'" :loading="saving" :disabled="historyLoading" @click="submit">{{ submitLabels[mode] }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '@/common/http'
import PageHeader from '@/common/ui/PageHeader.vue'
import StatusTag from '@/common/ui/StatusTag.vue'
import { ORDER_STATUS, PAYMENT_STATUS, SETTLEMENT_STATUS, PAYMENT_CHANNEL, PAYMENT_ATTEMPT_STATUS, lookup } from '@/common/labels'
import { formatMoney } from '@/common/format'

/**
 * 交易与人工结算（/v1/admin/finance）。
 * 列表接口按订单 ID 倒序每页 50 笔且不返回总数，视图筛选在当前页内进行。
 * 逾期处置携带 operationId：打开弹窗时生成，失败重试复用，后端据此避免重复扣分。
 * 人工结算须先真实转账，再上传流水号与凭证；登记不会触发任何渠道打款。
 */
const route = useRoute()
const router = useRouter()

const views = [
  { key: 'all', label: '全部' },
  { key: 'dispute', label: '争议待裁决' },
  { key: 'overdue', label: '逾期待处置' },
  { key: 'settle', label: '待登记结算' }
]
const titles = { resolve: '裁决争议', penalty: '逾期付款处置', settle: '登记人工结算' }
const submitLabels = { resolve: '提交裁决', penalty: '执行处置', settle: '登记结算' }

const orders = ref([])
const loading = ref(false)
const page = ref(1)
const visible = ref(false)
const saving = ref(false)
const selected = ref(null)
const mode = ref('')
const file = ref(null)
const history = ref([])
const historyLoading = ref(false)
const form = reactive({ amount: 0, paymentHours: 1, reason: '', deduction: 0, ban: false, reference: '', confirmed: false, operationId: '' })

const view = computed(() => (views.some(v => v.key === route.query.view) ? route.query.view : 'all'))
const matchers = {
  all: () => true,
  dispute: o => o.disputed,
  overdue: o => o.escalatedAt && o.paymentStatus === 'unpaid' && !o.disputed,
  settle: o => o.paymentStatus === 'paid' && o.settlementStatus !== 'recorded' && !o.disputed
}
const visibleOrders = computed(() => orders.value.filter(matchers[view.value]))
const counts = computed(() => Object.fromEntries(Object.keys(matchers).map(k => [k, orders.value.filter(matchers[k]).length])))

function tag(table, value) {
  const t = lookup(table, value)
  return { label: t.label, tone: t.tone }
}

function setView(key) {
  router.replace({ query: key === 'all' ? {} : { view: key } })
}

async function load() {
  loading.value = true
  try { orders.value = (await http.get('/admin/finance/orders', { params: { page: page.value } })).data.data || [] }
  catch { orders.value = [] }
  finally { loading.value = false }
}

function go(p) {
  page.value = p
  load()
}

async function open(order, action) {
  selected.value = order
  mode.value = action
  file.value = null
  Object.assign(form, { amount: Number(order.amountDue), paymentHours: 1, reason: '', deduction: 0, ban: false, reference: '', confirmed: false, operationId: globalThis.crypto?.randomUUID?.() || `op-${Date.now().toString(36)}` })
  visible.value = true
  history.value = []
  historyLoading.value = true
  try { history.value = (await http.get(`/admin/finance/orders/${order.orderId}/payments`)).data.data || [] }
  catch { visible.value = false }
  finally { historyLoading.value = false }
}

// 同一打开的处罚表单复用操作编号，网络重试不会重复扣分。
async function submit() {
  if (mode.value !== 'settle' && !form.reason.trim()) { ElMessage.warning('请填写理由'); return }
  if (mode.value === 'settle' && (!form.confirmed || !file.value || !form.reference)) { ElMessage.warning('请填写转账流水号、上传凭证并勾选核实'); return }
  saving.value = true
  try {
    const base = `/admin/finance/orders/${selected.value.orderId}`
    if (mode.value === 'settle') {
      const body = new FormData()
      body.append('amount', selected.value.amountDue)
      body.append('reference', form.reference)
      body.append('evidence', file.value)
      await http.post(`${base}/settlement`, body)
    } else if (mode.value === 'resolve') {
      await http.post(`${base}/resolve`, { amount: form.amount, paymentHours: form.paymentHours, reason: form.reason })
    } else {
      await http.post(`${base}/penalty`, { operationId: form.operationId, deduction: form.deduction, ban: form.ban, reason: form.reason })
    }
    visible.value = false
    ElMessage.success({ resolve: '裁决已提交', penalty: '处置已执行', settle: '人工结算已登记' }[mode.value])
    await load()
  } catch {
    // 表单与操作编号保持不变，重试同一请求不会重复扣分或登记结算。
  } finally {
    saving.value = false
  }
}

async function download(order) {
  try {
    const response = await http.get(`/admin/finance/orders/${order.orderId}/evidence`, { responseType: 'blob' })
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `settlement-${order.orderId}`
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch {
    // 下载失败由请求层提示；只有成功响应才创建可下载文件。
  }
}

watch(() => route.path, () => { if (route.path === '/admin/finance') load() }, { immediate: true })
</script>

<style scoped>
.views {
  display: flex;
  gap: 4px;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--line);
}
.view {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 14px;
  border: 0;
  background: none;
  color: var(--ink-2);
  font: inherit;
  font-weight: 500;
  cursor: pointer;
}
.view.on { color: var(--pine); font-weight: 700; }
.view.on::after {
  content: "";
  position: absolute;
  left: 14px;
  right: 14px;
  bottom: 0;
  height: 3px;
  background: var(--apricot);
  border-radius: 2px 2px 0 0;
}
.count {
  min-width: 20px;
  padding: 0 6px;
  border-radius: var(--r-pill);
  background: var(--apricot);
  color: var(--ink);
  font-size: var(--fs-xs);
  font-weight: 700;
  line-height: 18px;
  text-align: center;
}
.table-wrap { padding: 4px 8px; }
.title-link { color: var(--ink); font-weight: 600; text-decoration: none; }
.title-link:hover { color: var(--mint); }
.cell-sub { color: var(--ink-3); font-size: var(--fs-xs); }
.muted { color: var(--ink-3); }
.money { color: var(--amber-ink); font-weight: 600; }
.paging {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 20px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}
.order-facts {
  display: grid;
  grid-template-columns: 84px 1fr;
  gap: 4px 12px;
  margin: 0 0 12px;
  padding: 12px 14px;
  background: #f7f9f8;
  border-radius: 8px;
  font-size: var(--fs-sm);
}
.order-facts dt { color: var(--ink-2); }
.order-facts dd { margin: 0; }
.history { margin-bottom: 12px; font-size: var(--fs-sm); }
.history-title { color: var(--ink-2); margin-bottom: 4px; }
.history-row { display: flex; gap: 10px; }
.row2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.mt { margin-top: 12px; }
.settle-warn {
  margin-bottom: 12px;
  padding: 8px 12px;
  border-left: 3px solid var(--apricot);
  background: var(--apricot-wash);
  color: var(--amber-ink);
  font-size: var(--fs-sm);
}
</style>
