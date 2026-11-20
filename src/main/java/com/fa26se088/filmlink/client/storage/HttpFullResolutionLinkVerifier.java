package com.fa26se088.filmlink.client.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Bản thật của {@link FullResolutionLinkVerifier}: gửi {@code HEAD} (server trả 405/501 thì {@code
 * GET} nhưng không đọc body) tới link, chấp nhận khi nhận 2xx.
 *
 * <p>Chống SSRF:
 *
 * <ul>
 *   <li>Chỉ scheme {@code http} / {@code https}, phải có host.
 *   <li>Host được phân giải trước khi gửi; <b>bất kỳ</b> địa chỉ nào thuộc loopback, private
 *       (10/8, 172.16/12, 192.168/16, fc00::/7, fec0::/10), link-local (169.254/16 — gồm metadata
 *       endpoint của cloud — và fe80::/10), multicast, any-local (0.0.0.0, ::), 0.0.0.0/8, CGNAT
 *       100.64/10, broadcast thì từ chối.
 *   <li>Không để {@link HttpClient} tự đi theo chuyển hướng ({@code Redirect.NEVER}): tự xử lý tối
 *       đa {@value #MAX_REDIRECTS} lần, <b>kiểm lại scheme và host ở mỗi bước</b>.
 *   <li>Timeout kết nối và timeout chờ phản hồi đều {@value #TIMEOUT_SECONDS} giây; không đọc body.
 * </ul>
 *
 * <p>Giới hạn còn lại: {@link HttpClient} phân giải DNS lại khi kết nối, nên tên miền đổi IP giữa
 * lúc kiểm và lúc kết nối (DNS rebinding) chưa chặn được triệt để.
 */
@Slf4j
@Component
public class HttpFullResolutionLinkVerifier implements FullResolutionLinkVerifier {

  static final int TIMEOUT_SECONDS = 5;
  static final int MAX_REDIRECTS = 3;

  private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
  private static final Set<Integer> REDIRECT_CODES = Set.of(301, 302, 303, 307, 308);
  private static final String USER_AGENT = "FilmLink-LinkVerifier/1.0";

  private final HttpClient httpClient;
  private final Duration timeout;
  private final boolean allowLoopback;

  /** Bản dùng trong app: chặn mọi địa chỉ nội bộ, kể cả loopback. */
  @Autowired
  public HttpFullResolutionLinkVerifier() {
    this(Duration.ofSeconds(TIMEOUT_SECONDS), false);
  }

  /**
   * Chỉ dùng trong test: {@code allowLoopback = true} cho phép gọi server HTTP chạy trên localhost.
   * Các dải nội bộ khác vẫn bị chặn.
   */
  HttpFullResolutionLinkVerifier(Duration timeout, boolean allowLoopback) {
    this.timeout = timeout;
    this.allowLoopback = allowLoopback;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @Override
  public LinkVerification verify(String url) {
    if (url == null || url.isBlank()) {
      return LinkVerification.failed("Link trống.");
    }
    URI uri;
    try {
      uri = new URI(url.strip());
    } catch (URISyntaxException ex) {
      return LinkVerification.failed("Link không đúng định dạng URL.");
    }

    for (int redirects = 0; ; redirects++) {
      String blocked = checkTarget(uri);
      if (blocked != null) {
        return LinkVerification.failed(blocked);
      }
      int status;
      Optional<String> location;
      try {
        HttpResponse<?> response = send(uri, "HEAD");
        if (response.statusCode() == 405 || response.statusCode() == 501) {
          response = send(uri, "GET");
        }
        status = response.statusCode();
        location = response.headers().firstValue("Location");
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        return LinkVerification.failed("Không kiểm tra được link (bị ngắt).");
      } catch (IOException | IllegalArgumentException ex) {
        log.info("Full-resolution link check failed for host {}: {}", uri.getHost(), ex.toString());
        return LinkVerification.failed(
            "Không truy cập được link (lỗi kết nối hoặc quá " + TIMEOUT_SECONDS + " giây).");
      }

      if (status >= 200 && status < 300) {
        return LinkVerification.ok();
      }
      if (!REDIRECT_CODES.contains(status)) {
        return LinkVerification.failed("Link trả mã HTTP " + status + ", không truy cập được.");
      }
      if (redirects >= MAX_REDIRECTS) {
        return LinkVerification.failed(
            "Link chuyển hướng quá " + MAX_REDIRECTS + " lần; hãy gửi link trực tiếp.");
      }
      if (location.isEmpty()) {
        return LinkVerification.failed("Link chuyển hướng nhưng không có địa chỉ đích.");
      }
      try {
        uri = uri.resolve(new URI(location.get().strip()));
      } catch (URISyntaxException | IllegalArgumentException ex) {
        return LinkVerification.failed("Link chuyển hướng tới địa chỉ không hợp lệ.");
      }
    }
  }

  /**
   * Kiểm scheme và host của một bước (link gốc hoặc đích chuyển hướng).
   *
   * @return lý do bị chặn; {@code null} nếu được phép gửi request
   */
  String checkTarget(URI uri) {
    String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
    if (scheme == null || !ALLOWED_SCHEMES.contains(scheme)) {
      return "Chỉ chấp nhận link http hoặc https.";
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return "Link không có tên máy chủ hợp lệ.";
    }
    InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host);
    } catch (UnknownHostException | SecurityException ex) {
      return "Không phân giải được tên máy chủ của link.";
    }
    for (InetAddress address : addresses) {
      if (isBlocked(address)) {
        return "Link trỏ tới địa chỉ nội bộ, không được phép.";
      }
    }
    return null;
  }

  /** Địa chỉ không được phép gọi tới (xem Javadoc của class). */
  boolean isBlocked(InetAddress address) {
    if (address.isLoopbackAddress()) {
      return !allowLoopback;
    }
    if (address.isAnyLocalAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    byte[] bytes = address.getAddress();
    if (address instanceof Inet4Address) {
      int first = bytes[0] & 0xFF;
      int second = bytes[1] & 0xFF;
      return first == 0 // 0.0.0.0/8
          || (first == 100 && second >= 64 && second <= 127) // 100.64.0.0/10 (CGNAT)
          || (first == 255 && (bytes[1] & bytes[2] & bytes[3] & 0xFF) == 0xFF); // broadcast
    }
    if (address instanceof Inet6Address inet6) {
      if ((bytes[0] & 0xFE) == 0xFC) { // fc00::/7 unique local
        return true;
      }
      if (inet6.isIPv4CompatibleAddress()) { // ::a.b.c.d
        try {
          return isBlocked(
              InetAddress.getByAddress(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]}));
        } catch (UnknownHostException ex) {
          return true;
        }
      }
    }
    return false;
  }

  /** Gửi một request, không đọc body: {@code GET} xin 1 byte và đóng stream ngay. */
  private HttpResponse<?> send(URI uri, String method) throws IOException, InterruptedException {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(uri).timeout(timeout).header("User-Agent", USER_AGENT);
    if ("HEAD".equals(method)) {
      return httpClient.send(
          builder.method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
          HttpResponse.BodyHandlers.discarding());
    }
    HttpResponse<InputStream> response =
        httpClient.send(
            builder.GET().header("Range", "bytes=0-0").build(),
            HttpResponse.BodyHandlers.ofInputStream());
    response.body().close();
    return response;
  }
}
