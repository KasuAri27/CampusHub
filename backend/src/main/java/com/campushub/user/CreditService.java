package com.campushub.user;

import com.campushub.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;

/** 评分、逾期处罚及裁决的信用变更入口，范围统一限制在 0–100。 */
@Service
@RequiredArgsConstructor
public class CreditService {
    private final UserRepository users;
    private final CreditEventRepository events;
    private final Clock clock;

    @Transactional
    public boolean adjust(Long userId, int delta, String eventKey, String reason) {
        User user = users.findLockedById(userId)
                .orElseThrow(() -> new BusinessException(404, "用户不存在"));
        // 获取用户锁后再检查唯一事件，两个并发调用不能同时通过存在性检查。
        var existing = events.findByEventKey(eventKey);
        if (existing.isPresent()) {
            CreditEvent event = existing.get();
            if (!event.getUserId().equals(userId) || event.getRequestedDelta() != delta || !java.util.Objects.equals(event.getReason(), reason))
                throw new BusinessException(409, "相同操作编号不能用于不同信用变更");
            return false;
        }
        int before = user.getCreditScore() == null ? 100 : user.getCreditScore();
        int after = (int) Math.max(0L, Math.min(100L, (long) before + delta));
        user.setCreditScore(after);
        CreditEvent event = new CreditEvent();
        event.setUserId(userId);
        event.setEventKey(eventKey);
        event.setRequestedDelta(delta);
        event.setPreviousScore(before);
        event.setResultingScore(after);
        event.setReason(reason);
        event.setCreatedAt(LocalDateTime.now(clock));
        events.save(event);
        return true;
    }
}
