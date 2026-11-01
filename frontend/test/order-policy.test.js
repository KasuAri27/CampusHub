import { test } from 'node:test'
import assert from 'node:assert/strict'
import { businessTime, canReviewOrder } from '../src/order/orderPolicy.js'
import { tutoringTotal } from '../src/order/money.js'

const eligible = '2026-10-31T12:00:00'
const now = businessTime(eligible)
const order = { payerId: 1, orderStatus: 'completed', hasReview: true, disputed: false, paymentStatus: 'paid', reviewEligibleAt: eligible }

test('只有本人已支付或无偿已验收订单可评价', () => {
  assert.equal(canReviewOrder(order, 1, now), true)
  assert.equal(canReviewOrder(order, 2, now), false)
  assert.equal(canReviewOrder({ ...order, paymentStatus: 'unpaid' }, 1, now), false)
  assert.equal(canReviewOrder({ ...order, paymentStatus: 'not_required' }, 1, now), true)
  assert.equal(canReviewOrder({ ...order, disputed: true }, 1, now), false)
  assert.equal(canReviewOrder({ ...order, reviewEligibleAt: null }, 1, now), false)
})
test('七天边界与上海业务时间不依赖浏览器时区', () => {
  assert.equal(now, Date.parse('2026-10-31T04:00:00Z'))
  assert.equal(canReviewOrder(order, 1, now + 7 * 86400000 - 1), true)
  assert.equal(canReviewOrder(order, 1, now + 7 * 86400000), false)
  assert.equal(canReviewOrder(order, 1, now - 1), false)
})
test('辅导总价使用整数分四舍五入', () => {
  assert.equal(tutoringTotal('30.00', '1.50'), '45.00')
  assert.equal(tutoringTotal('1.01', '0.50'), '0.51')
  assert.equal(tutoringTotal(0, 2), '0.00')
  assert.throws(() => tutoringTotal(-1, 1))
  assert.throws(() => tutoringTotal('1.001', 1))
})
