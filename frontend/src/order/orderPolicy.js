/** 后端业务时间为上海时区；没有时区的 DATETIME 不能按浏览器所在地解释。 */
export function businessTime(value) {
  if (!value) return NaN
  const text = String(value)
  return Date.parse(/[zZ]|[+-]\d{2}:\d{2}$/.test(text) ? text : `${text}+08:00`)
}

/** UI 仅作可操作提示，最终的身份、付款和争议条件仍由后端独立校验。 */
export function canReviewOrder(order, actorId, now = Date.now()) {
  const eligible = businessTime(order.reviewEligibleAt)
  return order.payerId === actorId && order.orderStatus === 'completed' && order.hasReview && !order.disputed &&
    ['paid', 'not_required'].includes(order.paymentStatus) && Number.isFinite(eligible) &&
    now >= eligible && now < eligible + 7 * 24 * 60 * 60 * 1000
}

export function orderStatusLabel(status) {
  return { in_progress: '服务进行中', awaiting_acceptance: '等待验收', completed: '已验收', cancelled: '已取消' }[status] || status
}
