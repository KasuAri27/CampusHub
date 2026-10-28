import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useUserStore } from '@/user/userStore'
import chatApi from '@/chat/chatApi'

export const useChatStore = defineStore('chat', () => {
    const conversations = ref([])
    const messages = ref({})
    const unreadTotal = ref(0)
    const ws = ref(null)
    const wsConnected = ref(false)
    let reconnectTimer = null
    let reconnectCount = 0

    async function fetchConversations() {
        try {
            const res = await chatApi.listConversations()
            conversations.value = res.data.data || []
            unreadTotal.value = conversations.value.reduce((sum, c) => sum + (c.unreadCount || 0), 0)
        } catch { /* ignore */ }
    }

    async function fetchMessages(convId, page = 1) {
        try {
            const res = await chatApi.getMessages(convId, page)
            const data = res.data.data
            const list = (data.items || []).reverse()
            if (page === 1) {
                messages.value[convId] = list
            } else {
                const old = messages.value[convId] || []
                messages.value[convId] = [...list, ...old]
            }
            messages.value = { ...messages.value }
            return data.pagination
        } catch { return null }
    }

    async function markConversationRead(convId) {
        try {
            await chatApi.markRead(convId)
            await fetchConversations()
        } catch { /* ignore */ }
    }

    function connectWebSocket() {
        const userStore = useUserStore()
        if (!userStore.token) return
        if (ws.value && ws.value.readyState === WebSocket.OPEN) return

        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
        const url = `${protocol}//${location.host}/api/ws/chat?token=${userStore.token}`
        const socket = new WebSocket(url)
        ws.value = socket

        socket.onopen = () => {
            wsConnected.value = true
            reconnectCount = 0
            setInterval(() => {
                if (socket.readyState === WebSocket.OPEN) {
                    socket.send(JSON.stringify({ type: 'PING' }))
                }
            }, 30000)
        }

        socket.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data)
                if (data.type === 'PONG') return

                if (data.type === 'MSG') {
                    addMessage(data.convId, data.message)
                }
                else if (data.type === 'RECALL') {
                    // 【核心修复一：同时兼容后端传实体对象或只传 msgId 的场景】
                    if (data.message) {
                        addMessage(data.convId, data.message)
                    } else {
                        const targetMsgId = data.msgId || data.messageId
                        if (targetMsgId) {
                            const msgs = messages.value[data.convId]
                            if (msgs) {
                                const idx = msgs.findIndex(m => m.id === targetMsgId)
                                if (idx !== -1) {
                                    // 原地将消息类型和内容变更为撤回状态
                                    // 修改后：只改变撤回状态，绝对不要动原文和原类型
                                    msgs[idx].isRecalled = true

                                    // 断开引用触发深层响应式更新
                                    messages.value[data.convId] = [...msgs]
                                    messages.value = { ...messages.value }
                                }
                            }
                            fetchConversations()
                        }
                    }
                }
                else if (data.type === 'ERROR') {
                    import('element-plus').then(({ ElMessage }) => {
                        ElMessage.error(data.message)
                    })
                }
            } catch (err) {
                console.error('WS Error:', err)
            }
        }

        socket.onclose = () => {
            wsConnected.value = false
            scheduleReconnect()
        }

        socket.onerror = () => {
            wsConnected.value = false
        }
    }

    function scheduleReconnect() {
        if (reconnectCount >= 5) return
        clearTimeout(reconnectTimer)
        const delay = Math.min(1000 * Math.pow(2, reconnectCount), 16000)
        reconnectTimer = setTimeout(() => {
            reconnectCount++
            connectWebSocket()
        }, delay)
    }

    function addMessage(convId, msg) {
        if (!msg) return
        if (!messages.value[convId]) {
            messages.value[convId] = []
        }
        const msgs = messages.value[convId]
        const idx = msgs.findIndex(m => m.id === msg.id)

        if (idx !== -1) {
            msgs.splice(idx, 1, msg)
        } else {
            msgs.push(msg)
        }

        messages.value[convId] = [...msgs]
        messages.value = { ...messages.value }
        fetchConversations()
    }

    async function sendMessage(convId, content, msgType = 'TEXT') {
        const userStore = useUserStore()
        const tempId = 'temp_' + Date.now()
        const optimisticMsg = {
            id: tempId, convId, senderId: userStore.userId,
            senderName: userStore.nickname || '我',
            msgType: msgType, content, createdAt: new Date().toISOString()
        }

        addMessage(convId, optimisticMsg)

        try {
            const res = await chatApi.sendMessage(convId, content, msgType)
            if (res.data?.code === 200 && res.data.data) {
                const realMsg = res.data.data
                const msgs = messages.value[convId]
                if (msgs) {
                    const tempIdx = msgs.findIndex(m => m.id === tempId)
                    if (tempIdx >= 0) msgs.splice(tempIdx, 1)
                    addMessage(convId, realMsg)
                }
            } else {
                removeTempMsg(convId, tempId)
            }
        } catch {
            removeTempMsg(convId, tempId)
        }
    }

    function removeTempMsg(convId, msgId) {
        const msgs = messages.value[convId]
        if (msgs) {
            const idx = msgs.findIndex(m => m.id === msgId)
            if (idx >= 0) {
                msgs.splice(idx, 1)
                messages.value[convId] = [...msgs]
                messages.value = { ...messages.value }
            }
        }
    }

    function disconnect() {
        clearTimeout(reconnectTimer)
        if (ws.value) {
            ws.value.close()
            ws.value = null
        }
        wsConnected.value = false
    }

    // 【核心修复二：发送方撤回增加乐观更新机制】
    function recallMessage(msg) {
        if (ws.value && ws.value.readyState === WebSocket.OPEN) {
            // 1. 发送撤回消息给后端
            ws.value.send(JSON.stringify({
                type: 'RECALL',
                msgId: msg.id,
                convId: msg.convId
            }))

            // 2. 本地直接先执行状态转换，实现秒级响应
            const msgs = messages.value[msg.convId]
            if (msgs) {
                const idx = msgs.findIndex(m => m.id === msg.id)
                if (idx !== -1) {
                    // 修改后：只更新撤回标识
                    msgs[idx].isRecalled = true
                    messages.value[msg.convId] = [...msgs]
                    messages.value = { ...messages.value }
                }
            }
            fetchConversations()
        } else {
            import('element-plus').then(({ ElMessage }) => {
                ElMessage.error('网络未连接，撤回失败')
            })
        }
    }

    return { conversations, messages, unreadTotal, wsConnected,
        fetchConversations, fetchMessages, markConversationRead, connectWebSocket, sendMessage, disconnect, recallMessage }
})