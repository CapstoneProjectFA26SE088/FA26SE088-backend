package com.fa26se088.filmlink.service.marketplace;

import com.fa26se088.filmlink.dto.marketplace.request.CreatePriceOfferRequest;
import com.fa26se088.filmlink.dto.marketplace.request.PublishConsignmentListingRequest;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentListingDetailResponse;
import com.fa26se088.filmlink.dto.marketplace.response.ConsignmentListingSummaryResponse;
import com.fa26se088.filmlink.dto.marketplace.response.PriceOfferResponse;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Đề xuất giá, chốt giá ký gửi và xuất bản tin đăng công khai (F-21, UC47, UC48, UC61). */
public interface ConsignmentListingService {

  /** Lab gửi đề xuất giá bán lần đầu sau khi kiểm định ACCEPTED (UC47). */
  PriceOfferResponse createInitialOffer(
      Long itemId, CreatePriceOfferRequest request, AuthenticatedAccount ownerAccount);

  /** Người bán đồng ý đề xuất giá của Lab (UC47, BR-52). */
  PriceOfferResponse acceptOffer(Long offerId, AuthenticatedAccount sellerAccount);

  /** Lab xuất bản tin đăng bán công khai kèm biên bản kiểm định (UC48, BR-51, BR-53). */
  ConsignmentListingDetailResponse publishListing(
      Long itemId, PublishConsignmentListingRequest request, AuthenticatedAccount ownerAccount);

  /** Danh sách tin đăng ký gửi đang mở bán công khai (UC61). */
  Page<ConsignmentListingSummaryResponse> listPublicListings(Pageable pageable);

  /** Chi tiết tin đăng ký gửi công khai kèm Condition Report (UC61). */
  ConsignmentListingDetailResponse getPublicListingDetail(Long listingId);
}
