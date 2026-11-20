import { businessTime, formatDateTime, formatMoney } from '../common/format.js'

/**
 * 订单履约流程的展示规则（只用于界面提示，所有资格最终由后端判定）。
 * 规则来源：backend/PAYMENTS.md
 *   接单 → 服务方提交完成 → 需求验收方验收（提交后 24 小时无异议自动验收）
 *   → 付款（验收后 1 小时付款期，逾期扣 10 分，再过 24 小时转管理员）→ 平台人工结算。
 * 付款方向以订单 payerId / payeeId 为准，二手出售由买家（接单人）付款，不能按“发布者必定付款”推断。
 * 无偿订单（组队、免费互助）payment_status 为 not_required，没有付款与结算两步。
 * 本文件只用相对导入，供 node --test 直接加载。
 */

/**
 * 当前用户在订单里的身份。
 * key 用于判断操作资格（付款方验收并付款，收款方提交完成）；
 * label 按需求发布者 / 接单人描述，始终准确，不依赖 OrderDTO.taskType
 * （该字段目前因懒加载代理始终返回 SERVICE，不能用来区分组队与二手）。
 */
export function orderRole(order, userId) {
  const me = Number(userId)
  const key = Number(order.payeeId) === me ? 'payee' : Number(order.payerId) === me ? 'payer' : 'other'
  const label = Number(order.requesterId) === me ? '我发布的' : Number(order.providerId) === me ? '我接的单' : ''
  return { key, label }
}

/** 金额方向：有偿订单里我是应付还是应收。无偿订单返回空串。 */
export function moneyDirection(order, userId) {
  if (!paymentRequired(order)) return ''
  const me = Number(userId)
  if (Number(order.payerId) === me) return '应付'
  if (Number(order.payeeId) === me) return '应收'
  return '金额'
}

/** 无偿订单（组队、免费互助）后端置为 not_required，没有付款与结算两步。 */
export function paymentRequired(order) {
  return order.paymentStatus !== 'not_required'
}

/** 步骤条数据：done 已完成，active 当前所处步骤。已取消订单返回空数组。 */
export function orderSteps(order) {
  if (order.orderStatus === 'cancelled') return []
  const steps = [
    { key: 'accepted', label: '接单', time: order.acceptedAt, done: true },
    { key: 'submitted', label: '提交完成', time: order.completionSubmittedAt, done: Boolean(order.completionSubmittedAt) || ['awaiting_acceptance', 'completed'].includes(order.orderStatus) },
    { key: 'completed', label: '验收', time: order.completedAt, done: order.orderStatus === 'completed' }
  ]
  if (paymentRequired(order)) {
    steps.push(
      { key: 'paid', label: '付款', time: order.paidAt, done: order.paymentStatus === 'paid' },
      { key: 'settled', label: '平台结算', time: order.settledAt, done: order.settlementStatus === 'recorded' }
    )
  }
  const current = steps.find(s => !s.done)
  return steps.map(s => ({ ...s, active: s === current }))
}

/**
 * 下一步提示：need 为 true 表示轮到当前用户操作（界面用醒目样式），否则是等待说明。
 */
export function nextHint(order, userId, now = Date.now()) {
  const role = orderRole(order, userId).key
  const at = v => formatDateTime(v)
  const amount = order.amountDue != null ? `¥${formatMoney(order.amountDue)}` : '约定金额'

  if (order.orderStatus === 'cancelled') return { need: false, text: '订单已取消。' }
  if (order.disputed) {
    return { need: false, text: '存在待处理的异议，自动验收、扣分和升级计时已暂停，等待管理员裁决。' }
  }
  if (order.orderStatus === 'in_progress') {
    return role === 'payee'
      ? { need: true, text: '完成服务后点击“提交完成”，对方验收后订单才算完成。' }
      : { need: false, text: '对方正在履约，提交完成后你需要验收。' }
  }
  if (order.orderStatus === 'awaiting_acceptance') {
    const due = order.acceptanceDueAt ? `，${at(order.acceptanceDueAt)} 起无异议将自动验收` : ''
    return role === 'payer'
      ? { need: true, text: `对方已提交完成，请确认服务结果${due}。有问题请提出异议。` }
      : { need: false, text: `已提交完成，等待对方验收${due}。` }
  }
  // 已验收
  if (!paymentRequired(order)) return { need: false, text: '订单已完成。' }
  if (order.paymentStatus === 'unpaid') {
    if (order.escalatedAt) {
      return { need: role === 'payer', text: `已超过付款期限并转交管理员处理，请尽快付款 ${amount}。` }
    }
    const due = businessTime(order.paymentDueAt)
    const overdue = Number.isFinite(due) && now > due
    if (role === 'payer') {
      return overdue
        ? { need: true, text: `付款已逾期，请尽快付款 ${amount}；${order.escalationDueAt ? `${at(order.escalationDueAt)} 后` : '继续逾期'}将转交管理员。` }
        : { need: true, text: `请在 ${at(order.paymentDueAt)} 前付款 ${amount}，逾期将扣除 10 分信用分。` }
    }
    return { need: false, text: `等待对方付款 ${amount}，付款期限 ${at(order.paymentDueAt)}。` }
  }
  if (order.settlementStatus === 'recorded') return { need: false, text: `平台已登记人工结算，订单完成。` }
  return {
    need: false,
    text: role === 'payee' ? `对方已付款，平台将人工转账 ${amount} 给你。` : '付款已确认，平台会把款项人工结算给对方。'
  }
}
