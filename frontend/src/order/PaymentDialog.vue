<template>
  <el-dialog :model-value="visible" title="订单付款" width="400px" @update:model-value="emit('update:visible', $event)">
    <p>应付金额：¥{{ order.amountDue }}</p>
    <p v-if="!channels.length">支付暂不可用，请联系平台处理。</p>
    <template v-else>
      <p>每笔订单请使用同一支付渠道，避免重复付款。</p>
      <el-button v-for="channel in channels" :key="channel" :loading="loading" @click="create(channel)">
        {{ channel === 'WECHAT' ? '微信支付' : '支付宝' }}
      </el-button>
    </template>
    <div v-if="qrCode" class="payment-code">
      <img :src="qrCode" alt="本订单支付二维码" width="240" height="240" />
      <p>请使用对应支付应用扫码。付款结果以后台确认为准。</p>
      <el-button :loading="checking" @click="check">查询付款结果</el-button>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'
import QRCode from 'qrcode'
import { ElMessage } from 'element-plus'
import http from '@/common/http'

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
.payment-code { margin-top: 16px; text-align: center; }
.payment-code p { margin: 12px 0; }
</style>
