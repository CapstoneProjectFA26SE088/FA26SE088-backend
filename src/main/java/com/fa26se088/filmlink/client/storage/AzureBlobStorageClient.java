package com.fa26se088.filmlink.client.storage;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import com.azure.storage.blob.sas.BlobSasPermission;
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues;
import com.azure.storage.common.policy.RequestRetryOptions;
import com.azure.storage.common.policy.RetryPolicyType;
import com.azure.storage.common.sas.SasProtocol;
import com.fa26se088.filmlink.exception.BusinessException;
import com.fa26se088.filmlink.exception.ErrorCode;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bản {@link StorageClient} dùng Azure Blob Storage: blobKey là tên blob trong một container private,
 * link tải là service SAS chỉ đọc, chỉ HTTPS, ký bằng account key trong connection string.
 *
 * <p>Chỉ được tạo khi {@code filmlink.storage.provider=azure}. App dừng khi khởi động nếu cấu hình
 * sai (thiếu / hỏng connection string, Azure từ chối, container đang public). Azure tạm thời không
 * kết nối được thì app vẫn khởi động (UH-39), thao tác file trả {@code STORAGE_UNAVAILABLE}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "filmlink.storage.provider", havingValue = "azure")
public class AzureBlobStorageClient implements StorageClient {

  /** Tổng thời gian chờ một thao tác, gồm cả các lần thử lại. */
  private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(60);

  /** Mặc định của SDK có thể treo request nhiều phút khi storage lỗi. */
  private static final RequestRetryOptions RETRY_OPTIONS =
      new RequestRetryOptions(
          RetryPolicyType.EXPONENTIAL,
          2,
          Duration.ofSeconds(25),
          Duration.ofSeconds(1),
          Duration.ofSeconds(5),
          null);

  private final BlobContainerClient container;
  private final Duration signedUrlTtl;
  private final Clock clock;

  @Autowired
  public AzureBlobStorageClient(StorageProperties properties, Clock clock) {
    this(buildContainerClient(properties), properties.signedUrlTtl(), clock);
    prepareContainer();
    log.info("Storage provider=azure, container={}", container.getBlobContainerName());
  }

  /** Không gọi Azure, dùng cho test. */
  AzureBlobStorageClient(BlobContainerClient container, Duration signedUrlTtl, Clock clock) {
    this.container = container;
    this.signedUrlTtl = signedUrlTtl;
    this.clock = clock;
  }

  /** Đọc trọn file vào bộ nhớ (tối đa vài MB theo purpose) để chặn file quá lớn trước khi gọi Azure. */
  @Override
  public String upload(
      StoragePurpose purpose, String originalFilename, InputStream content, long size) {
    StorageFileValidator.checkSize(purpose, size);
    StorageFileType type;
    byte[] bytes;
    try {
      BufferedInputStream input = new BufferedInputStream(content);
      type = StorageFileValidator.detectAllowedType(purpose, input);
      bytes = input.readNBytes(Math.toIntExact(purpose.maxSizeBytes() + 1));
    } catch (IOException ex) {
      throw unavailable("upload", ex);
    }
    StorageFileValidator.checkSize(purpose, bytes.length);

    String blobKey = StorageBlobKey.generate(purpose, type, clock.instant());
    BlobParallelUploadOptions options =
        new BlobParallelUploadOptions(BinaryData.fromBytes(bytes))
            .setHeaders(new BlobHttpHeaders().setContentType(type.contentType()));
    try {
      container.getBlobClient(blobKey).uploadWithResponse(options, OPERATION_TIMEOUT, Context.NONE);
    } catch (RuntimeException ex) {
      // SDK ném nhiều kiểu: BlobStorageException, lỗi mạng, IllegalStateException khi quá timeout.
      throw unavailable("upload", ex);
    }
    return blobKey;
  }

  @Override
  public void delete(String blobKey) {
    StorageBlobKey.requireValid(blobKey);
    try {
      container
          .getBlobClient(blobKey)
          .deleteIfExistsWithResponse(null, null, OPERATION_TIMEOUT, Context.NONE);
    } catch (RuntimeException ex) {
      throw unavailable("delete", ex);
    }
  }

