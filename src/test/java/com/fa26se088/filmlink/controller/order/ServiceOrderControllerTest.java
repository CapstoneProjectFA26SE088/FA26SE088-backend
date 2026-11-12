package com.fa26se088.filmlink.controller.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fa26se088.filmlink.dto.order.request.CreateServiceOrderRequest;
import com.fa26se088.filmlink.dto.order.request.FilmRollItemRequest;
import com.fa26se088.filmlink.dto.order.response.ServiceOrderResponse;
import com.fa26se088.filmlink.entity.account.enums.UserAccountRole;
import com.fa26se088.filmlink.entity.order.ServiceOrder;
import com.fa26se088.filmlink.entity.order.enums.FilmHandoverMethod;
import com.fa26se088.filmlink.entity.order.enums.OrderPaymentMethod;
import com.fa26se088.filmlink.entity.order.enums.ServiceOrderStatus;
import com.fa26se088.filmlink.entity.order.enums.ServiceTier;
import com.fa26se088.filmlink.exception.GlobalExceptionHandler;
import com.fa26se088.filmlink.mapper.order.ServiceOrderMapper;
import com.fa26se088.filmlink.security.AuthenticatedAccount;
import com.fa26se088.filmlink.service.order.ServiceOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class ServiceOrderControllerTest {

  @Mock private ServiceOrderService orderService;

  private final ServiceOrderMapper orderMapper = new ServiceOrderMapper();

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  private final AuthenticatedAccount currentAccount =
      new AuthenticatedAccount(10L, "photographer@test.com", UserAccountRole.PHOTOGRAPHER);

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.findAndRegisterModules();

    HandlerMethodArgumentResolver authPrincipalResolver = new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
      }

      @Override
      public Object resolveArgument(MethodParameter parameter,
                                    ModelAndViewContainer mavContainer,
                                    NativeWebRequest webRequest,
                                    WebDataBinderFactory binderFactory) {
        return currentAccount;
      }
    };

    mockMvc = MockMvcBuilders
        .standaloneSetup(new ServiceOrderController(orderService))
        .setControllerAdvice(new GlobalExceptionHandler())
        .setCustomArgumentResolvers(authPrincipalResolver)
        .build();
  }

  private ServiceOrderResponse createSampleResponse(Long id, String code, ServiceOrderStatus status) {
    ServiceOrder order = new ServiceOrder();
    ReflectionTestUtils.setField(order, "id", id);
    order.setOrderCode(code);
    order.setIdempotencyKey(UUID.randomUUID());
    order.setPhotographerAccountId(10L);
    order.setFilmLabId(1L);
    order.setStatus(status);
    order.setServiceTier(ServiceTier.STANDARD);
    order.setFilmHandoverMethod(FilmHandoverMethod.SELF_DROP_OFF);
    order.setPaymentMethod(OrderPaymentMethod.ONLINE);
    order.setEstimatedTotalVnd(120000L);
    order.setExpectedReturnAt(Instant.now());
    ReflectionTestUtils.setField(order, "createdAt", Instant.now());
    ReflectionTestUtils.setField(order, "updatedAt", Instant.now());
    return orderMapper.toResponse(order);
  }

  @Test
  @DisplayName("POST /api/v1/service-orders tạo đơn thành công trả về 201 Created")
  void createOrder_success() throws Exception {
    UUID key = UUID.randomUUID();
    CreateServiceOrderRequest request = new CreateServiceOrderRequest(
        key,
        1L,
        ServiceTier.STANDARD,
        FilmHandoverMethod.SELF_DROP_OFF,
        OrderPaymentMethod.ONLINE,
        "Yêu cầu đặc biệt",
        "123 Nguyen Hue",
        "Quan 1",
        "TP HCM",
        "0901234567",
        null,
        null,
        false,
        List.of(new FilmRollItemRequest("35mm Color", "C-41", 120000L, "Ghi chú"))
    );

    ServiceOrderResponse response = createSampleResponse(100L, "FL-ABC12345", ServiceOrderStatus.AWAITING_PAYMENT);
    when(orderService.createOrder(any(), any())).thenReturn(response);

    mockMvc.perform(
        post("/api/v1/service-orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(100L))
        .andExpect(jsonPath("$.orderCode").value("FL-ABC12345"))
        .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
        .andExpect(jsonPath("$.estimatedTotalVnd").value(120000L));
  }

  @Test
  @DisplayName("GET /api/v1/service-orders/{id} lấy thông tin đơn thành công")
  void getOrderById_success() throws Exception {
    ServiceOrderResponse response = createSampleResponse(100L, "FL-ABC12345", ServiceOrderStatus.AWAITING_PAYMENT);

    when(orderService.getOrderById(eq(100L), any())).thenReturn(response);

    mockMvc.perform(get("/api/v1/service-orders/100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(100L))
        .andExpect(jsonPath("$.orderCode").value("FL-ABC12345"));
  }
}
