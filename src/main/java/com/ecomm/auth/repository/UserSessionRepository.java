package com.ecomm.auth.repository;

import com.ecomm.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    Optional<UserSession> findByTokenId(String tokenId);

    List<UserSession> findByFamilyId(String familyId);

    @Modifying
    @Query("UPDATE UserSession s SET s.revoked = true WHERE s.familyId = :familyId")
    void revokeFamily(@Param("familyId") String familyId);

    @Modifying
    @Query("UPDATE UserSession s SET s.revoked = true WHERE s.user.id = :userId")
    void revokeAllForUser(@Param("userId") Long userId);
}

