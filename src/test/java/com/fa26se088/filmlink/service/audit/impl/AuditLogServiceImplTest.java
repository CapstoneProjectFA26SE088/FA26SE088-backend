package com.fa26se088.filmlink.service.audit.impl;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.ForwardedHeaderFilter;

class AuditLogServiceImplTest {

  @AfterEach
  void clearContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  /** Chuỗi toàn chữ hex là tên máy, không phải IP: phải trả null, không tra DNS. */
  @Test
  void parseIpLiteralRejectsHostLikeHexString() {
    assertThat(AuditLogServiceImpl.parseIpLiteral("abc")).isNull();
  }

  @Test
  void parseIpLiteralAcceptsIpv4AndIpv6() {
    assertThat(AuditLogServiceImpl.parseIpLiteral("203.0.113.7").getHostAddress())
        .isEqualTo("203.0.113.7");
    assertThat(AuditLogServiceImpl.parseIpLiteral("::1")).isNotNull();
  }

  /** App Service có thể gửi {@code ip:port}; ForwardedHeaderFilter thật phải cho ra đúng IP. */
  @ParameterizedTest
  @CsvSource({
    "203.0.113.7, 203.0.113.7",
    "203.0.113.7:54321, 203.0.113.7",
    "'203.0.113.7:54321, 10.0.0.1:80', 203.0.113.7",
    "2001:db8::1, 2001:db8:0:0:0:0:0:1",
    "[2001:db8::1]:443, 2001:db8:0:0:0:0:0:1",
    "::ffff:203.0.113.7, 203.0.113.7"
  })
  void clientIpBehindProxyIgnoresPort(String forwardedFor, String expected) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.0.0.1");
    request.addHeader("X-Forwarded-For", forwardedFor);
    MockFilterChain chain = new MockFilterChain();
    new ForwardedHeaderFilter().doFilter(request, new MockHttpServletResponse(), chain);
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes((HttpServletRequest) chain.getRequest()));

    assertThat(AuditLogServiceImpl.currentClientIp().getHostAddress()).isEqualTo(expected);
  }

  /** Giá trị không phải IP (client tự gửi được) trả null, không tra DNS. */
  @ParameterizedTest
  @ValueSource(
      strings = {"", "unknown", "abc.de", "abc.de:80", "999.1.1.1", "[abc]", "[", "a:b", ".:", "1.2"})
  void parseIpLiteralRejectsNonIp(String address) {
    assertThat(AuditLogServiceImpl.parseIpLiteral(address)).isNull();
  }
}
