package com.fa26se088.filmlink.controller.lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.client.storage.StorageClient;
import com.fa26se088.filmlink.client.storage.StoragePurpose;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.repository.account.UserAccountRepository;
import com.fa26se088.filmlink.service.lab.impl.FilmLabFixture;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code /api/v1/public/film-labs} trên DB thật (schema test): tìm lab và trang lab công khai
 * (UC05, UC17, BR-34). Mọi request gửi <b>không có token</b>. Máy dựng bằng SQL trong fixture và
 * được kiểm bằng {@code SqlMachineReadiness} thật.
 *
 * <p>Toạ độ quanh điểm gốc (21.028500, 105.854200); lệch vĩ độ 0.008993° ≈ 1 km. Schema test có thể
 * còn lab của lần chạy khác, nên các assert về danh sách chỉ xét các lab do test này tạo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicFilmLabControllerIntegrationTest {

  private static final String PATH = "/api/v1/public/film-labs";
  private static final String ORIGIN_LAT = "21.028500";
  private static final String ORIGIN_LNG = "105.854200";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private UserAccountRepository userAccountRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private StorageClient storageClient;

  private FilmLabFixture fixture;

  @BeforeEach
  void setUp() {
    fixture =
        new FilmLabFixture(
            jdbcTemplate, userAccountRepository, eventPublisher, transactionTemplate, storageClient);
  }

  @AfterEach
  void cleanUp() {
    fixture.cleanUp();
  }

  // --- Tìm lab (UC17) ---

  /**
   * TC-F07-01 (Report 5): 5 lab có toạ độ. Tìm khổ 120, quy trình E-6, bán kính 10 km, sắp theo
   * giá → chỉ lab khớp và trong bán kính, rẻ nhất trước; 20 dòng/trang.
   *
   * <ul>
   *   <li>A: 2 km, E-6/120 150.000 → có.
   *   <li>B: 5 km, E-6/120 90.000 (+ C-41/135 30.000 không khớp lọc) → có, giá 90.000.
   *   <li>C: 8 km, E-6/120 120.000 (+ E-6/120 WITHDRAWN 50.000) → có, giá 120.000.
   *   <li>D: 30 km, E-6/120 80.000 → ngoài bán kính.
   *   <li>E: 3 km, chỉ C-41/120 → sai quy trình.
   * </ul>
   */
  @Test
  @DisplayName("TC-F07-01 Search format 120, process E-6, radius 10 km, sort by price")
  void tcF0701_searchByFormatProcessRadiusSortedByPrice() throws Exception {
    long labA = visibleLab("Lab A", 2);
    fixture.insertPackage(labA, "E-6 120 A", "DEVELOPING", "E6", "120", 150_000, "ON_SALE");
    long labB = visibleLab("Lab B", 5);
    fixture.insertPackage(labB, "E-6 120 B", "DEVELOPING", "E6", "120", 90_000, "ON_SALE");
    fixture.insertPackage(labB, "C-41 135 B", "DEVELOPING", "C41", "135", 30_000, "ON_SALE");
    long labC = visibleLab("Lab C", 8);
    fixture.insertPackage(labC, "E-6 120 C", "DEVELOPING", "E6", "120", 120_000, "ON_SALE");
    fixture.insertPackage(labC, "E-6 120 C cũ", "DEVELOPING", "E6", "120", 50_000, "WITHDRAWN");
    long labD = visibleLab("Lab D", 30);
    fixture.insertPackage(labD, "E-6 120 D", "DEVELOPING", "E6", "120", 80_000, "ON_SALE");
    long labE = visibleLab("Lab E", 3);
    fixture.insertPackage(labE, "C-41 120 E", "DEVELOPING", "C41", "120", 70_000, "ON_SALE");

    String body =
        mockMvc
            .perform(
                get(PATH)
                    .param("filmFormatId", id("film_formats", "120"))
                    .param("filmProcessId", id("film_processes", "E6"))
                    .param("lat", ORIGIN_LAT)
                    .param("lng", ORIGIN_LNG)
                    .param("radiusKm", "10")
                    .param("sort", "price"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    assertThat(ownIds(body, Set.of(labA, labB, labC, labD, labE)))
        .containsExactly(labB, labC, labA);
    assertThat(priceOf(body, labB)).isEqualTo(90_000L);
    assertThat(priceOf(body, labC)).isEqualTo(120_000L);
    assertThat(priceOf(body, labA)).isEqualTo(150_000L);
    assertThat(distanceOf(body, labA)).isCloseTo(2.0, within(0.05));
  }

  @Test
  void search_sortByDistance_nearestFirst() throws Exception {
    long near = visibleLab("Lab Gần", 1);
    fixture.insertPackage(near, "E-6 120 Gần", "DEVELOPING", "E6", "120", 200_000, "ON_SALE");
    long far = visibleLab("Lab Xa", 6);
    fixture.insertPackage(far, "E-6 120 Xa", "DEVELOPING", "E6", "120", 100_000, "ON_SALE");

    String body =
        search(
            "filmProcessId", id("film_processes", "E6"),
            "lat", ORIGIN_LAT,
            "lng", ORIGIN_LNG,
            "radiusKm", "10",
            "sort", "distance");

    assertThat(ownIds(body, Set.of(near, far))).containsExactly(near, far);
  }

  /** BR-34: lab tắt nhận đơn, lab chỉ có gói WITHDRAWN, lab thiếu máy không hiện. */
  @Test
  void search_hidesLabsNotVisible() throws Exception {
    long visible = visibleLab("Lab Hiện", 1);
    fixture.insertPackage(visible, "E-6 120", "DEVELOPING", "E6", "120", 100_000, "ON_SALE");

    long notAccepting = visibleLab("Lab Tắt Nhận Đơn", 1);
    fixture.insertPackage(notAccepting, "E-6 120", "DEVELOPING", "E6", "120", 100_000, "ON_SALE");
    fixture.setAcceptingOrders(notAccepting, false);

    long onlyWithdrawn = visibleLab("Lab Hết Gói", 1);
    fixture.insertPackage(
        onlyWithdrawn, "E-6 120", "DEVELOPING", "E6", "120", 100_000, "WITHDRAWN");

    long noMachines = labAt("Lab Thiếu Máy", 1);
    fixture.insertPackage(noMachines, "E-6 120", "DEVELOPING", "E6", "120", 100_000, "ON_SALE");
    fixture.setAcceptingOrders(noMachines, true);

    String body = search("lat", ORIGIN_LAT, "lng", ORIGIN_LNG, "radiusKm", "5");

    assertThat(ownIds(body, Set.of(visible, notAccepting, onlyWithdrawn, noMachines)))
        .containsExactly(visible);
  }

  /** Lab không có toạ độ: hiện khi không lọc bán kính (khoảng cách null), bị loại khi có bán kính. */
  @Test
  void search_labWithoutCoordinates_excludedOnlyWhenRadiusGiven() throws Exception {
    long noCoordinates = labAt("Lab Không Toạ Độ", null);
    fixture.insertReadyMachines(noCoordinates);
    fixture.insertPackage(noCoordinates, "BW 135", "DEVELOPING", "BW", "135", 40_000, "ON_SALE");
    fixture.setAcceptingOrders(noCoordinates, true);

    String filmProcessId = id("film_processes", "BW");
    String withoutRadius =
        search("filmProcessId", filmProcessId, "lat", ORIGIN_LAT, "lng", ORIGIN_LNG);
    assertThat(ownIds(withoutRadius, Set.of(noCoordinates))).containsExactly(noCoordinates);
    assertThat(
            JsonPath.<List<Object>>read(
                withoutRadius, "$.content[?(@.id == " + noCoordinates + ")].distanceKm"))
        .containsExactly((Object) null);

    String withRadius =
        search(
            "filmProcessId", filmProcessId,
            "lat", ORIGIN_LAT,
            "lng", ORIGIN_LNG,
            "radiusKm", "50");
    assertThat(ownIds(withRadius, Set.of(noCoordinates))).isEmpty();
  }

  @Test
  void search_radiusWithoutCoordinates_returns400() throws Exception {
    mockMvc
        .perform(get(PATH).param("radiusKm", "10"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("radiusKm"));
  }

  @Test
  void search_sortByDistanceWithoutCoordinates_returns400() throws Exception {
    mockMvc
        .perform(get(PATH).param("sort", "distance"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
  }

  @Test
  void search_radiusOutOfRange_returns400() throws Exception {
    mockMvc
        .perform(
            get(PATH).param("lat", ORIGIN_LAT).param("lng", ORIGIN_LNG).param("radiusKm", "51"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
  }

  @Test
  void search_unknownSort_returns400() throws Exception {
    mockMvc
        .perform(get(PATH).param("sort", "rating"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
  }

  @Test
  void search_sizeAbove100_isCappedAt100() throws Exception {
    mockMvc
        .perform(get(PATH).param("size", "500"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(100));
  }

  // --- Trang lab (UC05) ---

  @Test
  void getLab_returnsProfileHoursImagesAndOnSalePackagesWithNames() throws Exception {
    long labId = visibleLab("Lab Trang Công Khai", 1);
    long onSale =
        fixture.insertPackage(labId, "E-6 120", "DEVELOPING", "E6", "120", 150_000, "ON_SALE");
    fixture.insertScanOption(onSale, "L", 50_000);
    fixture.insertScanOption(onSale, "S", 20_000);
    fixture.insertPackage(labId, "Gói cũ", "DEVELOPING", "C41", "135", 10_000, "WITHDRAWN");
    jdbcTemplate.update(
        "INSERT INTO film_lab_opening_hours (film_lab_id, day_of_week, is_closed, opens_at,"
            + " closes_at) VALUES (?, 1, false, '08:00', '18:00')",
        labId);
    insertImage(labId);

    mockMvc
        .perform(get(PATH + "/" + labId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(labId))
        .andExpect(jsonPath("$.labName").value("Lab Trang Công Khai"))
        .andExpect(jsonPath("$.qcBufferHours").doesNotExist())
        .andExpect(jsonPath("$.contactEmail").doesNotExist())
        .andExpect(jsonPath("$.contactPhone").doesNotExist())
        .andExpect(jsonPath("$.openingHours", hasSize(1)))
        .andExpect(jsonPath("$.openingHours[0].opensAt").value("08:00"))
        .andExpect(jsonPath("$.images", hasSize(1)))
        .andExpect(jsonPath("$.images[0].url").value(startsWith("http")))
        .andExpect(jsonPath("$.packages", hasSize(1)))
        .andExpect(jsonPath("$.packages[0].id").value(onSale))
        .andExpect(jsonPath("$.packages[0].priceVnd").value(150_000))
        .andExpect(jsonPath("$.packages[0].filmProcess.code").value("E6"))
        .andExpect(jsonPath("$.packages[0].filmProcess.displayName").isNotEmpty())
        .andExpect(jsonPath("$.packages[0].filmFormat.code").value("120"))
        .andExpect(jsonPath("$.packages[0].serviceType.code").value("DEVELOPING"))
        .andExpect(jsonPath("$.packages[0].status").doesNotExist())
        .andExpect(jsonPath("$.packages[0].scanOptions", hasSize(2)))
        .andExpect(jsonPath("$.packages[0].scanOptions[0].resolutionLabel").value("S"));
  }

  @Test
  void search_returnsCoverImageAndDistance() throws Exception {
    long labId = visibleLab("Lab Có Ảnh", 3);
    fixture.insertPackage(labId, "E-6 120", "DEVELOPING", "E6", "120", 150_000, "ON_SALE");
    insertImage(labId);

    mockMvc
        .perform(
            get(PATH)
                .param("filmProcessId", id("film_processes", "E6"))
                .param("lat", ORIGIN_LAT)
                .param("lng", ORIGIN_LNG)
                .param("radiusKm", "4"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.content[?(@.id == " + labId + ")].coverImageUrl")
                .value(contains(startsWith("http"))))
        .andExpect(
            jsonPath("$.content[?(@.id == " + labId + ")].distanceKm")
                .value(contains(closeTo(3.0, 0.05))));
  }

  /**
   * BR-16: gói ON_SALE có Film Process đã DISCONTINUED ({@code BW_REVERSAL}, seed sẵn) không hiện ở
   * trang lab, không khớp bộ lọc, không tính vào giá thấp nhất; lab chỉ còn gói như vậy thì ẩn.
   */
  @Test
  void packagesWithDiscontinuedMasterdata_areHidden() throws Exception {
    long mixed = visibleLab("Lab Có Gói Ngừng Dùng", 1);
    long active =
        fixture.insertPackage(mixed, "E-6 120", "DEVELOPING", "E6", "120", 200_000, "ON_SALE");
    fixture.insertPackage(mixed, "BW đảo 135", "DEVELOPING", "BW_REVERSAL", "135", 10_000, "ON_SALE");
    long onlyDiscontinued = visibleLab("Lab Chỉ Còn Gói Ngừng Dùng", 1);
    fixture.insertPackage(
        onlyDiscontinued, "BW đảo 135", "DEVELOPING", "BW_REVERSAL", "135", 10_000, "ON_SALE");
    Set<Long> own = Set.of(mixed, onlyDiscontinued);

    String all = search("lat", ORIGIN_LAT, "lng", ORIGIN_LNG, "radiusKm", "5");
    assertThat(ownIds(all, own)).containsExactly(mixed);
    assertThat(priceOf(all, mixed)).isEqualTo(200_000L);

    String byDiscontinued = search("filmProcessId", id("film_processes", "BW_REVERSAL"));
    assertThat(ownIds(byDiscontinued, own)).isEmpty();

    mockMvc
        .perform(get(PATH + "/" + mixed))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.packages", hasSize(1)))
        .andExpect(jsonPath("$.packages[0].id").value(active));
    mockMvc
        .perform(get(PATH + "/" + onlyDiscontinued))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.errorCode").value("LAB_NOT_FOUND"));
  }

  @Test
  void getLab_notVisible_returns404() throws Exception {
    long notAccepting = visibleLab("Lab Tắt Nhận Đơn", 1);
    fixture.insertPackage(notAccepting, "E-6 120", "DEVELOPING", "E6", "120", 100_000, "ON_SALE");
    fixture.setAcceptingOrders(notAccepting, false);

    long onlyWithdrawn = visibleLab("Lab Hết Gói", 1);
    fixture.insertPackage(
        onlyWithdrawn, "E-6 120", "DEVELOPING", "E6", "120", 100_000, "WITHDRAWN");

    for (long labId : List.of(notAccepting, onlyWithdrawn, Long.MAX_VALUE)) {
      mockMvc
          .perform(get(PATH + "/" + labId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.errorCode").value("LAB_NOT_FOUND"))
          .andExpect(jsonPath("$.packages").doesNotExist());
    }
  }

  // --- helpers ---

  /** Lab đủ máy, đang nhận đơn, cách điểm gốc {@code km} về phía bắc. Chưa có gói. */
  private long visibleLab(String name, int km) {
    long labId = labAt(name, km);
    fixture.insertReadyMachines(labId);
    fixture.setAcceptingOrders(labId, true);
    return labId;
  }

  /** Lab mới duyệt (chưa nhận đơn, chưa có máy); {@code km = null} thì không có toạ độ. */
  private long labAt(String name, Integer km) {
    long labId = fixture.createLab(fixture.createAccount(UserAccountRole.FILM_LAB_OWNER), name);
    if (km != null) {
      String latitude =
          new BigDecimal(ORIGIN_LAT)
              .add(BigDecimal.valueOf(km * 0.008993))
              .setScale(6, RoundingMode.HALF_UP)
              .toPlainString();
      fixture.setCoordinates(labId, latitude, ORIGIN_LNG);
    }
    return labId;
  }

  private void insertImage(long labId) {
    byte[] png = png();
    String blobKey =
        storageClient.upload(
            StoragePurpose.LAB_IMAGE, "sample.png", new ByteArrayInputStream(png), png.length);
    jdbcTemplate.update(
        "INSERT INTO film_lab_images (film_lab_id, blob_key, display_order) VALUES (?, ?, 1)",
        labId,
        blobKey);
  }

  private String id(String table, String code) {
    return String.valueOf(fixture.catalogId(table, code));
  }

  /** GET không token với các cặp tham số {@code name, value}; trả body JSON. */
  private String search(String... params) throws Exception {
    var request = get(PATH).param("size", "100");
    for (int i = 0; i < params.length; i += 2) {
      request.param(params[i], params[i + 1]);
    }
    return mockMvc
        .perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  /** Id các lab trong trang kết quả, giữ thứ tự, chỉ lấy lab thuộc {@code own}. */
  private static List<Long> ownIds(String body, Set<Long> own) {
    List<Number> ids = JsonPath.read(body, "$.content[*].id");
    return ids.stream().map(Number::longValue).filter(own::contains).toList();
  }

  private static long priceOf(String body, long labId) {
    List<Number> prices = JsonPath.read(body, "$.content[?(@.id == " + labId + ")].minPriceVnd");
    return prices.getFirst().longValue();
  }

  private static double distanceOf(String body, long labId) {
    List<Number> distances =
        JsonPath.read(body, "$.content[?(@.id == " + labId + ")].distanceKm");
    return distances.getFirst().doubleValue();
  }

  /** PNG tối thiểu: chỉ cần đúng magic bytes để storage nhận. */
  private static byte[] png() {
    byte[] content = new byte[256];
    byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    System.arraycopy(header, 0, content, 0, header.length);
    return content;
  }
}
