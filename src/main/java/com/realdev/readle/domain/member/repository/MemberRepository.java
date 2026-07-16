package com.realdev.readle.domain.member.repository;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.entity.OAuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

  Optional<Member> findByOauthProviderAndOauthId(OAuthProvider oauthProvider, String oauthId);

  Optional<Member> findByUuid(String uuid);
}
