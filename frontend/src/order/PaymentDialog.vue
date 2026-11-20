<template>
  <el-dialog :model-value="visible" title="付款" width="420px" append-to-body @update:model-value="emit('update:visible', $event)">
    <div class="pay">
      <p class="pay-label">应付金额</p>
      <p class="pay-amount"><span class="yen">¥</span><span class="num">{{ formatMoney(order.amountDue) }}</span></p>
      <p class="pay-title">{{ order.title }}</p>

      <p v-if="!channels.length" class="pay-off">支付渠道暂未开通，请通过“提出异议或投诉”联系平台处理。</p>
      <template v-else-if="!qrCode">
        <p class="pay-note">选择支付方式后生成收款码。同一笔订单请始终使用同一个渠道，避免重复付款。</p>
        <div class="channels">
          <el-button v-for="channel in channels" :key="channel" size="large" :loading="loading" @click="create(channel)">
            {{ PAYMENT_CHANNEL[channel] || channel }}
          </el-button>
        </div>
      </template>

      <div v-if="qrCode" class="payment-code">
        <img :src="qrCode" alt="本订单的支付二维码" width="220" height="220" />
        <p>用对应的支付应用扫码付款。付款结果以平台收到的渠道通知为准，页面会自动刷新结果。</p>
        <el-button :loading="checking" @click="check">我已付款，查询结果</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'
import QRCode from 'qrcode'
import { ElMessage } from 'element-plus'
import http from '@/common/http'
import { formatMoney } from '@/common/format'
import { PAYMENT_CHANNEL } from '@/common/labels'

const props = defineProps({ visible: Boolean, order: { type: Object, required: true } })
const emit = defineEmits(['update:visible', 'paid'])
const channels = ref([])
const loading = ref(false)
const checking = ref(false)
const qrCode = ref('')
let timer
let polls = 0
let generation = 0
function stop() { clearInterval(timer); timer = undefined }

// 二维码在浏览器本地生成，不向第三方二维码服务发送支付链接。
async function create(channel) {
  loading.value = true
  const requestGeneration = generation
  try {
    const { data } = await http.post(`/payments/orders/${props.order.orderId}`, { channel })
    const image = await QRCode.toDataURL(data.data.codeUrl, { width: 240, margin: 2 })
    if (!props.visible || requestGeneration !== generation) return
    qrCode.value = image
    stop()
    polls = 0
    timer = setInterval(() => { if (++polls > 24) stop(); else check() }, 5000)
  } catch (error) {
    // 下单失败不能展示旧二维码；网络错误已由拦截器提示。
    stop(); qrCode.value = ''
    if (!error.isAxiosError && !error.response) ElMessage.error('支付二维码生成失败，请稍后重试')
  } finally { loading.value = false }
}
async function check() {
  if (checking.value || !props.visible) return
  checking.value = true
  try {
    const { data } = await http.get(`/payments/orders/${props.order.orderId}`)
    if (data.data.status === 'PAID') {
      stop()
      ElMessage.success('付款已确认，等待平台结算')
      emit('paid')
      emit('update:visible', false)
    } else if (data.data.status === 'CLOSED') {
      stop(); qrCode.value = ''; ElMessage.info('原支付单已关闭，请重新选择渠道发起付款')
    }
  } catch { stop() }
  finally { checking.value = false }
}
watch(() => props.visible, async visible => {
  generation++
  stop()
  qrCode.value = ''
  if (visible) {
    try { channels.value = (await http.get('/payments/channels')).data.data }
    catch { channels.value = [] }
  }
})
onUnmounted(() => { generation++; stop() })
</script>

<style scoped>
.pay { text-align: center; }
.pay-label { color: var(--ink-2); font-size: var(--fs-sm); }
.pay-amount { color: var(--amber-ink); font-weight: 600; }
.pay-amount .yen { font-size: var(--fs-lg); margin-right: 2px; }
.pay-amount .num { font-size: 40px; line-height: 1.2; }
.pay-title { margin-top: 2px; color: var(--ink-2); font-size: var(--fs-sm); }
.pay-note, .pay-off { margin: 18px 0 14px; color: var(--ink-2); font-size: var(--fs-sm); text-align: left; }
.pay-off { padding: 10px 12px; background: var(--apricot-wash); color: var(--amber-ink); border-radius: 6px; }
.channels { display: flex; gap: 10px; }
.channels .el-button { flex: 1; margin: 0; }
.payment-code { margin-top: 18px; }
.payment-code img { border: 1px solid var(--line); border-radius: 6px; }
.payment-code p { margin: 12px 0; color: var(--ink-2); font-size: var(--fs-sm); }
</style>
