/**
 * 后端枚举值到界面文案与色调的统一映射。
 * 色调 tone 取值：brand（品牌绿）、info（蓝）、warn（杏黄）、danger（红）、muted（灰），
 * 由 StatusTag 组件映射为具体颜色，页面不再各自维护一份状态表。
 */

/** 需求状态：对应 Task.TaskStatus。 */
export const TASK_STATUS = {
  pending_review: { label: '待审核', tone: 'warn' },
  published: { label: '待接单', tone: 'brand' },
  in_progress: { label: '进行中', tone: 'info' },
  completed: { label: '已完成', tone: 'muted' },
  cancelled: { label: '已取消', tone: 'muted' }
}

/**
 * 需求类型：对应 task_type 辨别值。short 用于卡片上的类型牌，name 用于标题与表单。
 * CONSULTING 的子表由后端按分类映射，卡片同样按类型展示。
 */
export const TASK_TYPE = {
  RUNNING: { short: '跑腿', name: '跑腿服务', desc: '取快递、代买、送文件' },
  TUTORING: { short: '辅导', name: '学习辅导', desc: '课程答疑、考前辅导，按小时计价' },
  FLEA: { short: '二手', name: '二手交易', desc: '出闲置或收二手物品' },
  TEAM: { short: '组队', name: '活动组队', desc: '比赛、运动、活动找队友，不涉及金额' },
  CONSULTING: { short: '咨询', name: '信息咨询', desc: '经验分享、升学就业咨询' }
}

/**
 * 发布类型与分类 ID 的固定对应（分类表种子数据 1-5）。
 * 发布页按类型提交 categoryId；分类管理页据此标出与发布类型绑定、不可删除的分类。
 */
export const TYPE_CATEGORY_ID = { RUNNING: 1, TUTORING: 2, FLEA: 3, TEAM: 4, CONSULTING: 5 }

/** 订单履约状态：对应 Order.OrderStatus。 */
export const ORDER_STATUS = {
  in_progress: { label: '服务进行中', tone: 'info' },
  awaiting_acceptance: { label: '等待验收', tone: 'warn' },
  completed: { label: '已验收', tone: 'brand' },
  cancelled: { label: '已取消', tone: 'muted' }
}

/** 付款状态：对应 Order.PaymentStatus。not_required 为组队等无偿订单。 */
export const PAYMENT_STATUS = {
  not_required: { label: '无需付款', tone: 'muted' },
  unpaid: { label: '待付款', tone: 'warn' },
  paid: { label: '已付款', tone: 'brand' }
}

/** 人工结算状态：对应 Order.SettlementStatus。 */
export const SETTLEMENT_STATUS = {
  not_required: { label: '无需结算', tone: 'muted' },
  pending: { label: '待结算', tone: 'warn' },
  recorded: { label: '已登记结算', tone: 'brand' }
}

/** 支付渠道与支付单状态：对应 PaymentAttempt.Channel / Status。 */
export const PAYMENT_CHANNEL = { WECHAT: '微信支付', ALIPAY: '支付宝' }
export const PAYMENT_ATTEMPT_STATUS = { PENDING: '待支付', PAID: '已支付', CLOSED: '已关闭' }

/** 校区：与后端 tasks.campus 存储值一致。 */
export const CAMPUSES = ['鼓楼校区', '仙林校区', '浦口校区', '苏州校区']

/** 查表并在未知值时回落为原值，避免后端新增枚举后页面出现空白。 */
export function lookup(table, value) {
  return table[value] || { label: value ?? '', tone: 'muted' }
}
