package com.realdev.readle.global.util.crawler;

import com.realdev.readle.domain.content.exception.ContentErrorCode;
import com.realdev.readle.global.exception.CustomException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import lombok.Getter;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jspecify.annotations.NonNull;
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
      URL parsedUrl;
      try {
        parsedUrl = new URL(currentUrl);
      } catch (MalformedURLException e) {
        throw new CustomException(ContentErrorCode.INVALID_URL, e);
      }

      String host = parsedUrl.getHost();

      // 1. 다중 A/AAAA 레코드 전체 조회 및 검증을 통한 연결 대상 고정(Pinning)
      InetAddress safeAddress = validateAndSelectSafeAddress(host);

      try {
        // IP 주소 기반 직접 연결 요청 (2차 DNS 룩업 차단을 통한 Rebinding 방어)
        Document doc = fetchHtml(currentUrl, host, safeAddress);
        return parse(doc);

      } catch (RedirectException e) {
        if (++redirectCount > MAX_REDIRECTS) {
          throw new CustomException(ContentErrorCode.EXTRACT_FAILED, "리다이렉트 횟수가 초과되었습니다.");
        }
        String redirectLocation = e.getLocation();
        if (redirectLocation == null || redirectLocation.isBlank()) {
          throw new CustomException(ContentErrorCode.EXTRACT_FAILED, "리다이렉트 대상 주소가 비어있습니다.");
        }

        // 상대 경로 주소일 경우 절대 경로로 복원
        if (!redirectLocation.startsWith("http://") && !redirectLocation.startsWith("https://")) {
          try {
            URL base = new URL(currentUrl);
            redirectLocation = new URL(base, redirectLocation).toString();
          } catch (MalformedURLException ex) {
            throw new CustomException(ContentErrorCode.INVALID_URL, ex);
          }
        }

        currentUrl = redirectLocation;
        continue;

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

  // IP Pinning(DNS Rebinding 방어) 및 수동 리다이렉트 추적이 적용된 저수준 HTTP 통신 처리
  private Document fetchHtml(String currentUrl, String host, InetAddress safeAddress)
      throws IOException {
    HttpURLConnection conn = getHttpURLConnection(currentUrl, host, safeAddress);

    // HTTPS인 경우 SNI 및 HostnameVerifier 수동 설정을 통한 IP 통신 지원
    if (conn instanceof HttpsURLConnection httpsConn) {
      httpsConn.setSSLSocketFactory(
          new SniSSLSocketFactory((SSLSocketFactory) SSLSocketFactory.getDefault(), host));
      httpsConn.setHostnameVerifier(
          (hostname, session) ->
              HttpsURLConnection.getDefaultHostnameVerifier().verify(host, session));
    }

    int statusCode = conn.getResponseCode();

    // 리다이렉트 발생 시 외부 루프로 위임하기 위한 커스텀 예외 발생
    if (statusCode >= 300 && statusCode < 400) {
      String location = conn.getHeaderField("Location");
      conn.disconnect();
      throw new RedirectException(location, statusCode);
    }

    if (statusCode == 200) {
      try (InputStream in = new BoundedInputStream(conn.getInputStream(), MAX_BODY_SIZE)) {
        // 수집된 인풋스트림 Jsoup 파싱
        return Jsoup.parse(in, "UTF-8", currentUrl);
      } finally {
        conn.disconnect();
      }
    } else {
      conn.disconnect();
      throw new HttpStatusException("HTTP Error", statusCode, currentUrl);
    }
  }

  @NonNull HttpURLConnection getHttpURLConnection(
      String currentUrl, String host, InetAddress safeAddress) throws IOException {
    URL parsedUrl = new URL(currentUrl);
    String ipString = safeAddress.getHostAddress();
    String ipHost = (safeAddress instanceof Inet6Address) ? "[" + ipString + "]" : ipString;

    // DNS Rebinding 차단을 위해 IP 기반 URL로 재생성
    URL ipUrl = new URL(parsedUrl.getProtocol(), ipHost, parsedUrl.getPort(), parsedUrl.getFile());

    HttpURLConnection conn = (HttpURLConnection) ipUrl.openConnection();
    conn.setConnectTimeout(TIMEOUT_MS);
    conn.setReadTimeout(TIMEOUT_MS);
    conn.setInstanceFollowRedirects(false); // 리다이렉트 경로 SSRF 재검증을 위해 자동 이동 금지
    conn.setRequestProperty("User-Agent", USER_AGENT);
    conn.setRequestProperty("Host", host); // HTTP 가상 호스트 라우팅 보존
    conn.setRequestProperty(
        "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp, *//*;q=0.8");
    conn.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7");
    return conn;
  }

  // 다중 A/AAAA IP 레코드 전체 조회 및 안전한 IP 검증 후 고정 연결 대상 반환
  private InetAddress validateAndSelectSafeAddress(String host) {
    try {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      if (addresses == null || addresses.length == 0) {
        throw new CustomException(ContentErrorCode.INVALID_URL, "호스트를 해석할 수 없습니다: " + host);
      }

      // 해석된 모든 IP의 전역 라우팅 가능 여부 및 안전성 검증
      for (InetAddress address : addresses) {
        if (!isSafeAddress(address)) {
          throw new CustomException(ContentErrorCode.INVALID_URL, "허용되지 않는 호스트 주소입니다: " + host);
        }
      }

      // 모든 IP 레코드 검증 통과 시, 첫 번째 주소를 고정 연결 대상으로 채택
      return addresses[0];
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      throw new CustomException(ContentErrorCode.INVALID_URL, e);
    }
  }

  // SSRF 차단을 위한 포괄적 IP 대역 필터링 규칙 (IPv4/IPv6 포함)
  private boolean isSafeAddress(InetAddress address) {
    if (address.isLoopbackAddress() // 127.0.0.1, ::1 등 루프백
        || address.isLinkLocalAddress() // 169.254.x.x 등 링크 로컬
        || address.isAnyLocalAddress() // 0.0.0.0 등 비라우팅
        || address.isMulticastAddress()) { // 멀티캐스트 대역 차단
      return false;
    }

    byte[] bytes = address.getAddress();

    // 1) IPv4 검증
    if (bytes.length == 4) {
      int first = bytes[0] & 0xFF;
      int second = bytes[1] & 0xFF;

      // RFC 1918 사설망 대역 (isSiteLocalAddress 보완)
      if (first == 10) {
        return false; // 10.0.0.0/8
      }
      if (first == 172 && (second >= 16 && second <= 31)) {
        return false; // 172.16.0.0/12
      }
      if (first == 192 && second == 168) {
        return false; // 192.168.0.0/16
      }

      // CGNAT 대역: 100.64.0.0/10 (100.64.0.0 ~ 100.127.255.255)
      if (first == 100 && (second >= 64 && second <= 127)) {
        return false;
      }

      // Link-Local 대역 재검증: 169.254.0.0/16
      if (first == 169 && second == 254) {
        return false;
      }
    }

    // 2) IPv6 ULA (Unique Local Address) 대역 차단: fc00::/7 (bits 1111110x)
    return bytes.length != 16 || ((bytes[0] & 0xFF) & 0xFE) != 0xFC;
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

  // HTTPS 연결 시 IP 주소로 통신하되 원래 Host네임(SNI)을 보존해 주는 커스텀 SSL 소켓 팩토리
  private static class SniSSLSocketFactory extends SSLSocketFactory {
    private final SSLSocketFactory delegate;
    private final String originalHost;

    public SniSSLSocketFactory(SSLSocketFactory delegate, String originalHost) {
      this.delegate = delegate;
      this.originalHost = originalHost;
    }

    private Socket configureSocket(Socket socket) {
      if (socket instanceof SSLSocket sslSocket) {
        try {
          SSLParameters params = sslSocket.getSSLParameters();
          params.setServerNames(List.of(new SNIHostName(originalHost)));
          sslSocket.setSSLParameters(params);
        } catch (IllegalArgumentException e) {
          // 도메인 형식이 아닌 특수 호스트명이 넘어와서 발생하는 SNI 주입 오류 방어
        }
      }
      return socket;
    }

    @Override
    public String[] getDefaultCipherSuites() {
      return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
      return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose)
        throws IOException {
      return configureSocket(delegate.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket() throws IOException {
      return configureSocket(delegate.createSocket());
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
      return configureSocket(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
        throws IOException {
      return configureSocket(delegate.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
      return configureSocket(delegate.createSocket(host, port));
    }

    @Override
    public Socket createSocket(
        InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
      return configureSocket(delegate.createSocket(address, port, localAddress, localPort));
    }
  }

  // 리다이렉트 발생을 외부 루프로 위임하기 위한 이너 예외 클래스
  @Getter
  private static class RedirectException extends IOException {
    private final String location;
    private final int statusCode;

    public RedirectException(String location, int statusCode) {
      this.location = location;
      this.statusCode = statusCode;
    }
  }

  // 읽기 제한용 BoundedInputStream
  static class BoundedInputStream extends InputStream {
    private final InputStream in;
    private final int maxBytes;
    private int bytesRead = 0;

    public BoundedInputStream(InputStream in, int maxBytes) {
      this.in = in;
      this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
      int result = in.read();
      if (result != -1) {
        bytesRead++;
        if (bytesRead > maxBytes) {
          throw new IOException("본문 크기가 제한을 초과했습니다: " + maxBytes + " bytes");
        }
      }
      return result;
    }

    @Override
    public int read(byte @NonNull [] b, int off, int len) throws IOException {
      if (len == 0) {
        return 0;
      }
      int result = in.read(b, off, len);
      if (result != -1) {
        bytesRead += result;
        if (bytesRead > maxBytes) {
          throw new IOException("본문 크기가 제한을 초과했습니다: " + maxBytes + " bytes");
        }
      }
      return result;
    }

    @Override
    public void close() throws IOException {
      in.close();
    }
  }
}
