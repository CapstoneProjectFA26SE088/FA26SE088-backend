package com.fa26se088.filmlink.controller.payment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.dto.payment.response.PaymentResponse;
import com.fa26se088.filmlink.dto.payment.response.PaymentReturnResponse;
import com.fa26se088.filmlink.dto.payment.response.VnPayIpnResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.payment.enums.PaymentDirection;
import com.fa26se088.filmlink.entity.payment.enums.PaymentMethod;
import com.fa26se088.filmlink.entity.payment.enums.PaymentPurpose;
import com.fa26se088.filmlink.entity.payment.enums.PaymentStatus;
import com.fa26se088.filmlink.exception.GlobalExceptionHandler;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.payment.PaymentService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

  private MockMvc mockMvc;

  @Mock private PaymentService paymentService;

  private AuthenticatedAccount currentAccount;

  @BeforeEach
  void setUp() {
    currentAccount = new AuthenticatedAccount(100L, "photographer@example.com", UserAccountRole.PHOTOGRAPHER);

    HandlerMethodArgumentResolver authPrincipalResolver =
        new HandlerMethodArgumentResolver() {
          @Override
          public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
          }

          @Override
          public Object resolveArgument(
              MethodParameter parameter,
              ModelAndViewContainer mavContainer,
              NativeWebRequest webRequest,
              WebDataBinderFactory binderFactory) {
            return currentAccount;
          }
        };

    PaymentController controller = new PaymentController(paymentService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setCustomArgumentResolvers(authPrincipalResolver)
            .build();
  }

  private PaymentResponse samplePaymentResponse() {
    return PaymentResponse.builder()
        .id(1L)
        .transactionCode("TXN123456789")
        .purpose(PaymentPurpose.ORDER_PAYMENT)
        .direction(PaymentDirection.IN)
        .method(PaymentMethod.VNPAY)
        .status(PaymentStatus.PENDING)
        .amountVnd(250_000L)
        .serviceOrderId(10L)
        .counterpartyAccountId(100L)
        .paymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?token=123")
        .createdAt(Instant.now())
        .build();
  }

  @Test
  @DisplayName("POST /api/v1/payments/vnpay/ipn thành công trả RspCode 00")
  void processVnPayIpnPost_success_returnsRspCode00() throws Exception {
    when(paymentService.processVnPayIpn(any())).thenReturn(VnPayIpnResponse.success());

    mockMvc
        .perform(
            post("/api/v1/payments/vnpay/ipn")
                .param("vnp_TxnRef", "TXN123456789")
                .param("vnp_Amount", "25000000")
                .param("vnp_ResponseCode", "00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.RspCode").value("00"))
        .andExpect(jsonPath("$.Message").value("Confirm Success"));
  }

  @Test
  @DisplayName("GET /api/v1/payments/vnpay/return hiển thị kết quả thành công")
  void processVnPayReturn_success_returns200() throws Exception {
    PaymentReturnResponse returnResponse =
        PaymentReturnResponse.builder()
            .transactionCode("TXN123456789")
            .success(true)
            .responseCode("00")
            .message("Thanh toán thành công")
            .amountVnd(250_000L)
            .serviceOrderId(10L)
            .build();

    when(paymentService.processVnPayReturn(any())).thenReturn(returnResponse);

    mockMvc
        .perform(
            get("/api/v1/payments/vnpay/return")
                .param("vnp_TxnRef", "TXN123456789")
                .param("vnp_ResponseCode", "00"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionCode").value("TXN123456789"))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.amountVnd").value(250000));
  }

  @Test
  @DisplayName("GET /api/v1/payments/{id} thành công trả 200")
  void getPaymentById_success_returns200() throws Exception {
    when(paymentService.getPaymentById(eq(1L), any())).thenReturn(samplePaymentResponse());

    mockMvc
        .perform(get("/api/v1/payments/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.transactionCode").value("TXN123456789"));
  }

  @Test
  @DisplayName("POST /api/v1/payments đã bỏ (tạo link thuộc order, T-04) -> không còn handler")
  void createPaymentEndpoint_removed() throws Exception {
    mockMvc.perform(post("/api/v1/payments")).andExpect(status().is4xxClientError());
  }

  @Test
  @DisplayName("GET /api/v1/payments/by-order/{id} trả lịch sử thanh toán của đơn")
  void getPaymentsByOrderId_success_returns200() throws Exception {
    when(paymentService.getPaymentsByOrderId(eq(10L), any()))
        .thenReturn(List.of(samplePaymentResponse()));

    mockMvc
        .perform(get("/api/v1/payments/by-order/10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].serviceOrderId").value(10))
        .andExpect(jsonPath("$[0].transactionCode").value("TXN123456789"));
  }
}
