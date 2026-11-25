package com.fa26se088.filmlink.client.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.PublicAccessType;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Chạy SDK thật với Azurite, cần Docker. Không chạy mặc định (tag {@code azurite}):
 *
 * <pre>.\mvnw.cmd test "-DexcludedTestGroups=none" "-Dtest=AzureBlobStorageClientAzuriteTest"</pre>
 */
@Tag("azurite")
class AzureBlobStorageClientAzuriteTest {

  private static final String ACCOUNT = "testaccount";

  /** Khoá giả sinh trong test, không phải bí mật thật. */
  private static final String ACCOUNT_KEY = Base64.getEncoder().encodeToString(new byte[64]);

  private static GenericContainer<?> azurite;
  private static BlobServiceClient service;

  @BeforeAll
  static void start() {
    azurite =
        new GenericContainer<>(DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:3.35.0"))
            .withEnv("AZURITE_ACCOUNTS", ACCOUNT + ":" + ACCOUNT_KEY)
            .withCommand("azurite-blob", "--blobHost", "0.0.0.0", "--skipApiVersionCheck")
            .withExposedPorts(10000);
    azurite.start();
    service = new BlobServiceClientBuilder().connectionString(connectionString()).buildClient();
  }

  @AfterAll
  static void stop() {
    if (azurite != null) {
      azurite.stop();
    }
  }

  @Test
  void createsPrivateContainerThenUploadsAndDeletes() {
    AzureBlobStorageClient client = newClient("filmlink");
    BlobContainerClient container = service.getBlobContainerClient("filmlink");
    assertThat(container.getProperties().getBlobPublicAccess()).isNull();

    byte[] content = LocalStorageClientTest.png(6000);
    String key =
        client.upload(
            StoragePurpose.AVATAR, "a.png", new ByteArrayInputStream(content), content.length);

    assertThat(container.getBlobClient(key).downloadContent().toBytes()).isEqualTo(content);
    assertThat(container.getBlobClient(key).getProperties().getContentType()).isEqualTo("image/png");

    client.delete(key);
    assertThat(container.getBlobClient(key).exists()).isFalse();
    assertThatCode(() -> client.delete(key)).doesNotThrowAnyException();
    // Khởi động lại khi container đã có.
    assertThatCode(() -> newClient("filmlink")).doesNotThrowAnyException();
  }

  /**
   * Azurite chạy HTTP. Nó kiểm chữ ký trước rồi mới kiểm giao thức, nên lỗi
   * AuthorizationProtocolMismatch chứng tỏ chữ ký đúng và link chỉ dùng được qua HTTPS.
   */
  @Test
  void signedUrlHasValidSignatureAndRefusesHttp() throws Exception {
    AzureBlobStorageClient client = newClient("filmlink-sas");
    byte[] content = LocalStorageClientTest.png(100);
    String key =
        client.upload(
            StoragePurpose.AVATAR, "a.png", new ByteArrayInputStream(content), content.length);
    URI uri = client.signedUrl(key);

    assertThat(get(uri)).contains("AuthorizationProtocolMismatch");
    URI tampered = URI.create(uri.toString().replace("sp=r", "sp=rw"));
    assertThat(get(tampered)).contains("AuthorizationFailure");
  }

  @Test
  void publicContainerStopsStartup() {
    service
        .getBlobContainerClient("public")
        .createWithResponse(null, PublicAccessType.BLOB, null, null);

    assertThatThrownBy(() -> newClient("public"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("private");
  }

  private static AzureBlobStorageClient newClient(String containerName) {
    return new AzureBlobStorageClient(
        new StorageProperties(
            "azure",
            Duration.ofMinutes(15),
            null,
            new StorageProperties.Azure(connectionString(), containerName)),
        Clock.systemUTC());
  }

  private static String connectionString() {
    return "DefaultEndpointsProtocol=http;AccountName="
        + ACCOUNT
        + ";AccountKey="
        + ACCOUNT_KEY
        + ";BlobEndpoint=http://"
        + azurite.getHost()
        + ":"
        + azurite.getMappedPort(10000)
        + "/"
        + ACCOUNT;
  }

  private static String get(URI uri) throws Exception {
    try (HttpClient http = HttpClient.newHttpClient()) {
      HttpResponse<String> response =
          http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(403);
      return response.body();
    }
  }
}
