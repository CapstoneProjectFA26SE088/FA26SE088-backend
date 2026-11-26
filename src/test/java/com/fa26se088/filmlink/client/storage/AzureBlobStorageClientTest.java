package com.fa26se088.filmlink.client.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.azure.core.util.FluxUtil;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Không gọi Azure thật: SDK được mock, SAS ký offline bằng khoá giả. */
class AzureBlobStorageClientTest {

  private static final Instant NOW = Instant.parse("2026-11-25T03:00:00.900Z");
  private static final Duration TTL = Duration.ofMinutes(15);
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String KEY = "avatar/2026/11/00000000-0000-0000-0000-000000000001.png";

  /** Khoá giả sinh trong test, không phải bí mật thật. */
  private static final String FAKE_KEY = LocalStorageClientTest.TEST_SECRET;

  private BlobContainerClient container;
  private BlobClient blob;
  private AzureBlobStorageClient client;

  @BeforeEach
  void setUp() {
    container = mock(BlobContainerClient.class);
    blob = mock(BlobClient.class);
    when(container.getBlobClient(anyString())).thenReturn(blob);
    client = new AzureBlobStorageClient(container, TTL, CLOCK);
  }

  @Test
  void uploadsWithServerGeneratedKeyAndDetectedContentType() {
    byte[] content = LocalStorageClientTest.pdf(100);

    String key = upload(StoragePurpose.LAB_DOCUMENT, "giay-phep.png", content);

    assertThat(key).matches("lab-document/2026/11/[0-9a-f-]{36}\\.pdf");
    verify(container).getBlobClient(key);
    ArgumentCaptor<BlobParallelUploadOptions> options =
        ArgumentCaptor.forClass(BlobParallelUploadOptions.class);
    verify(blob).uploadWithResponse(options.capture(), any(), any());
    assertThat(options.getValue().getHeaders().getContentType()).isEqualTo("application/pdf");
    assertThat(FluxUtil.collectBytesInByteBufferStream(options.getValue().getDataFlux()).block())
        .isEqualTo(content);
  }

  @Test
  void wrongTypeIsRejectedBeforeCallingSdk() {
    assertError(
        () -> upload(StoragePurpose.AVATAR, "a.png", LocalStorageClientTest.pdf(100)),
        ErrorCode.STORAGE_FILE_TYPE_NOT_ALLOWED);
    verifyNoInteractions(container);
  }

  /** SCR-05: chặn cả dung lượng khai báo lẫn dung lượng thật. */
  @Test
  void tooLargeFileIsRejectedBeforeCallingSdk() {
    int limit = (int) StoragePurpose.AVATAR.maxSizeBytes();
    byte[] small = LocalStorageClientTest.png(10);
    byte[] tooBig = LocalStorageClientTest.png(limit + 1);

    assertError(
        () ->
            client.upload(
                StoragePurpose.AVATAR, "a.png", new ByteArrayInputStream(small), limit + 1L),
        ErrorCode.STORAGE_FILE_TOO_LARGE);
    assertError(
        () -> client.upload(StoragePurpose.AVATAR, "a.png", new ByteArrayInputStream(tooBig), 10),
        ErrorCode.STORAGE_FILE_TOO_LARGE);
    verifyNoInteractions(container);
  }

  @Test
  void fileAtExactLimitIsAccepted() {
    upload(
        StoragePurpose.AVATAR,
        "a.png",
        LocalStorageClientTest.png((int) StoragePurpose.AVATAR.maxSizeBytes()));

    verify(blob).uploadWithResponse(any(), any(), any());
  }

  /** NFR-PRI-02: SAS chỉ đọc, chỉ HTTPS, hết hạn không quá TTL. */
  @Test
  void signedUrlIsReadOnlyHttpsAndExpiresWithinTtl() {
    BlobContainerClient offline =
        new BlobServiceClientBuilder()
            .connectionString(connectionString(FAKE_KEY))
            .buildClient()
            .getBlobContainerClient("filmlink");

    URI uri = new AzureBlobStorageClient(offline, TTL, CLOCK).signedUrl(KEY);

    assertThat(uri.getScheme()).isEqualTo("https");
    // Tên blob có "/" có thể được mã hoá %2F trong URL; getPath() đã giải mã.
    assertThat(uri.getPath()).isEqualTo("/filmlink/" + KEY);
    Map<String, String> params = LocalStorageClientTest.queryParams(uri);
    assertThat(params)
        .containsEntry("sp", "r")
        .containsEntry("spr", "https")
        .containsEntry("sr", "b")
        .containsKey("sig");
    assertThat(Instant.parse(params.get("se")))
        .isBeforeOrEqualTo(NOW.plus(TTL))
        .isAfter(NOW.plus(TTL).minusSeconds(1));
  }

