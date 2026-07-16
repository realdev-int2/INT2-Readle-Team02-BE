package com.realdev.readle.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.domain.member.service.OAuthMemberService;
import com.realdev.readle.domain.member.service.OAuthProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthLoginServiceTest {

  @Mock private OAuthMemberService memberService;
  @Mock private RefreshTokenService refreshTokenService;

  @InjectMocks private OAuthLoginService loginService;

  @Test
  void issuesRefreshTokenAfterUpsertingMember() {
    OAuthProfile profile = new OAuthProfile(OAuthProvider.GOOGLE, "subject", null, "Readler", null);
    Member member = Member.create(OAuthProvider.GOOGLE, "subject", null, "Readler", null);

    when(memberService.upsert(profile)).thenReturn(member);
    when(refreshTokenService.issue(member)).thenReturn("refresh-token");

    String refreshToken = loginService.login(profile);

    InOrder ordered = inOrder(memberService, refreshTokenService);
    ordered.verify(memberService).upsert(profile);
    ordered.verify(refreshTokenService).issue(member);

    assertThat(refreshToken).isEqualTo("refresh-token");
  }

  @Test
  void doesNotIssueRefreshTokenWhenUpsertFails() {
    OAuthProfile profile = new OAuthProfile(OAuthProvider.GOOGLE, "subject", null, "Readler", null);
    RuntimeException upsertFailure = new RuntimeException("member upsert failed");

    when(memberService.upsert(profile)).thenThrow(upsertFailure);

    assertThatThrownBy(() -> loginService.login(profile)).isSameAs(upsertFailure);

    verifyNoInteractions(refreshTokenService);
    verify(memberService).upsert(profile);
  }
}
