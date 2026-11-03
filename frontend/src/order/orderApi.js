import http from '@/common/http'

/**
 * 订单模块 API 封装 — 对应后端 OrderController
 *
 * 注意：身份由 JWT 统一传递（http.js 拦截器注入 Authorization），后端从 SecurityContext 读取。
 */
export default {
    /** 同时返回作为需求方和服务方参与的订单，避免旧“我的接单”遗漏付款入口。 */
    mine() { return http.get('/orders/mine') },
    detail(orderId) { return http.get(`/orders/${orderId}`) },
    submitCompletion(orderId) { return http.post(`/orders/${orderId}/submit-completion`) },
    getByTaskId(taskId) {
        return http.get(`/orders/task/${taskId}`)
    },
    /**
     * POST /v1/orders/accept?taskId={id} — 服务方接单
     * 返回 OrderDTO（含 orderId），需缓存供需求方后续操作使用
     */
    accept(taskId) {
        return http.post('/orders/accept', null, {
            params: {
                taskId
            }
        })
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
