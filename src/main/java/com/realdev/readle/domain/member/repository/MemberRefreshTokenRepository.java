package com.realdev.readle.domain.member.repository;

import com.realdev.readle.domain.member.entity.MemberRefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRefreshTokenRepository extends JpaRepository<MemberRefreshToken, Long> {

  Optional<MemberRefreshToken> findByTokenHash(String tokenHash);
}
