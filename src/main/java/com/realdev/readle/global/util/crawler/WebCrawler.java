package com.realdev.readle.global.util.crawler;

import com.realdev.readle.domain.content.exception.ContentErrorCode;
import com.realdev.readle.global.exception.CustomException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class WebCrawler {

  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
  private static final int TIMEOUT_MS = 3000;
  private static final int MAX_BODY_SIZE = 5 * 1024 * 1024; // 5MB
  private static final int MAX_REDIRECTS = 3;

  public CrawledDocument crawl(String url) {
    String currentUrl = url;
    int redirectCount = 0;

    while (true) {
      // SSRF 차단 검증
      validateUrl(currentUrl);

      try {
        // Jsoup 자동 리다이렉션 방지 (followRedirects(false)) 설정
        Connection.Response response =
            Jsoup.connect(currentUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .maxBodySize(MAX_BODY_SIZE)
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp, */*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .followRedirects(false)
                .execute();

        int statusCode = response.statusCode();

        // 수동 리다이렉트 처리 (3xx 대역)
        if (statusCode >= 300 && statusCode < 400) {
          if (++redirectCount > MAX_REDIRECTS) {
            throw new CustomException(ContentErrorCode.EXTRACT_FAILED, "리다이렉트 횟수가 초과되었습니다.");
          }
          String redirectLocation = response.header("Location");
          if (redirectLocation == null || redirectLocation.isBlank()) {
            throw new CustomException(ContentErrorCode.EXTRACT_FAILED, "리다이렉트 대상 주소가 비어있습니다.");
          }

          // 상대 경로 주소일 경우 절대 경로로 복원
          if (!redirectLocation.startsWith("http://") && !redirectLocation.startsWith("https://")) {
            URL base = new URL(currentUrl);
            redirectLocation = new URL(base, redirectLocation).toString();
          }

          currentUrl = redirectLocation;
          continue;
        }

        // 정상 수집 완료 (200 OK)
        if (statusCode == 200) {
          Document doc = response.parse();
          return parse(doc);
        } else {
          throw new HttpStatusException("HTTP Error", statusCode, currentUrl);
        }

      } catch (IllegalArgumentException | MalformedURLException e) {
        throw new CustomException(ContentErrorCode.INVALID_URL, e);
      } catch (SocketTimeoutException e) {
        throw new CustomException(ContentErrorCode.CRAWLING_TIMEOUT, e);
      } catch (IOException e) {
        throw new CustomException(ContentErrorCode.EXTRACT_FAILED, e);
      }
    }
  }

  // Jsoup 파싱 로직 검증용 default 제한자 제공
  CrawledDocument parse(Document doc) {
    String title = extractTitle(doc);
    String content = extractCleanBody(doc);
    return new CrawledDocument(title, content);
  }

  // SSRF 공격을 차단하기 위한 내부망/사설망 IP 검증
  private void validateUrl(String urlString) {
    try {
      URL url = new URL(urlString);
      String protocol = url.getProtocol();
      if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
        throw new CustomException(ContentErrorCode.INVALID_URL, "지원하지 않는 프로토콜 스킴입니다: " + protocol);
      }

      String host = url.getHost();
      InetAddress address = InetAddress.getByName(host);

      if (address.isLoopbackAddress() // 127.0.0.1 등 루프백
          || address.isLinkLocalAddress() // 169.254.x.x 등 (AWS Metadata 차단)
          || address.isSiteLocalAddress() // 10.x, 192.168.x 등 사설 대역
          || address.isAnyLocalAddress()) { // 0.0.0.0 등 비라우팅 대역
        throw new CustomException(ContentErrorCode.INVALID_URL, "허용되지 않는 호스트 주소입니다: " + host);
      }
    } catch (IllegalArgumentException | MalformedURLException e) {
      throw new CustomException(ContentErrorCode.INVALID_URL, e);
    } catch (Exception e) {
      // DNS 해석 실패 등의 예외도 잘못된 형식의 URL로 간주
      throw new CustomException(ContentErrorCode.INVALID_URL, e);
    }
  }

  private String extractTitle(Document doc) {
    // 1순위: og:title
    Element ogTitle = doc.selectFirst("meta[property=og:title]");
    if (ogTitle != null && !ogTitle.attr("content").isBlank()) {
      return ogTitle.attr("content").trim();
    }

    // 2순위: <title> 태그
    String docTitle = doc.title();
    if (!docTitle.isBlank()) {
      return docTitle.trim();
    }

    // Fallback
    return "제목 없음";
  }

  private String extractCleanBody(Document doc) {
    // 불필요한 메타/인풋/노이즈 엘리먼트 제거
    doc.select(
            "script, style, header, footer, nav, form, noscript, iframe, button, input, textarea, aside, dialog, svg")
        .remove();

    // 아티클 본문 검색 우선순위 설정
    Element bodyElement = doc.selectFirst("article");
    if (bodyElement == null) {
      bodyElement = doc.selectFirst("main");
    }
    if (bodyElement == null) {
      bodyElement = doc.body();
    }

    if (!bodyElement.hasText()) {
      return "";
    }

    return MarkdownConverter.convert(bodyElement);
  }

  public record CrawledDocument(String title, String content) {}
}
