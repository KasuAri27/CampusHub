package com.campushub.chat.service;

import com.campushub.chat.dto.MessageDTO;
import com.campushub.chat.entity.Message;
import com.campushub.chat.repository.MessageRepository;
import com.campushub.common.BusinessException;
import com.campushub.common.PageResult;
import com.campushub.user.User;
import com.campushub.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository msgRepo;
    private final UserRepository userRepo;

    @Transactional
    public MessageDTO saveAndGet(Long convId, Long senderId, String content, String msgType) {
        Message msg = new Message();
        msg.setConvId(convId);
        msg.setSenderId(senderId);
        msg.setContent(content);
        msg.setMsgType(msgType != null ? msgType : "TEXT");
        msg.setIsRecalled(false); // 新增默认值
        msg = msgRepo.save(msg);

        String senderName = (senderId == 0L) ? "系统通知" : getSenderName(senderId);

        return MessageDTO.builder()
                .id(msg.getId()).convId(msg.getConvId())
                .senderId(msg.getSenderId())
                .senderName(senderName)
                .msgType(msg.getMsgType()).content(msg.getContent())
                .isRecalled(msg.getIsRecalled()) // 返回撤回状态
                .createdAt(msg.getCreatedAt()).build();
    }

    @Transactional(readOnly = true)
    public PageResult<MessageDTO> getHistory(Long convId, int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size);
        Page<Message> msgPage = msgRepo.findByConvIdOrderByCreatedAtDesc(convId, pageable);

        List<MessageDTO> items = msgPage.getContent().stream()
                .map(m -> {
                    String senderName = (m.getSenderId() == 0L) ? "系统通知" : getSenderName(m.getSenderId());
                    return MessageDTO.builder()
                            .id(m.getId()).convId(m.getConvId())
                            .senderId(m.getSenderId())
                            .senderName(senderName)
                            .msgType(m.getMsgType()).content(m.getContent())
                            .isRecalled(m.getIsRecalled()) // 映射撤回状态
                            .createdAt(m.getCreatedAt()).build();
                }).toList();

        return new PageResult<>(items, new PageResult.Pagination(page, size, msgPage.getTotalElements(), msgPage.getTotalPages()));
    }

    @Transactional
    public MessageDTO recallMessage(Long msgId, Long userId) {
        Message msg = msgRepo.findById(msgId)
                .orElseThrow(() -> new BusinessException("消息不存在"));

        if (!msg.getSenderId().equals(userId)) {
            throw new BusinessException("只能撤回自己的消息");
        }

        if (msg.getCreatedAt().plusMinutes(2).isBefore(LocalDateTime.now())) {
            throw new BusinessException("发送时间超过2分钟，无法撤回");
        }

        String senderName = getSenderName(userId);

        // 【关键修改】：绝对不要再修改 setMsgType 和 setContent，保留原文和原类型
        msg.setIsRecalled(true);
        msg = msgRepo.save(msg);

        return MessageDTO.builder()
                .id(msg.getId()).convId(msg.getConvId())
                .senderId(msg.getSenderId())
                .senderName(senderName)
                .msgType(msg.getMsgType()) // 保持原样 (TEXT/IMAGE)
                .content(msg.getContent()) // 保持原文
                .isRecalled(msg.getIsRecalled()) // 设置为 true
                .createdAt(msg.getCreatedAt()).build();
    }

    private String getSenderName(Long userId) {
        User sender = userRepo.findById(userId).orElse(null);
        return sender != null ? (sender.getNickname() != null ? sender.getNickname() : sender.getName()) : "未知";
    }
}
