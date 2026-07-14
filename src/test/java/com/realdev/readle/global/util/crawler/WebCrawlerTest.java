package com.realdev.readle.global.util.crawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.realdev.readle.global.exception.CustomException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        .isInstanceOf(CustomException.class);
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
}
