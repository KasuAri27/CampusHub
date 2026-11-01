import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createHttpClient } from '../src/common/httpClient.js'

test('网络断开必须拒绝，不能伪造付款成功', async () => {
  const errors = []
  const client = createHttpClient({ getToken: () => 'jwt', onError: message => errors.push(message) })
  await assert.rejects(client.post('/payments/orders/1', {}, { adapter: () => Promise.reject(new Error('offline')) }))
  assert.deepEqual(errors, ['网络连接失败，请稍后重试'])
})
test('HTTP200携带业务错误也必须拒绝', async () => {
  const client = createHttpClient({ getToken: () => null, onError: () => {} })
  await assert.rejects(client.get('/orders/1', { adapter: config => Promise.resolve({ config, status: 200, data: { code: 403, message: '无权访问' } }) }), /无权访问/)
})
test('所有token正常发送请求，没有local-test-token后门', async () => {
  let sent = false
  const client = createHttpClient({ getToken: () => 'local-test-token', onError: () => {} })
  await client.get('/orders/mine', { adapter: config => {
    sent = true; assert.equal(config.headers.Authorization, 'Bearer local-test-token')
    return Promise.resolve({ config, status: 200, data: { code: 200, data: [] } })
  } })
  assert.equal(sent, true)
})
