package com.fa26se088.filmlink.client.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.fa26se088.filmlink.client.storage.FullResolutionLinkVerifier.LinkVerification;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * UC26: kiểm link file gốc chống SSRF. Không gọi Internet: các ca bị chặn dùng IP literal (không
 * phân giải DNS, không gửi request); các ca gửi request dùng {@link HttpServer} trên 127.0.0.1 với
 * verifier tạo bằng constructor chỉ dành cho test (cho phép loopback).
 */
class HttpFullResolutionLinkVerifierTest {

  private HttpServer server;
  private String baseUrl;
  private final List<String> methods = new CopyOnWriteArrayList<>();

  /** Verifier cho phép loopback, timeout ngắn để test chạy nhanh. */
  private HttpFullResolutionLinkVerifier loopbackVerifier;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.setExecutor(Executors.newFixedThreadPool(4));
    server.createContext("/ok", exchange -> respond(exchange, 200, null));
    server.createContext("/missing", exchange -> respond(exchange, 404, null));
    server.createContext(
        "/no-head",
        exchange -> respond(exchange, "HEAD".equals(exchange.getRequestMethod()) ? 405 : 200, null));
    server.createContext("/hop1", exchange -> respond(exchange, 302, "/hop2"));
    server.createContext("/hop2", exchange -> respond(exchange, 301, baseUrl + "/ok"));
    server.createContext("/loop", exchange -> respond(exchange, 302, "/loop"));
    server.createContext(
        "/to-private", exchange -> respond(exchange, 302, "http://10.0.0.1/file.zip"));
    server.createContext(
        "/to-metadata", exchange -> respond(exchange, 307, "http://169.254.169.254/latest/"));
    server.createContext(
        "/slow",
        exchange -> {
          try {
            Thread.sleep(2_000);
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
          respond(exchange, 200, null);
        });
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    loopbackVerifier = new HttpFullResolutionLinkVerifier(Duration.ofMillis(800), true);
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  // --- Mặc định chặn (bản Spring dùng) ---

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://127.0.0.1/file.zip",
        "http://127.0.0.1:8386/api/v1/files",
        "http://localhost/",
        "http://[::1]/",
        "http://169.254.169.254/latest/meta-data/",
        "http://10.0.0.1/",
        "http://172.16.5.4/",
        "http://192.168.1.10/",
        "http://0.0.0.0/",
        "http://100.64.0.1/",
        "http://224.0.0.1/",
        "http://[fd00::1]/",
        "http://[fe80::1]/"
      })
  void defaultVerifier_blocksInternalAddresses(String url) {
    LinkVerification result = new HttpFullResolutionLinkVerifier().verify(url);

    assertThat(result.reachable()).isFalse();
    assertThat(result.reason()).contains("nội bộ");
  }

  @ParameterizedTest
  @ValueSource(strings = {"ftp://example.com/file.zip", "file:///etc/passwd", "gopher://x/"})
  void defaultVerifier_rejectsNonHttpSchemes(String url) {
    LinkVerification result = new HttpFullResolutionLinkVerifier().verify(url);

    assertThat(result.reachable()).isFalse();
    assertThat(result.reason()).contains("http");
  }

  @Test
  void defaultVerifier_blocksLocalTestServer_noRequestSent() {
    LinkVerification result = new HttpFullResolutionLinkVerifier().verify(baseUrl + "/ok");

    assertThat(result.reachable()).isFalse();
    assertThat(methods).isEmpty();
  }

  @Test
  void malformedUrl_isRejected() {
    assertThat(new HttpFullResolutionLinkVerifier().verify("http://exa mple.com").reachable())
        .isFalse();
    assertThat(new HttpFullResolutionLinkVerifier().verify("  ").reachable()).isFalse();
  }

  // --- Gửi request thật tới server localhost (constructor dành cho test) ---

  @Test
  void head2xx_isReachable() {
    LinkVerification result = loopbackVerifier.verify(baseUrl + "/ok");

    assertThat(result).isEqualTo(LinkVerification.ok());
    assertThat(methods).containsExactly("HEAD");
  }

  @Test
  void non2xx_isUnreachableWithStatusInReason() {
    LinkVerification result = loopbackVerifier.verify(baseUrl + "/missing");

    assertThat(result.reachable()).isFalse();
    assertThat(result.reason()).contains("404");
  }

  @Test
  void head405_fallsBackToGet() {
    LinkVerification result = loopbackVerifier.verify(baseUrl + "/no-head");

    assertThat(result.reachable()).isTrue();
    assertThat(methods).containsExactly("HEAD", "GET");
  }

  @Test
  void followsUpToThreeRedirects() {
    LinkVerification result = loopbackVerifier.verify(baseUrl + "/hop1");

    assertThat(result.reachable()).isTrue();
    assertThat(methods).hasSize(3);
  }

  @Test
  void moreThanThreeRedirects_isRejected() {
    LinkVerification result = loopbackVerifier.verify(baseUrl + "/loop");

    assertThat(result.reachable()).isFalse();
    assertThat(result.reason()).contains("chuyển hướng quá 3");
    // Link gốc + đúng 3 lần đi theo chuyển hướng.
    assertThat(methods).hasSize(4);
  }

  /** Mỗi bước chuyển hướng được kiểm lại host: không bị dẫn tới địa chỉ nội bộ. */
  @Test
  void redirectToPrivateOrMetadataAddress_isBlocked() {
    LinkVerification toPrivate = loopbackVerifier.verify(baseUrl + "/to-private");
    LinkVerification toMetadata = loopbackVerifier.verify(baseUrl + "/to-metadata");

    assertThat(toPrivate.reachable()).isFalse();
    assertThat(toPrivate.reason()).contains("nội bộ");
    assertThat(toMetadata.reachable()).isFalse();
    assertThat(toMetadata.reason()).contains("nội bộ");
  }

  @Test
  void slowServer_timesOut() {
    LinkVerification result = loopbackVerifier.verify(baseUrl + "/slow");

    assertThat(result.reachable()).isFalse();
    assertThat(result.reason()).contains("Không truy cập được");
  }

  @Test
  void loopbackAllowed_stillBlocksOtherInternalRanges() {
    assertThat(loopbackVerifier.verify("http://10.0.0.1/").reachable()).isFalse();
    assertThat(loopbackVerifier.verify("http://169.254.169.254/").reachable()).isFalse();
  }

  private void respond(HttpExchange exchange, int status, String location) throws IOException {
    methods.add(exchange.getRequestMethod());
    if (location != null) {
      exchange.getResponseHeaders().add("Location", location);
    }
    exchange.sendResponseHeaders(status, -1);
    exchange.close();
  }
}
