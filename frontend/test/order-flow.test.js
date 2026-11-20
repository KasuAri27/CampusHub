import { test } from 'node:test'
import assert from 'node:assert/strict'
import { orderSteps, nextHint, orderRole, moneyDirection } from '../src/order/orderFlow.js'
import { businessTime } from '../src/order/orderPolicy.js'

const base = {
  orderId: 1, requesterId: 1, providerId: 2, payerId: 1, payeeId: 2,
  orderStatus: 'in_progress', paymentStatus: 'unpaid', settlementStatus: 'pending',
  amountDue: 100, acceptedAt: '2026-11-19T10:00:00', disputed: false
}

test('进行中订单：收款方需要提交完成，付款方等待', () => {
  assert.equal(nextHint(base, 2).need, true)
  assert.equal(nextHint(base, 1).need, false)
  const steps = orderSteps(base)
  assert.deepEqual(steps.map(s => s.key), ['accepted', 'submitted', 'completed', 'paid', 'settled'])
  assert.equal(steps.find(s => s.active).key, 'submitted')
})

test('待验收轮到付款方；验收后未付款时提示付款期限', () => {
  const awaiting = { ...base, orderStatus: 'awaiting_acceptance', completionSubmittedAt: '2026-11-20T10:00:00', acceptanceDueAt: '2026-11-21T10:00:00' }
  assert.equal(nextHint(awaiting, 1).need, true)
  assert.equal(orderSteps(awaiting).find(s => s.active).key, 'completed')

  const unpaid = { ...awaiting, orderStatus: 'completed', completedAt: '2026-11-20T12:00:00', paymentDueAt: '2026-11-20T13:00:00' }
  const before = businessTime('2026-11-20T12:30:00')
  assert.equal(nextHint(unpaid, 1, before).need, true)
  assert.match(nextHint(unpaid, 1, before).text, /扣除 10 分/)
  assert.equal(nextHint(unpaid, 2, before).need, false)
  assert.match(nextHint(unpaid, 1, businessTime('2026-11-20T14:00:00')).text, /逾期/)
})

test('异议中订单不提示任何一方操作', () => {
  const disputed = { ...base, orderStatus: 'awaiting_acceptance', disputed: true }
  assert.equal(nextHint(disputed, 1).need, false)
  assert.equal(nextHint(disputed, 2).need, false)
})

test('无偿订单没有付款与结算两步，也没有金额方向', () => {
  const free = { ...base, paymentStatus: 'not_required', settlementStatus: 'not_required', amountDue: 0 }
  assert.deepEqual(orderSteps(free).map(s => s.key), ['accepted', 'submitted', 'completed'])
  assert.equal(moneyDirection(free, 1), '')
  assert.equal(nextHint({ ...free, orderStatus: 'completed' }, 1).text, '订单已完成。')
})

test('二手出售时发布者收款：身份描述按发布/接单，金额方向按付款人', () => {
  const flea = { ...base, payerId: 2, payeeId: 1 }
  assert.equal(orderRole(flea, 1).label, '我发布的')
  assert.equal(orderRole(flea, 1).key, 'payee')
  assert.equal(moneyDirection(flea, 1), '应收')
  assert.equal(moneyDirection(flea, 2), '应付')
})

test('已取消订单没有步骤条', () => {
  assert.deepEqual(orderSteps({ ...base, orderStatus: 'cancelled' }), [])
})
