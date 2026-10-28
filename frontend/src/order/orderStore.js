import http from '@/common/http'

/**
 * 订单模块 API 封装 — 对应后端 OrderController
 *
 * 注意：OrderController 使用 @RequestHeader("X-User-Id")，
 * http.js 请求拦截器已全局注入该 Header（见更新后的 http.js）。
 */
export default {
    /**
     * POST /v1/orders/accept?taskId={id} — 服务方接单
     * 返回 OrderDTO（含 orderId），需缓存供需求方后续操作使用
     */
    accept(taskId) {
        return http.post('/orders/accept', null, { params: { taskId } })
    },

    /**
     * POST /v1/orders/{id}/complete — 需求方确认完成订单
     * 只有需求方可调用
     */
    complete(orderId) {
        return http.post(`/orders/${orderId}/complete`)
    },

    /**
     * POST /v1/orders/{id}/cancel — 取消订单
     * 需求方和服务方均可取消
     */
    cancel(orderId) {
        return http.post(`/orders/${orderId}/cancel`)
    },

    /**
     * GET /v1/orders/my-accepted?status={status} — 服务方接单历史
     * status: accepted | in_progress | completed | cancelled（可选）
     */
    myAccepted(status) {
        return http.get('/orders/my-accepted', {
            params: status ? { status } : {}
        })
    }
}