package com.campushub.chat.repository;

import com.campushub.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("SELECT c FROM Conversation c WHERE c.id IN " +
           "(SELECT m.convId FROM ConversationMember m WHERE m.userId = :userId) " +
           "ORDER BY c.updatedAt DESC")
    List<Conversation> findByUserId(@Param("userId") Long userId);

    Optional<Conversation> findByTaskIdAndConvType(Long taskId, String convType);

    Optional<Conversation> findByTaskIdAndOrderIdAndConvType(Long taskId, Long orderId, String convType);

    List<Conversation> findByConvType(String convType);
}
