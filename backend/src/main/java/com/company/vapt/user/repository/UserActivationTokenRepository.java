package com.company.vapt.user.repository;

import com.company.vapt.user.entity.UserActivationToken;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface UserActivationTokenRepository extends JpaRepository<UserActivationToken, UUID> {
    @Modifying
    @Query("update UserActivationToken token set token.usedAt = :now where token.user.id = :userId and token.usedAt is null")
    int invalidateOutstanding(@Param("userId") Long userId, @Param("now") java.time.Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from UserActivationToken token where token.tokenHash = :hash")
    Optional<UserActivationToken> findByTokenHashForUpdate(@Param("hash") String hash);
}