  /** NFR-PRI-02: ký tại chỗ bằng account key, không gọi Azure. */
  @Override
  public URI signedUrl(String blobKey) {
    StorageBlobKey.requireValid(blobKey);
    try {
      return sign(container.getBlobClient(blobKey));
    } catch (RuntimeException ex) {
      throw unavailable("signedUrl", ex);
    }
  }

  private URI sign(BlobClient blob) {
    OffsetDateTime expiry =
        OffsetDateTime.ofInstant(clock.instant().plus(signedUrlTtl), ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.SECONDS);
    BlobServiceSasSignatureValues values =
        new BlobServiceSasSignatureValues(expiry, new BlobSasPermission().setReadPermission(true))
            .setProtocol(SasProtocol.HTTPS_ONLY);
    return URI.create(blob.getBlobUrl() + "?" + blob.generateSas(values));
  }

  private static BlobContainerClient buildContainerClient(StorageProperties properties) {
    StorageProperties.Azure azure =
        properties.azure() == null ? new StorageProperties.Azure(null, null) : properties.azure();
    String connectionString =
        StorageProperties.requireSetting(
            azure.connectionString(),
            "azure",
            "connection-string",
            "AZURE_STORAGE_CONNECTION_STRING");
    String containerName =
        StorageProperties.requireSetting(
            azure.container(), "azure", "container", "AZURE_STORAGE_CONTAINER");
    try {
      return new BlobServiceClientBuilder()
          .connectionString(connectionString)
          .retryOptions(RETRY_OPTIONS)
          .buildClient()
          .getBlobContainerClient(containerName);
    } catch (RuntimeException ex) {
      // Không kèm cause: message của SDK có thể chứa một phần connection string.
      throw new IllegalStateException(
          "AZURE_STORAGE_CONNECTION_STRING hoặc AZURE_STORAGE_CONTAINER không đúng định dạng");
    }
  }

  private void prepareContainer() {
    try {
      // Ký thử: phát hiện ngay connection string không có AccountKey hoặc AccountKey không phải Base64.
      sign(container.getBlobClient("startup-check"));
    } catch (RuntimeException ex) {
      throw new IllegalStateException(
          "AZURE_STORAGE_CONNECTION_STRING phải có AccountKey hợp lệ để ký link tải");
    }
    if (createContainerAndCheckPublic()) {
      throw new IllegalStateException(
          "Container AZURE_STORAGE_CONTAINER đang cho truy cập công khai, phải đặt về private");
    }
  }

  /** Tạo container private nếu chưa có; trả {@code true} khi container đang public. */
  private boolean createContainerAndCheckPublic() {
    try {
      // Không đặt access type: container mới là private.
      container.createIfNotExistsWithResponse(null, OPERATION_TIMEOUT, Context.NONE);
      return container
              .getPropertiesWithResponse(null, OPERATION_TIMEOUT, Context.NONE)
              .getValue()
              .getBlobPublicAccess()
          != null;
    } catch (RuntimeException ex) {
      if (ex instanceof BlobStorageException storageEx
          && storageEx.getStatusCode() >= 400
          && storageEx.getStatusCode() < 500) {
        throw new IllegalStateException(
            "Azure Blob Storage từ chối cấu hình storage (HTTP "
                + storageEx.getStatusCode()
                + ", "
                + storageEx.getErrorCode()
                + ")");
      }
      // UH-39: storage lỗi tạm thời lúc khởi động thì phần khác của app vẫn chạy.
      log.error("Storage azure: container check failed at startup", ex);
      return false;
    }
  }

  /** UH-39: ghi log lỗi kỹ thuật, trả 503 với thông báo chung; phần khác của app vẫn chạy. */
  private static BusinessException unavailable(String operation, Exception ex) {
    log.error("Storage {} failed", operation, ex);
    return new BusinessException(
        ErrorCode.STORAGE_UNAVAILABLE, "Dịch vụ lưu trữ file tạm thời không khả dụng.");
  }
}
