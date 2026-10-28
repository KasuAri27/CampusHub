import http from '@/common/http'

export default {
  /** 仪表盘统计 */
  getStats() { return http.get('/admin/stats') },

  /** 用户列表 */
  listUsers(params) { return http.get('/admin/users', { params }) },

  /** 启用/禁用用户 */
  toggleUserStatus(id) { return http.put(`/admin/users/${id}/status`) },

  /** 调整信用分 */
  adjustCredit(id, delta) { return http.put(`/admin/users/${id}/credit`, { delta }) },

  /** 全部需求列表 */
  listTasks(params) { return http.get('/admin/tasks', { params }) },

  /** 强制删除需求 */
  deleteTask(id) { return http.delete(`/admin/tasks/${id}`) },

  /** 审核通过需求 */
  approveTask(id) { return http.put(`/admin/tasks/${id}/approve`) },

  /** 驳回需求（可选驳回理由） */
  rejectTask(id, reason) { return http.put(`/admin/tasks/${id}/reject`, { reason }) },

  /** 会话列表 */
  listConversations(params) { return http.get('/admin/conversations', { params }) },

  /** 会话消息列表 */
  getConversationMessages(id, params) { return http.get(`/admin/conversations/${id}/messages`, { params }) }
}