  @Test
  void invalidBlobKeyIsRejectedBeforeCallingSdk() {
    assertThatThrownBy(() -> client.signedUrl("avatar/../../x.png"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> client.delete(null)).isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(container);
  }

  @Test
  void deleteUsesDeleteIfExists() {
    client.delete(KEY);

    verify(container).getBlobClient(KEY);
    verify(blob).deleteIfExistsWithResponse(isNull(), isNull(), any(), any());
  }

  /** UH-39: mọi lỗi SDK (lỗi HTTP, mạng, timeout) thành STORAGE_UNAVAILABLE, không lộ chi tiết. */
  @Test
  void sdkErrorsBecomeStorageUnavailable() {
    when(blob.uploadWithResponse(any(), any(), any()))
        .thenThrow(new BlobStorageException("AccountKey=leak", null, null));
    when(blob.deleteIfExistsWithResponse(any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("Timeout on blocking read"));
    when(blob.generateSas(any())).thenThrow(new IllegalArgumentException("AccountKey=leak"));

    assertError(
        () -> upload(StoragePurpose.AVATAR, "a.png", LocalStorageClientTest.png(10)),
        ErrorCode.STORAGE_UNAVAILABLE);
    assertError(() -> client.delete(KEY), ErrorCode.STORAGE_UNAVAILABLE);
    assertError(() -> client.signedUrl(KEY), ErrorCode.STORAGE_UNAVAILABLE);
    assertThatThrownBy(() -> client.signedUrl(KEY))
        .hasMessageNotContaining("AccountKey")
        .hasNoCause();
  }

  @Test
  void unreadableContentBecomesStorageUnavailable() {
    InputStream broken =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException("broken");
          }
        };

    assertError(
        () -> client.upload(StoragePurpose.AVATAR, "a.png", broken, 10),
        ErrorCode.STORAGE_UNAVAILABLE);
  }

  @Test
  void missingConnectionStringStopsStartupWithClearMessage() {
    assertStartupFails("", "filmlink", "AZURE_STORAGE_CONNECTION_STRING");
  }

  @Test
  void blankContainerStopsStartupWithClearMessage() {
    assertStartupFails(connectionString(FAKE_KEY), "", "AZURE_STORAGE_CONTAINER");
  }

  /** Connection string hỏng: dừng trước khi gọi mạng, thông báo không chứa giá trị. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "leaked-value",
        "AccountName=leaked-value;AccountKey=@@@;EndpointSuffix=core.windows.net",
        "BlobEndpoint=https://x.blob.core.windows.net;SharedAccessSignature=sig=leaked-value"
      })
  void malformedConnectionStringStopsStartupWithoutLeakingIt(String connectionString) {
    assertStartupFails(connectionString, "filmlink", "AZURE_STORAGE_CONNECTION_STRING");
  }

  @Test
  void propertiesToStringHidesConnectionString() {
    assertThat(new StorageProperties.Azure("leaked-value", "filmlink").toString())
        .doesNotContain("leaked-value");
  }

  @Configuration
  @EnableConfigurationProperties(StorageProperties.class)
  @Import({AzureBlobStorageClient.class, LocalStorageClient.class})
  static class StorageTestConfig {
    @Bean
    Clock clock() {
      return CLOCK;
    }
  }

  private static void assertStartupFails(
      String connectionString, String containerName, String expectedMessage) {
    new ApplicationContextRunner()
        .withUserConfiguration(StorageTestConfig.class)
        .withPropertyValues(
            "filmlink.storage.provider=azure",
            "filmlink.storage.signed-url-ttl=PT15M",
            "filmlink.storage.azure.connection-string=" + connectionString,
            "filmlink.storage.azure.container=" + containerName)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .rootCause()
                  .isInstanceOf(IllegalStateException.class)
                  .hasMessageContaining(expectedMessage);
              assertThat(context.getStartupFailure().toString()).doesNotContain("leaked-value");
            });
  }

  private static String connectionString(String accountKey) {
    return "DefaultEndpointsProtocol=https;AccountName=fakeaccount;AccountKey="
        + accountKey
        + ";EndpointSuffix=core.windows.net";
  }

  private String upload(StoragePurpose purpose, String filename, byte[] content) {
    return client.upload(purpose, filename, new ByteArrayInputStream(content), content.length);
  }

  private static void assertError(ThrowingCallable call, ErrorCode errorCode) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessException.class)
        .extracting("errorCode")
        .isEqualTo(errorCode);
  }
}
