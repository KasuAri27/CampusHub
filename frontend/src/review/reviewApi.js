import http from '@/common/http'

export default {
    /**
     * POST /v1/reviews/submit — 提交评价
     * 对应后端 ReviewController 的 submitReview 接口
     */
    submit(orderId, score, comment) {
        return http.post('/reviews/submit', null, {
            params: {
                orderId,
                score,
                comment
            }
        })
    },
    getByOrderId(orderId) {
        return http.get(`/reviews/order/${orderId}`)
    }
}