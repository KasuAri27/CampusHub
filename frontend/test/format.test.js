import { test } from 'node:test'
import assert from 'node:assert/strict'
import { businessTime, formatDateTime, relativeTime, deadlineInfo, formatMoney } from '../src/common/format.js'
import { assetUrl } from '../src/common/assetUrl.js'

const now = businessTime('2026-11-19T12:00:00')

test('无时区的业务时间按上海时间展示，与运行环境时区无关', () => {
  assert.equal(formatDateTime('2026-11-19T08:05:00', now), '11月19日 08:05')
  assert.equal(formatDateTime('2025-12-31T23:00:00', now), '2025年12月31日 23:00')
  assert.equal(formatDateTime(null), '')
})

test('相对时间与截止提示', () => {
  assert.equal(relativeTime('2026-11-19T11:59:30', now), '刚刚')
  assert.equal(relativeTime('2026-11-19T09:00:00', now), '3小时前')
  assert.deepEqual(deadlineInfo('2026-11-19T11:00:00', now), { text: '已过截止时间', urgent: false, expired: true })
  assert.equal(deadlineInfo('2026-11-19T15:30:00', now).urgent, true)
  assert.equal(deadlineInfo('2026-11-21T12:00:00', now).text, '2天后截止')
})

test('金额：整数不带小数，其余保留两位', () => {
  assert.equal(formatMoney(100), '100')
  assert.equal(formatMoney('12.5'), '12.50')
  assert.equal(formatMoney(null), '')
})

test('上传文件地址走同源相对路径，旧的本机绝对地址被改写（K6）', () => {
  assert.equal(assetUrl('/api/uploads/a.png'), '/api/uploads/a.png')
  assert.equal(assetUrl('http://localhost:8080/api/uploads/a.png'), '/api/uploads/a.png')
  assert.equal(assetUrl('https://cdn.example.com/a.png'), 'https://cdn.example.com/a.png')
  assert.equal(assetUrl('uploads/a.png'), '/api/uploads/a.png')
  assert.equal(assetUrl(''), '')
})
