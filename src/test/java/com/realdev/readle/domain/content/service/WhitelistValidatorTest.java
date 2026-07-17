package com.realdev.readle.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

import com.realdev.readle.domain.content.config.ContentValidationProperties;
import com.realdev.readle.domain.content.entity.Content;
import com.realdev.readle.domain.content.entity.CrawlStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WhitelistValidatorTest {

  @Mock private ContentValidationProperties properties;

  @InjectMocks private WhitelistValidator whitelistValidator;

  @BeforeEach
  void setUp() {
    lenient()
        .when(properties.whitelistDomains())
        .thenReturn(
            List.of(
                "techblog.woowahan.com", "toss.tech", "engineering.linecorp.com", "d2.naver.com"));
  }

  @Test
  @DisplayName("도메인이 정확히 일치하면 화이트리스트 검증을 통과한다")
  void isEligibleForWhitelist_exactDomainMatch_returnsTrue() {
    Content content = mockContentWithUrl("https://techblog.woowahan.com/path", CrawlStatus.SUCCESS);
    assertThat(whitelistValidator.isEligibleForWhitelist(content)).isTrue();
  }

  @Test
  @DisplayName("서브도메인(.domain)이 일치하면 화이트리스트 검증을 통과한다")
  void isEligibleForWhitelist_subdomainMatch_returnsTrue() {
    Content content = mockContentWithUrl("https://blog.toss.tech/path", CrawlStatus.SUCCESS);
    assertThat(whitelistValidator.isEligibleForWhitelist(content)).isTrue();
  }

  @Test
  @DisplayName("도메인 접미사만 일치하고 .으로 구분되지 않으면(우회 시도) 화이트리스트 검증을 통과하지 못한다")
  void isEligibleForWhitelist_subdomainBypassAttempt_returnsFalse() {
    Content content = mockContentWithUrl("https://faketoss.tech/path", CrawlStatus.SUCCESS);
    assertThat(whitelistValidator.isEligibleForWhitelist(content)).isFalse();
  }

  @Test
  @DisplayName("CrawlStatus가 SUCCESS가 아니면 화이트리스트 검증을 통과하지 못한다")
  void isEligibleForWhitelist_notSuccessCrawlStatus_returnsFalse() {
    Content content = mockContentWithUrl("https://d2.naver.com/path", CrawlStatus.FAILED);
    assertThat(whitelistValidator.isEligibleForWhitelist(content)).isFalse();
  }

  @Test
  @DisplayName("malformed URL인 경우 화이트리스트 검증을 통과하지 못한다")
  void isEligibleForWhitelist_malformedUrl_returnsFalse() {
    Content content = mockContentWithUrl("not_a_valid_url", CrawlStatus.SUCCESS);
    assertThat(whitelistValidator.isEligibleForWhitelist(content)).isFalse();
  }

  @Test
  @DisplayName("host가 null인 경우 화이트리스트 검증을 통과하지 못한다")
  void isEligibleForWhitelist_nullHost_returnsFalse() {
    Content content = mockContentWithUrl("file:///path/to/file", CrawlStatus.SUCCESS);
    assertThat(whitelistValidator.isEligibleForWhitelist(content)).isFalse();
  }

  @Test
  @DisplayName("URL이 null인 경우 화이트리스트 검증을 통과하지 못한다")
  void isEligibleForWhitelist_nullUrl_returnsFalse() {
    Content content = mockContentWithUrl(null, CrawlStatus.SUCCESS);
    assertThat(whitelistValidator.isEligibleForWhitelist(content)).isFalse();
  }

  @Test
  @DisplayName("URL이 공백인 경우 화이트리스트 검증을 통과하지 못한다")
  void isEligibleForWhitelist_blankUrl_returnsFalse() {
    Content content = mockContentWithUrl("   ", CrawlStatus.SUCCESS);
    assertThat(whitelistValidator.isEligibleForWhitelist(content)).isFalse();
  }

  private Content mockContentWithUrl(String url, CrawlStatus status) {
    Content content = org.mockito.Mockito.mock(Content.class);
    lenient().when(content.getOriginalUrl()).thenReturn(url);
    lenient().when(content.getCrawlStatus()).thenReturn(status);
    return content;
  }
}
