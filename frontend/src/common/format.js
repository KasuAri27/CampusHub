/**
 * 展示层通用格式化工具。
 * 本文件只使用相对导入，node --test 可以直接加载；页面通过 '@/common/format' 引用。
 */

/**
 * 后端业务时间为上海时区；没有时区后缀的 DATETIME 不能按浏览器所在地解释，
 * 否则在非 UTC+8 的机器上会整体偏移（本机开发环境即为 UTC-8）。
 */
export function businessTime(value) {
  if (!value) return NaN
  const text = String(value)
  return Date.parse(/[zZ]|[+-]\d{2}:\d{2}$/.test(text) ? text : `${text}+08:00`)
}

/** 统一按上海时区拆出年月日时分，避免浏览器本地时区参与展示。 */
function shanghaiParts(ms) {
  const parts = new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: 'numeric', day: 'numeric',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
  }).formatToParts(new Date(ms))
  const pick = type => parts.find(p => p.type === type)?.value
  return { year: Number(pick('year')), month: Number(pick('month')), day: Number(pick('day')), hour: pick('hour'), minute: pick('minute') }
}

/** “11月19日 14:30”；跨年时补年份。无效时间返回空串。 */
export function formatDateTime(value, now = Date.now()) {
  const ms = businessTime(value)
  if (!Number.isFinite(ms)) return ''
  const p = shanghaiParts(ms)
  const sameYear = shanghaiParts(now).year === p.year
  return `${sameYear ? '' : `${p.year}年`}${p.month}月${p.day}日 ${p.hour}:${p.minute}`
}

/** 仅时分，用于聊天气泡等同日场景。 */
export function formatClock(value) {
  const ms = businessTime(value)
  if (!Number.isFinite(ms)) return ''
  const p = shanghaiParts(ms)
  return `${p.hour}:${p.minute}`
}

/** 过去时间的相对描述：刚刚 / N分钟前 / N小时前 / N天前，超过一周回落到日期。 */
export function relativeTime(value, now = Date.now()) {
  const ms = businessTime(value)
  if (!Number.isFinite(ms)) return ''
  const diff = now - ms
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)}分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)}小时前`
  if (diff < 604_800_000) return `${Math.floor(diff / 86_400_000)}天前`
  return formatDateTime(value, now)
}

/**
 * 截止时间的剩余描述，供卡片与详情页提示紧迫程度。
 * urgent 表示 24 小时内截止，expired 表示已过截止时间（后端定时任务会自动取消）。
 */
export function deadlineInfo(value, now = Date.now()) {
  const ms = businessTime(value)
  if (!Number.isFinite(ms)) return { text: '', urgent: false, expired: false }
  const left = ms - now
  if (left <= 0) return { text: '已过截止时间', urgent: false, expired: true }
  if (left < 3_600_000) return { text: `${Math.max(1, Math.floor(left / 60_000))}分钟后截止`, urgent: true, expired: false }
  if (left < 86_400_000) return { text: `${Math.floor(left / 3_600_000)}小时后截止`, urgent: true, expired: false }
  if (left < 7 * 86_400_000) return { text: `${Math.floor(left / 86_400_000)}天后截止`, urgent: false, expired: false }
  return { text: `${formatDateTime(value, now)}截止`, urgent: false, expired: false }
}

/** 金额展示：整数不带小数，其余保留两位。后端 BigDecimal 序列化为数字或字符串均可。 */
export function formatMoney(value) {
  if (value === null || value === undefined || value === '') return ''
  const n = Number(value)
  if (!Number.isFinite(n)) return String(value)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

/** 截断长文本，用于卡片摘要。 */
export function truncate(text, max) {
  if (!text) return ''
  return text.length > max ? `${text.slice(0, max)}…` : text
}
