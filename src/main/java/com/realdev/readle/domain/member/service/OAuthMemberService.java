package com.realdev.readle.domain.member.service;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.repository.MemberRepository;
import com.realdev.readle.global.exception.CustomException;
import com.realdev.readle.global.exception.GlobalErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthMemberService {

  private static final String FALLBACK_NICKNAME = "사용자";

  private final MemberRepository memberRepository;

  public OAuthMemberService(MemberRepository memberRepository) {
    this.memberRepository = memberRepository;
  }

  @Transactional
  public Member upsert(OAuthProfile profile) {
    if (profile == null || profile.provider() == null || isBlank(profile.subject())) {
      throw new CustomException(GlobalErrorCode.OAUTH_AUTHORIZATION_FAILED);
    }

    String nickname = nickname(profile.displayName());
    String email = blankToNull(profile.email());
    return memberRepository
        .findByOauthProviderAndOauthId(profile.provider(), profile.subject())
        .map(
            member -> {
              member.updateLoginProfile(email, nickname, blankToNull(profile.profileImageUrl()));
              return member;
            })
        .orElseGet(
            () ->
                memberRepository.save(
                    Member.create(
                        profile.provider(),
                        profile.subject(),
                        email,
                        nickname,
                        blankToNull(profile.profileImageUrl()))));
  }

  private String nickname(String displayName) {
    if (displayName == null) {
      return FALLBACK_NICKNAME;
    }
    String stripped = displayName.strip();
    return stripped.isEmpty() || stripped.length() > 30 ? FALLBACK_NICKNAME : stripped;
  }

  private String blankToNull(String value) {
    return isBlank(value) ? null : value;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
