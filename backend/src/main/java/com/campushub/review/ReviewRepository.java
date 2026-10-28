package com.campushub.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByOrder_Id(Long orderId);
    Optional<Review> findByOrder_Id(Long orderId);
    void deleteByOrder_Id(Long orderId);
}
