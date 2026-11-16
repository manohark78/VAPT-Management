package com.company.vapt.user.repository;

import com.company.vapt.user.domain.UserScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserScopeRepository extends JpaRepository<UserScope, Long> {

    List<UserScope> findByUserId(Long userId);

    @Query("SELECT s FROM UserScope s JOIN s.user u WHERE u.email = :email")
    List<UserScope> findByUserEmail(@Param("email") String email);

    boolean existsByUserIdAndIpAddress(Long userId, String ipAddress);

    boolean existsByUserIdAndApplicationName(Long userId, String applicationName);

    boolean existsByUserIdAndIpAddressAndApplicationName(Long userId, String ipAddress, String applicationName);

    void deleteByUserId(Long userId);
}
