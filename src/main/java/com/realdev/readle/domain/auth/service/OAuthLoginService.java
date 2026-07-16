package com.realdev.readle.domain.auth.service;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.service.OAuthMemberService;
import com.realdev.readle.domain.member.service.OAuthProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OAuthLoginService {

  private final OAuthMemberService memberService;
  private final RefreshTokenService refreshTokenService;

  public OAuthLoginService(
      OAuthMemberService memberService, RefreshTokenService refreshTokenService) {
    this.memberService = memberService;
    this.refreshTokenService = refreshTokenService;
  }

  @Transactional
  public String login(OAuthProfile profile) {
    Member member = memberService.upsert(profile);
    return refreshTokenService.issue(member);
  }
}
