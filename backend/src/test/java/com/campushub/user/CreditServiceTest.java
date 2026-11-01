package com.campushub.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.Optional;
import static com.campushub.support.BusinessFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 直接验证信用上下限及重复事件，不用调用次数替代分数结果。 */
class CreditServiceTest {
    @ParameterizedTest
    @CsvSource({"99,1,100", "100,1,100", "100,-1,99", "0,-1,0", "7,-10,0", "90,-10,80", "66,0,66"})
    void clampsCreditAndKeepsAudit(int before, int delta, int expected) {
        UserRepository users = mock(UserRepository.class);
        CreditEventRepository events = mock(CreditEventRepository.class);
        User user = user(1, User.Role.requester); user.setCreditScore(before);
        when(users.findLockedById(1L)).thenReturn(Optional.of(user));
        CreditService service = new CreditService(users, events, CLOCK);
        assertThat(service.adjust(1L, delta, "test-event", "测试" )).isTrue();
        assertThat(user.getCreditScore()).isEqualTo(expected);
        var captor = org.mockito.ArgumentCaptor.forClass(CreditEvent.class);
        verify(events).save(captor.capture());
        assertThat(captor.getValue().getPreviousScore()).isEqualTo(before);
        assertThat(captor.getValue().getResultingScore()).isEqualTo(expected);
    }
    @Test void duplicateEventCannotDeductAgainOrChangePayload() {
        UserRepository users = mock(UserRepository.class); CreditEventRepository events = mock(CreditEventRepository.class);
        User user = user(1, User.Role.requester); user.setCreditScore(90);
        CreditEvent recorded = new CreditEvent(); recorded.setUserId(1L); recorded.setRequestedDelta(-10); recorded.setReason("逾期");
        when(users.findLockedById(1L)).thenReturn(Optional.of(user)); when(events.findByEventKey("overdue:20")).thenReturn(Optional.of(recorded));
        CreditService service = new CreditService(users, events, CLOCK);
        assertThat(service.adjust(1L, -10, "overdue:20", "逾期")).isFalse();
        assertThat(user.getCreditScore()).isEqualTo(90);
        assertThatThrownBy(() -> service.adjust(1L, -20, "overdue:20", "逾期")).isInstanceOf(com.campushub.common.BusinessException.class);
        verify(events, never()).save(any());
    }
}
