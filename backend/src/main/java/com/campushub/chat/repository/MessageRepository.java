package com.campushub.chat.repository;

import com.campushub.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConvIdOrderByCreatedAtDesc(Long convId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.convId = :convId AND m.id = " +
           "(SELECT MAX(m2.id) FROM Message m2 WHERE m2.convId = :convId)")
    Message findLastMessage(@Param("convId") Long convId);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.convId = :convId AND m.senderId <> :userId AND m.createdAt > :since")
    long countUnreadSince(@Param("convId") Long convId,
                          @Param("userId") Long userId,
                          @Param("since") LocalDateTime since);
}
