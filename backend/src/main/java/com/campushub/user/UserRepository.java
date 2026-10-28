package com.campushub.user;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问层接口.
 * 继承 {@link JpaRepository} 获得基础 CRUD 能力。
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>,
        JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);
    Optional<User> findByStudentId(String studentId);
    Optional<User> findByPhone(String phoneEncrypted);

    boolean existsByEmail(String email);
    boolean existsByStudentId(String studentId);
    boolean existsByPhone(String phoneEncrypted);

    @Query("SELECT u FROM User u WHERE u.studentId = :account")
    Optional<User> findByStudentIdOnly(@Param("account") String account);

    @Modifying
    @Query("UPDATE User u SET u.creditScore = u.creditScore + 1 WHERE u.creditScore < 70")
    int recoverCreditScore();
}
