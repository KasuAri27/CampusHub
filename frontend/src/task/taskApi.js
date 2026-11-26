import http from '@/common/http'

/**
 * 需求模块 API 封装
 */
export default {
  list(params) {
    return http.get('/tasks', { params })
  },

  minePublished(params) {
    return http.get('/tasks/mine/published', { params })
  },

  getById(id) {
    return http.get(`/tasks/${id}`)
  },

  create(data) {
    return http.post('/tasks', data)
  },

  /** 修改并重新提交（API 契约 3.6）：仅待审核或被驳回的需求，成功后回到待审核 */
  update(id, data) {
    return http.put(`/tasks/${id}`, data)
  },

  deleteTask(id) {
    return http.delete(`/tasks/${id}`)
  }
}
