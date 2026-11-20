import http from '@/common/http'

/** 管理后台 API 封装，对应后端 AdminController（/v1/admin/**）。 */
export default {
  /** 仪表盘统计 */
  getStats() { return http.get('/admin/stats') },

  /** 用户列表：keyword 匹配学号、邮箱、姓名；role 取 user / admin */
  listUsers(params) { return http.get('/admin/users', { params }) },

  /** 启用/禁用用户（切换 status 1 ↔ 0） */
  toggleUserStatus(id) { return http.put(`/admin/users/${id}/status`) },

  /**
   * 调整信用分。operationId 为客户端操作编号：同一次调整的重试必须复用同一编号，
   * 后端据此去重，网络超时后重试不会重复扣分或重复通知（模块契约 §3.2）。
   */
  adjustCredit(id, delta, operationId) { return http.put(`/admin/users/${id}/credit`, { delta, operationId }) },

  /** 全部需求列表：status 状态筛选，keyword 标题关键词 */
  listTasks(params) { return http.get('/admin/tasks', { params }) },

  /** 删除需求（仅允许没有有效付款或争议订单的需求） */
  deleteTask(id) { return http.delete(`/admin/tasks/${id}`) },

  /** 审核通过需求 */
  approveTask(id) { return http.put(`/admin/tasks/${id}/approve`) },

  /** 驳回需求（可选驳回理由，随系统通知发给发布者） */
  rejectTask(id, reason) { return http.put(`/admin/tasks/${id}/reject`, { reason }) },

  /** 分类字典 */
  listCategories() { return http.get('/admin/categories') },
  createCategory(data) { return http.post('/admin/categories', data) },
  updateCategory(id, data) { return http.put(`/admin/categories/${id}`, data) },
  deleteCategory(id) { return http.delete(`/admin/categories/${id}`) },

  /** 系统通知：列表为全平台通知记录；发送时 receiverId 为空表示向全体用户广播 */
  listNotifications(params) { return http.get('/admin/notifications', { params }) },
  sendNotification(data) { return http.post('/admin/notifications', data) },

  /** 会话列表 */
  listConversations(params) { return http.get('/admin/conversations', { params }) },

  /** 会话消息列表 */
  getConversationMessages(id, params) { return http.get(`/admin/conversations/${id}/messages`, { params }) }
}
