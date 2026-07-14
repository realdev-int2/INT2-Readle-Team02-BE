package com.realdev.readle.global.util.crawler;

import com.realdev.readle.domain.content.exception.ContentErrorCode;
import com.realdev.readle.global.exception.CustomException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class WebCrawler {

  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
  private static final int TIMEOUT_MS = 5000;
  private static final int MAX_BODY_SIZE = 5 * 1024 * 1024; // 5MB

  public CrawledDocument crawl(String url) {
    try {
      Document doc =
          Jsoup.connect(url)
              .userAgent(USER_AGENT)
              .timeout(TIMEOUT_MS)
              .maxBodySize(MAX_BODY_SIZE)
              .header(
                  "Accept",
                  "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp, */*;q=0.8")
              .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
              .followRedirects(true)
              .get();

      return parse(doc);

    } catch (IllegalArgumentException | MalformedURLException e) {
      throw new CustomException(ContentErrorCode.INVALID_URL, e);
    } catch (SocketTimeoutException e) {
      throw new CustomException(ContentErrorCode.CRAWLING_TIMEOUT, e);
    } catch (IOException e) {
      throw new CustomException(ContentErrorCode.EXTRACT_FAILED, e);
    }
  }

  // 테스트 코드에서도 파싱 로직 검증할 수 있도록 default 제한자로 제공
  CrawledDocument parse(Document doc) {
    String title = extractTitle(doc);
    String content = extractCleanBody(doc);
    return new CrawledDocument(title, content);
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
