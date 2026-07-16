package com.realdev.readle.domain.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.realdev.readle.domain.member.entity.Member;
import com.realdev.readle.domain.member.entity.OAuthProvider;
import com.realdev.readle.domain.member.repository.MemberRepository;
import com.realdev.readle.global.exception.CustomException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OAuthMemberServiceTest {

  @Mock private MemberRepository memberRepository;

  @InjectMocks private OAuthMemberService oauthMemberService;

  @Test
  void createsMemberWithPresentEmailAndTrimmedValidDisplayName() {
    when(memberRepository.findByOauthProviderAndOauthId(OAuthProvider.GOOGLE, "subject"))
        .thenReturn(Optional.empty());
    when(memberRepository.save(any(Member.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    oauthMemberService.upsert(
        new OAuthProfile(
            OAuthProvider.GOOGLE, "subject", "member@example.com", "  Readler  ", null));

    ArgumentCaptor<Member> member = ArgumentCaptor.forClass(Member.class);
    verify(memberRepository).save(member.capture());
    assertThat(member.getValue().getEmail()).isEqualTo("member@example.com");
    assertThat(member.getValue().getNickname()).isEqualTo("Readler");
  }

  @Test
  void createsMemberWithNullEmailAndValidDisplayName() {
    when(memberRepository.findByOauthProviderAndOauthId(OAuthProvider.GOOGLE, "subject"))
        .thenReturn(Optional.empty());
    when(memberRepository.save(any(Member.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    oauthMemberService.upsert(
        new OAuthProfile(OAuthProvider.GOOGLE, "subject", null, "Readler", null));

    ArgumentCaptor<Member> member = ArgumentCaptor.forClass(Member.class);
    verify(memberRepository).save(member.capture());
    assertThat(member.getValue().getEmail()).isNull();
    assertThat(member.getValue().getNickname()).isEqualTo("Readler");
  }

  @Test
  void usesFallbackNicknameForBlankDisplayName() {
    assertThat(savedMemberForDisplayName("").getNickname()).isEqualTo("사용자");
  }

  @Test
  void usesFallbackNicknameForWhitespaceOnlyDisplayName() {
    assertThat(savedMemberForDisplayName(" \t\n ").getNickname()).isEqualTo("사용자");
  }

  @Test
  void usesFallbackNicknameForDisplayNameLongerThanThirtyCharacters() {
    assertThat(savedMemberForDisplayName("a".repeat(31)).getNickname()).isEqualTo("사용자");
  }

  @Test
  void permitsNicknameCollisions() {
    when(memberRepository.findByOauthProviderAndOauthId(any(), any())).thenReturn(Optional.empty());
    when(memberRepository.save(any(Member.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Member first =
        oauthMemberService.upsert(
            new OAuthProfile(OAuthProvider.GOOGLE, "first", null, "  Readler  ", null));
    Member second =
        oauthMemberService.upsert(
            new OAuthProfile(OAuthProvider.KAKAO, "second", null, "Readler", null));

    assertThat(first.getNickname()).isEqualTo("Readler");
    assertThat(second.getNickname()).isEqualTo("Readler");
    assertThat(first.getOauthProvider()).isNotEqualTo(second.getOauthProvider());
    assertThat(first.getOauthId()).isNotEqualTo(second.getOauthId());
  }

  @Test
  void rejectsMissingOAuthSubjectWithoutAccessingRepository() {
    assertThatThrownBy(
            () ->
                oauthMemberService.upsert(
                    new OAuthProfile(OAuthProvider.GOOGLE, null, null, "Readler", null)))
        .isInstanceOf(CustomException.class);

    verifyNoInteractions(memberRepository);
  }

  @Test
  void rejectsBlankOAuthSubjectWithoutAccessingRepository() {
    assertThatThrownBy(
            () ->
                oauthMemberService.upsert(
                    new OAuthProfile(OAuthProvider.GOOGLE, " \t\n ", null, "Readler", null)))
        .isInstanceOf(CustomException.class);

    verifyNoInteractions(memberRepository);
  }

  private Member savedMemberForDisplayName(String displayName) {
    when(memberRepository.findByOauthProviderAndOauthId(OAuthProvider.GOOGLE, "subject"))
        .thenReturn(Optional.empty());
    when(memberRepository.save(any(Member.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    return oauthMemberService.upsert(
        new OAuthProfile(OAuthProvider.GOOGLE, "subject", null, displayName, null));
  }
}
