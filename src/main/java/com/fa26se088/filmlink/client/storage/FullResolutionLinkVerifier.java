package com.fa26se088.filmlink.client.storage;

/**
 * Kiểm link file độ phân giải gốc do Film Lab gửi (UC26, BR-08): link phải truy cập được (trả 2xx)
 * trước khi lưu vào {@code archive_rolls}. File gốc không lưu trong storage của FilmLink, chỉ lưu
 * link.
 *
 * <p>Bản thật ({@link HttpFullResolutionLinkVerifier}) gửi request ra Internet tới host do người
 * dùng nhập, nên phải chống SSRF: chỉ {@code http}/{@code https}, chặn host phân giải ra địa chỉ
 * nội bộ, kiểm lại ở mỗi lần chuyển hướng. Test dùng mock của interface này, không gọi Internet.
 */
public interface FullResolutionLinkVerifier {

  /**
   * Kiểm link. Không ném exception với link sai / không truy cập được: trả kết quả kèm lý do để
   * domain gọi tự chọn mã lỗi.
   *
   * @param url link người dùng gửi (đã qua Bean Validation {@code ^https?://})
   */
  LinkVerification verify(String url);

  /**
   * Kết quả kiểm link.
   *
   * @param reachable {@code true} khi link trả 2xx (sau tối đa 3 lần chuyển hướng)
   * @param reason lý do không hợp lệ, tiếng Việt, hiển thị được cho người dùng; {@code null} khi
   *     hợp lệ
   */
  record LinkVerification(boolean reachable, String reason) {

    public static LinkVerification ok() {
      return new LinkVerification(true, null);
    }

    public static LinkVerification failed(String reason) {
      return new LinkVerification(false, reason);
    }
  }
}
