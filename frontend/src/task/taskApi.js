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

  deleteTask(id) {
    return http.delete(`/tasks/${id}`)
  }
}
