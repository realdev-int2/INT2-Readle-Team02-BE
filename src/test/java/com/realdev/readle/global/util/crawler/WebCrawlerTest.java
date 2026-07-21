package com.realdev.readle.global.util.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.realdev.readle.domain.content.exception.ContentErrorCode;
import com.realdev.readle.global.exception.CustomException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class WebCrawlerTest {

  private final WebCrawler webCrawler = new WebCrawler();

  @Test
  @DisplayName("og:title이 존재하면 최우선으로 제목을 추출한다")
  void extractTitleOgTitleFirst() {
    String html =
        "<html><head><meta property=\"og:title\" content=\"오픈그래프 제목\"><title>기본 타이틀</title></head><body>본문</body></html>";
    Document doc = Jsoup.parse(html);

    WebCrawler.CrawledDocument result = webCrawler.parse(doc);
    assertThat(result.title()).isEqualTo("오픈그래프 제목");
  }

  @Test
  @DisplayName("og:title이 없고 title 태그만 존재하면 2순위로 제목을 추출한다")
  void extractTitleTagSecond() {
    String html = "<html><head><title>기본 타이틀</title></head><body>본문</body></html>";
    Document doc = Jsoup.parse(html);

    WebCrawler.CrawledDocument result = webCrawler.parse(doc);
    assertThat(result.title()).isEqualTo("기본 타이틀");
  }

  @Test
  @DisplayName("og:title 메타 태그가 존재하더라도 content 속성이 비어있으면 2순위인 title 태그 값을 제목으로 추출한다")
  void extractTitleOgTitleEmptyContentFallback() {
    String html =
        "<html><head><meta property=\"og:title\" content=\"   \"><title>기본 타이틀</title></head><body>본문</body></html>";
    Document doc = Jsoup.parse(html);

    WebCrawler.CrawledDocument result = webCrawler.parse(doc);
    assertThat(result.title()).isEqualTo("기본 타이틀");
  }

  @Test
  @DisplayName("모든 제목 정보가 유실되면 '제목 없음'을 반환한다")
  void extractTitleFallback() {
    String html = "<html><body>본문</body></html>";
    Document doc = Jsoup.parse(html);

    WebCrawler.CrawledDocument result = webCrawler.parse(doc);
    assertThat(result.title()).isEqualTo("제목 없음");
  }

  @Test
  @DisplayName("노이즈 태그(script, style 등)가 완벽히 소거되며 40% 이상의 토큰 절감을 보여준다")
  void promptCompressionCheck() {
    String rawHtml =
        "<html><head><style>body { background: #fff; }</style></head>"
            + "<body>"
            + "<header><nav><ul><li>메뉴</li></ul></nav></header>"
            + "<h1>실제 제목</h1>"
            + "<p>본문 내용입니다.</p>"
            + "<script>alert('noise');</script>"
            + "<footer>하단 카피라이트</footer>"
            + "</body></html>";

    Document doc = Jsoup.parse(rawHtml);
    String compressedMarkdown = webCrawler.parse(doc).content();

    // 1. 노이즈 태그 및 헤더/푸터가 제거되었는지 검증
    assertThat(compressedMarkdown).doesNotContain("background");
    assertThat(compressedMarkdown).doesNotContain("noise");
    assertThat(compressedMarkdown).doesNotContain("하단 카피라이트");

    // 2. 마크다운 변환 확인
    assertThat(compressedMarkdown).contains("# 실제 제목");

    // 3. 압축률 검증 (HTML 소스 대비 마크다운 압축률)
    double reductionRate = 1.0 - ((double) compressedMarkdown.length() / rawHtml.length());
    assertThat(reductionRate).isGreaterThan(0.40);
  }

  @Test
  @DisplayName("article 태그가 존재하면 main 이나 body의 다른 영역을 제외하고 article 태그의 본문만 추출한다")
  void extractBodyArticlePriority() {
    String html =
        "<html><body><main><p>메인 영역 외 노이즈</p><article><p>아티클 본문</p></article></main></body></html>";
    Document doc = Jsoup.parse(html);

    String content = webCrawler.parse(doc).content();
    assertThat(content).isEqualTo("아티클 본문");
  }

  @Test
  @DisplayName("article 태그가 없고 main 태그가 존재하면 main 태그의 본문만 추출한다")
  void extractBodyMainPriority() {
    String html = "<html><body><p>바디 영역 외 노이즈</p><main><p>메인 본문</p></main></body></html>";
    Document doc = Jsoup.parse(html);

    String content = webCrawler.parse(doc).content();
    assertThat(content).isEqualTo("메인 본문");
  }

  @Test
  @DisplayName("article과 main 태그가 모두 없으면 body 전체를 본문으로 판단하여 추출한다")
  void extractBodyFallbackToBody() {
    String html = "<html><body><p>전체 바디 본문</p></body></html>";
    Document doc = Jsoup.parse(html);

    String content = webCrawler.parse(doc).content();
    assertThat(content).isEqualTo("전체 바디 본문");
  }

  @Test
  @DisplayName("a 태그의 링크 URL은 제거되고 내부 텍스트만 보존한다")
  void stripLinkUrlsKeepText() {
    String html =
        "<html><body><p>이것은 <a href=\"https://google.com\">구글</a> 링크입니다.</p></body></html>";
    Document doc = Jsoup.parse(html);

    String content = webCrawler.parse(doc).content();
    assertThat(content).isEqualTo("이것은 구글 링크입니다.");
  }

  @Test
  @DisplayName("리스트 태그(li)가 마크다운 리스트 포맷(- )으로 변환된다")
  void convertListToMarkdown() {
    String html = "<html><body><ul><li>사과</li><li>바나나</li></ul></body></html>";
    Document doc = Jsoup.parse(html);

    String content = webCrawler.parse(doc).content();
    assertThat(content).isEqualTo("- 사과\n- 바나나");
  }

  @Test
  @DisplayName("HTML 본문이 비어있을 경우 예외 없이 빈 문자열을 반환한다")
  void emptyHtmlReturnsEmptyString() {
    String html = "<html><head></head><body></body></html>";
    Document doc = Jsoup.parse(html);

    WebCrawler.CrawledDocument result = webCrawler.parse(doc);
    assertThat(result.content()).isEmpty();
  }

  @Test
  @DisplayName("잘못된 URL 요청 시 CustomException 예외가 전파된다")
  void invalidUrlException() {
    assertThatThrownBy(() -> webCrawler.crawl("invalid-url-scheme"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_URL);
  }

  @Test
  @DisplayName("동시성 테스트: 여러 스레드에서 크롤링 요청이 들어와도 상호 간섭없이 스레드 안전하게 동작한다")
  void concurrencyTest() throws InterruptedException {
    int numberOfThreads = 10;
    ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
    CountDownLatch latch = new CountDownLatch(numberOfThreads);
    AtomicInteger successCount = new AtomicInteger(0);

    String html = "<html><head><title>동시성 테스트</title></head><body>본문 텍스트</body></html>";
    Document doc = Jsoup.parse(html);

    for (int i = 0; i < numberOfThreads; i++) {
      service.submit(
          () -> {
            try {
              WebCrawler.CrawledDocument result =
                  webCrawler.parse(doc); // parse 또는 local mock 문서 파싱
              // 여기서는 thread-safety 검증을 위해 동일 문서에 대해 각 스레드가 독립된 결과를 반환받는지 확인
              WebCrawler.CrawledDocument localResult = webCrawler.parse(doc);
              if (localResult.title().equals("동시성 테스트")
                  && localResult.content().contains("본문 텍스트")) {
                successCount.incrementAndGet();
              }
            } finally {
              latch.countDown();
            }
          });
    }

    latch.await();
    service.shutdown();

    assertThat(successCount.get()).isEqualTo(numberOfThreads);
  }

  @Test
  @DisplayName("SSRF 공격 시도(루프백, 사설IP, 링크로컬 대역) 발생 시 CustomException 예외가 전파된다")
  void ssrfBlockException() {
    // 127.0.0.1 대역 차단 검증
    assertThatThrownBy(() -> webCrawler.crawl("http://127.0.0.1:8080/admin"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_URL);

    // 192.168.x.x 사설 대역 차단 검증
    assertThatThrownBy(() -> webCrawler.crawl("http://192.168.0.1/status"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_URL);

    // AWS 인스턴스 메타데이터 API 대역 차단 검증
    assertThatThrownBy(() -> webCrawler.crawl("http://169.254.169.254/latest/meta-data"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_URL);

    // IPv4-Mapped IPv6 루프백 차단 검증
    assertThatThrownBy(() -> webCrawler.crawl("http://[::ffff:127.0.0.1]/status"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_URL);

    // IPv4-Mapped IPv6 사설망 차단 검증
    assertThatThrownBy(() -> webCrawler.crawl("http://[::ffff:192.168.0.1]/status"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_URL);
  }

  @Test
  @DisplayName("공개 URL에서 사설 IP로의 리다이렉트 발생 시 CustomException(INVALID_URL)이 발생한다")
  void redirectToPrivateIpThrowsInvalidUrl() throws IOException {
    HttpURLConnection mockConn = mock(HttpURLConnection.class);
    when(mockConn.getResponseCode()).thenReturn(302);
    when(mockConn.getHeaderField("Location")).thenReturn("http://192.168.0.1/status");

    WebCrawler testCrawler =
        new WebCrawler() {
          @Override
          @NonNull HttpURLConnection getHttpURLConnection(
              String currentUrl, String host, InetAddress safeAddress) {
            return mockConn;
          }

          @Override
          InetAddress validateAndSelectSafeAddress(String host) {
            if ("public-site.com".equals(host)) {
              InetAddress mockAddr = mock(InetAddress.class);
              when(mockAddr.getHostAddress()).thenReturn("8.8.8.8");
              return mockAddr;
            }
            return super.validateAndSelectSafeAddress(host);
          }
        };

    assertThatThrownBy(() -> testCrawler.crawl("https://public-site.com"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_URL);
  }

  @Test
  @DisplayName("상대 경로 리다이렉트 시 절대 경로로 복원하여 정상적으로 파싱한다")
  void relativeRedirectResolvesCorrectly() throws IOException {
    HttpURLConnection firstConn = mock(HttpURLConnection.class);
    when(firstConn.getResponseCode()).thenReturn(302);
    when(firstConn.getHeaderField("Location")).thenReturn("/relative-path");

    HttpURLConnection secondConn = mock(HttpURLConnection.class);
    when(secondConn.getResponseCode()).thenReturn(200);
    byte[] mockHtml = "<html><head><title>성공</title></head><body>본문</body></html>".getBytes();
    when(secondConn.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(mockHtml));

    WebCrawler testCrawler =
        new WebCrawler() {
          private int callCount = 0;

          @Override
          @NonNull HttpURLConnection getHttpURLConnection(
              String currentUrl, String host, InetAddress safeAddress) {
            callCount++;
            if (callCount == 1) {
              return firstConn;
            } else {
              return secondConn;
            }
          }

          @Override
          InetAddress validateAndSelectSafeAddress(String host) {
            if ("public-site.com".equals(host)) {
              InetAddress mockAddr = mock(InetAddress.class);
              when(mockAddr.getHostAddress()).thenReturn("8.8.8.8");
              return mockAddr;
            }
            return super.validateAndSelectSafeAddress(host);
          }
        };

    WebCrawler.CrawledDocument result = testCrawler.crawl("https://public-site.com");
    assertThat(result.title()).isEqualTo("성공");
  }

  @Test
  @DisplayName("리다이렉트 횟수가 최대 제한(3회)을 초과하면 CustomException(EXTRACT_FAILED)이 발생한다")
  void redirectCountExceededThrowsExtractFailed() throws IOException {
    HttpURLConnection mockConn = mock(HttpURLConnection.class);
    when(mockConn.getResponseCode()).thenReturn(302);
    when(mockConn.getHeaderField("Location")).thenReturn("https://public-site.com/redirect");

    WebCrawler testCrawler =
        new WebCrawler() {
          @Override
          @NonNull HttpURLConnection getHttpURLConnection(
              String currentUrl, String host, InetAddress safeAddress) {
            return mockConn;
          }

          @Override
          InetAddress validateAndSelectSafeAddress(String host) {
            if ("public-site.com".equals(host)) {
              InetAddress mockAddr = mock(InetAddress.class);
              when(mockAddr.getHostAddress()).thenReturn("8.8.8.8");
              return mockAddr;
            }
            return super.validateAndSelectSafeAddress(host);
          }
        };

    assertThatThrownBy(() -> testCrawler.crawl("https://public-site.com"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.EXTRACT_FAILED);
  }

  @Test
  @DisplayName("리다이렉트 대상 주소(Location)가 비어있으면 CustomException(EXTRACT_FAILED)이 발생한다")
  void emptyRedirectLocationThrowsExtractFailed() throws IOException {
    HttpURLConnection mockConn = mock(HttpURLConnection.class);
    when(mockConn.getResponseCode()).thenReturn(302);
    when(mockConn.getHeaderField("Location")).thenReturn("   ");

    WebCrawler testCrawler =
        new WebCrawler() {
          @Override
          @NonNull HttpURLConnection getHttpURLConnection(
              String currentUrl, String host, InetAddress safeAddress) {
            return mockConn;
          }

          @Override
          InetAddress validateAndSelectSafeAddress(String host) {
            if ("public-site.com".equals(host)) {
              InetAddress mockAddr = mock(InetAddress.class);
              when(mockAddr.getHostAddress()).thenReturn("8.8.8.8");
              return mockAddr;
            }
            return super.validateAndSelectSafeAddress(host);
          }
        };

    assertThatThrownBy(() -> testCrawler.crawl("https://public-site.com"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.EXTRACT_FAILED);
  }

  @Test
  @DisplayName("BoundedInputStream: 지정된 바이트 크기를 초과하여 읽으려 하면 IOException이 발생한다")
  void boundedInputStreamThrowsOnLimitExceeded() {
    byte[] data = "123456".getBytes();
    java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(data);
    WebCrawler.BoundedInputStream boundedIs = new WebCrawler.BoundedInputStream(bis, 5);

    assertThatThrownBy(
            () -> {
              byte[] buffer = new byte[10];
              boundedIs.read(buffer);
            })
        .isInstanceOf(java.io.IOException.class)
        .hasMessageContaining("본문 크기가 제한을 초과했습니다");
  }

  @Test
  @DisplayName("BoundedInputStream: 지정된 바이트 크기 이하의 데이터는 정상적으로 모두 읽을 수 있다")
  void boundedInputStreamReadsNormalData() throws java.io.IOException {
    byte[] data = "12345".getBytes();
    java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(data);
    WebCrawler.BoundedInputStream boundedIs = new WebCrawler.BoundedInputStream(bis, 5);

    byte[] buffer = new byte[10];
    int bytesRead = boundedIs.read(buffer);

    assertThat(bytesRead).isEqualTo(5);
    assertThat(new String(buffer, 0, bytesRead)).isEqualTo("12345");
    assertThat(boundedIs.read()).isEqualTo(-1); // EOF
  }

  @Test
  @DisplayName("HTTP 또는 HTTPS가 아닌 프로토콜 스킴(예: ftp, file) 요청 시 CustomException(INVALID_URL)이 발생한다")
  void nonHttpSchemeThrowsInvalidUrl() {
    assertThatThrownBy(() -> webCrawler.crawl("ftp://example.com/file"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_URL);

    assertThatThrownBy(() -> webCrawler.crawl("file:///etc/passwd"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_URL);
  }

  @Test
  @DisplayName("리다이렉트 타겟 프로토콜 스킴이 HTTP 또는 HTTPS가 아닌 경우 CustomException(INVALID_URL)이 발생한다")
  void redirectNonHttpSchemeThrowsInvalidUrl() throws IOException {
    HttpURLConnection mockConn = mock(HttpURLConnection.class);
    when(mockConn.getResponseCode()).thenReturn(302);
    when(mockConn.getHeaderField("Location")).thenReturn("ftp://example.com/file");

    WebCrawler testCrawler =
        new WebCrawler() {
          @Override
          @NonNull HttpURLConnection getHttpURLConnection(
              String currentUrl, String host, InetAddress safeAddress) {
            return mockConn;
          }

          @Override
          InetAddress validateAndSelectSafeAddress(String host) {
            if ("public-site.com".equals(host)) {
              InetAddress mockAddr = mock(InetAddress.class);
              when(mockAddr.getHostAddress()).thenReturn("8.8.8.8");
              return mockAddr;
            }
            return super.validateAndSelectSafeAddress(host);
          }
        };

    assertThatThrownBy(() -> testCrawler.crawl("https://public-site.com"))
        .isInstanceOf(CustomException.class)
        .extracting("errorCode")
        .isEqualTo(ContentErrorCode.INVALID_URL);
  }

  @Test
  @DisplayName("getHttpURLConnection: 비표준 포트가 포함된 URL 요청 시 Host 헤더에 포트 번호가 포함된다")
  void hostHeaderIncludesNonStandardPort() throws IOException {
    System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    try {
      WebCrawler crawler = new WebCrawler();
      InetAddress mockAddr = mock(InetAddress.class);
      when(mockAddr.getHostAddress()).thenReturn("8.8.8.8");

      // 1. 비표준 포트 (8080)
      HttpURLConnection conn1 =
          crawler.getHttpURLConnection("http://example.com:8080/path", "example.com", mockAddr);
      assertThat(conn1.getRequestProperty("Host")).isEqualTo("example.com:8080");

      // 2. 표준 포트 (80)
      HttpURLConnection conn2 =
          crawler.getHttpURLConnection("http://example.com:80/path", "example.com", mockAddr);
      assertThat(conn2.getRequestProperty("Host")).isEqualTo("example.com");

      // 3. 포트 생략
      HttpURLConnection conn3 =
          crawler.getHttpURLConnection("http://example.com/path", "example.com", mockAddr);
      assertThat(conn3.getRequestProperty("Host")).isEqualTo("example.com");
    } finally {
      System.clearProperty("sun.net.http.allowRestrictedHeaders");
    }
  }

  @Test
  @DisplayName("리다이렉트 대상 주소의 대소문자(경로 및 파라미터)가 훼손되지 않고 보존된다")
  void redirectPreservesCaseOfPathAndParameters() throws IOException {
    HttpURLConnection firstConn = mock(HttpURLConnection.class);
    when(firstConn.getResponseCode()).thenReturn(302);
    // 대소문자가 혼용된 상대 경로 설정
    when(firstConn.getHeaderField("Location")).thenReturn("/Relative-Path?Token=AbCdEf");

    HttpURLConnection secondConn = mock(HttpURLConnection.class);
    when(secondConn.getResponseCode()).thenReturn(200);
    byte[] mockHtml = "<html><head><title>성공</title></head><body>본문</body></html>".getBytes();
    when(secondConn.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(mockHtml));

    java.util.List<String> requestedUrls = new java.util.ArrayList<>();

    WebCrawler testCrawler =
        new WebCrawler() {
          private int callCount = 0;

          @Override
          HttpURLConnection getHttpURLConnection(
              String currentUrl, String host, InetAddress safeAddress) {
            requestedUrls.add(currentUrl);
            callCount++;
            if (callCount == 1) {
              return firstConn;
            } else {
              return secondConn;
            }
          }

          @Override
          InetAddress validateAndSelectSafeAddress(String host) {
            if ("public-site.com".equals(host)) {
              InetAddress mockAddr = mock(InetAddress.class);
              when(mockAddr.getHostAddress()).thenReturn("8.8.8.8");
              return mockAddr;
            }
            return super.validateAndSelectSafeAddress(host);
          }
        };

    testCrawler.crawl("https://public-site.com");

    // 두 번째 요청(리다이렉트 대상)의 URL에 대소문자가 정확히 유지되었는지 검증
    assertThat(requestedUrls).hasSize(2);
    assertThat(requestedUrls.get(1))
        .isEqualTo("https://public-site.com/Relative-Path?Token=AbCdEf");
  }

  @Test
  @DisplayName("IP 리터럴과 DNS의 SAN 검증 로직이 분리되어 올바르게 동작한다 (IPv4/IPv6 변형 포함)")
  void testHostnameVerifierWithIpAndDns() throws Exception {
    AtomicReference<HostnameVerifier> verifierRef = new AtomicReference<>();

    WebCrawler testCrawler =
        new WebCrawler() {
          @Override
          @NonNull HttpURLConnection getHttpURLConnection(
              String currentUrl, String host, InetAddress safeAddress) throws IOException {
            HttpsURLConnection mockConn = mock(HttpsURLConnection.class);
            when(mockConn.getResponseCode()).thenReturn(200);
            when(mockConn.getInputStream()).thenReturn(new ByteArrayInputStream("".getBytes()));

            Mockito.doAnswer(
                    invocation -> {
                      verifierRef.set(invocation.getArgument(0));
                      return null;
                    })
                .when(mockConn)
                .setHostnameVerifier(ArgumentMatchers.any());

            return mockConn;
          }
        };

    Method fetchHtmlMethod =
        WebCrawler.class.getDeclaredMethod(
            "fetchHtml", String.class, String.class, InetAddress.class);
    fetchHtmlMethod.setAccessible(true);
    fetchHtmlMethod.invoke(testCrawler, "https://[::1]/", "[::1]", InetAddress.getByName("::1"));

    HostnameVerifier verifier = verifierRef.get();
    assertThat(verifier).isNotNull();

    // 1. IPv6 변형 테스트 (host는 [::1], SAN은 0:0:0:0:0:0:0:1)
    SSLSession session1 = mock(SSLSession.class);
    X509Certificate cert1 = mock(X509Certificate.class);
    when(session1.getPeerCertificates()).thenReturn(new Certificate[] {cert1});
    when(cert1.getSubjectAlternativeNames()).thenReturn(List.of(List.of(7, "0:0:0:0:0:0:0:1")));
    assertThat(verifier.verify("[::1]", session1)).isTrue();

    // 2. IP 호스트인데 dNSName(Type 2) SAN만 있는 경우 차단
    SSLSession session2 = mock(SSLSession.class);
    X509Certificate cert2 = mock(X509Certificate.class);
    when(session2.getPeerCertificates()).thenReturn(new Certificate[] {cert2});
    when(cert2.getSubjectAlternativeNames()).thenReturn(List.of(List.of(2, "[::1]")));
    assertThat(verifier.verify("[::1]", session2)).isFalse();

    // 3. 도메인 호스트인데 iPAddress(Type 7) SAN이 있는 경우 차단 (도메인은 Type 2만 허용해야 함)
    fetchHtmlMethod.invoke(
        testCrawler, "https://example.com/", "example.com", InetAddress.getByName("127.0.0.1"));
    HostnameVerifier domainVerifier = verifierRef.get();

    SSLSession session3 = mock(SSLSession.class);
    X509Certificate cert3 = mock(X509Certificate.class);
    when(session3.getPeerCertificates()).thenReturn(new Certificate[] {cert3});
    when(cert3.getSubjectAlternativeNames()).thenReturn(List.of(List.of(7, "127.0.0.1")));
    assertThat(domainVerifier.verify("example.com", session3)).isFalse();
  }
}
