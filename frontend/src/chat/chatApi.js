import http from '@/common/http'

export default {
  listConversations() {
    return http.get('/conversations')
  },
  getConversation(id) {
    return http.get(`/conversations/${id}`)
  },
  getMessages(convId, page = 1, size = 30) {
    return http.get(`/conversations/${convId}/messages`, { params: { page, size } })
  },
  markRead(convId) {
    return http.put(`/conversations/${convId}/read`)
  },
  sendMessage(convId, content, msgType = 'TEXT') {
    return http.post(`/conversations/${convId}/messages`, { content, msgType })
  },
  keepGroup(convId) {
    return http.post(`/conversations/${convId}/keep`)
  },
  dismissGroup(convId) {
    return http.delete(`/conversations/${convId}`)
  }
}
