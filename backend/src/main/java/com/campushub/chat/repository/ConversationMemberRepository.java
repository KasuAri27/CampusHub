package com.campushub.chat.repository;

import com.campushub.chat.entity.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    List<ConversationMember> findByConvId(Long convId);

    @Query("SELECT m.userId FROM ConversationMember m WHERE m.convId = :convId")
    List<Long> findUserIdsByConvId(@Param("convId") Long convId);

    ConversationMember findByConvIdAndUserId(Long convId, Long userId);

    long countByConvId(Long convId);

    void deleteByConvIdAndUserId(Long convId, Long userId);

    boolean existsByConvIdAndUserId(Long convId, Long userId);
}
