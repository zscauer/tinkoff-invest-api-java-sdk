package ru.tinkoff.piapi.core;

import ru.tinkoff.piapi.core.utils.DateUtils;
import ru.tinkoff.piapi.core.utils.Helpers;
import ru.tinkoff.piapi.core.utils.ValidationUtils;
import ru.tinkoff.piapi.contract.v1.*;
import ru.tinkoff.piapi.contract.v1.OrdersServiceGrpc.OrdersServiceBlockingStub;
import ru.tinkoff.piapi.contract.v1.OrdersServiceGrpc.OrdersServiceStub;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class OrdersService {
  private final OrdersServiceBlockingStub ordersBlockingStub;
  private final OrdersServiceStub ordersStub;
  private final boolean readonlyMode;

  OrdersService(@Nonnull OrdersServiceBlockingStub ordersBlockingStub,
                @Nonnull OrdersServiceStub ordersStub,
                boolean readonlyMode) {
    this.ordersBlockingStub = ordersBlockingStub;
    this.ordersStub = ordersStub;
    this.readonlyMode = readonlyMode;
  }


  /**
   *
   * @param instrumentId figi / instrument_uid инструмента
   * @param quantity количество лотов
   * @param price цена (для лимитной заявки)
   * @param direction покупка/продажа
   * @param accountId id аккаунта
   * @param type рыночная / лимитная заявка
   * @param orderId уникальный идентификатор заявки
   * @return
   */
  @Nonnull
  public PostOrderResponse postOrderSync(@Nonnull String instrumentId,
                                         long quantity,
                                         @Nonnull Quotation price,
                                         @Nonnull OrderDirection direction,
                                         @Nonnull String accountId,
                                         @Nonnull OrderType type,
                                         @Nullable String orderId) {
    ValidationUtils.checkReadonly(readonlyMode);
    var finalOrderId = orderId == null ? UUID.randomUUID().toString() : orderId;

    return Helpers.unaryCall(() -> ordersBlockingStub.postOrder(
      PostOrderRequest.newBuilder()
        .setInstrumentId(instrumentId)
        .setQuantity(quantity)
        .setPrice(price)
        .setDirection(direction)
        .setAccountId(accountId)
        .setOrderType(type)
        .setOrderId(Helpers.preprocessInputOrderId(finalOrderId))
        .build()));
  }

  @Nonnull
  public Instant cancelOrderSync(@Nonnull String accountId,
                                 @Nonnull String orderId) {
    ValidationUtils.checkReadonly(readonlyMode);

    var responseTime = Helpers.unaryCall(() -> ordersBlockingStub.cancelOrder(
        CancelOrderRequest.newBuilder()
          .setAccountId(accountId)
          .setOrderId(orderId)
          .build())
      .getTime());

    return DateUtils.timestampToInstant(responseTime);
  }

  @Nonnull
  public OrderState getOrderStateSync(@Nonnull String accountId,
                                      @Nonnull String orderId) {
    return Helpers.unaryCall(() -> ordersBlockingStub.getOrderState(
      GetOrderStateRequest.newBuilder()
        .setAccountId(accountId)
        .setOrderId(orderId)
        .build()));
  }

  @Nonnull
  public List<OrderState> getOrdersSync(@Nonnull String accountId) {
    return Helpers.unaryCall(() -> ordersBlockingStub.getOrders(
        GetOrdersRequest.newBuilder()
          .setAccountId(accountId)
          .build())
      .getOrdersList());
  }

  /**
   *
   * @param instrumentId figi / instrument_uid инструмента
   * @param quantity количество лотов
   * @param price цена (для лимитной заявки)
   * @param direction покупка/продажа
   * @param accountId id аккаунта
   * @param type рыночная / лимитная заявка
   * @param orderId уникальный идентификатор заявки
   * @return
   */
  @Nonnull
  public CompletableFuture<PostOrderResponse> postOrder(@Nonnull String instrumentId,
                                                        long quantity,
                                                        @Nonnull Quotation price,
                                                        @Nonnull OrderDirection direction,
                                                        @Nonnull String accountId,
                                                        @Nonnull OrderType type,
                                                        @Nullable String orderId) {
    ValidationUtils.checkReadonly(readonlyMode);
    var finalOrderId = orderId == null ? UUID.randomUUID().toString() : orderId;

    return Helpers.unaryAsyncCall(
      observer -> ordersStub.postOrder(
        PostOrderRequest.newBuilder()
          .setInstrumentId(instrumentId)
          .setQuantity(quantity)
          .setPrice(price)
          .setDirection(direction)
          .setAccountId(accountId)
          .setOrderType(type)
          .setOrderId(Helpers.preprocessInputOrderId(finalOrderId))
          .build(),
        observer));
  }

  @Nonnull
  public CompletableFuture<Instant> cancelOrder(@Nonnull String accountId,
                                                @Nonnull String orderId) {
    ValidationUtils.checkReadonly(readonlyMode);

    return Helpers.<CancelOrderResponse>unaryAsyncCall(
        observer -> ordersStub.cancelOrder(
          CancelOrderRequest.newBuilder()
            .setAccountId(accountId)
            .setOrderId(orderId)
            .build(),
          observer))
      .thenApply(response -> DateUtils.timestampToInstant(response.getTime()));
  }

  @Nonnull
  public CompletableFuture<OrderState> getOrderState(@Nonnull String accountId,
                                                     @Nonnull String orderId) {
    return Helpers.unaryAsyncCall(
      observer -> ordersStub.getOrderState(
        GetOrderStateRequest.newBuilder()
          .setAccountId(accountId)
          .setOrderId(orderId)
          .build(),
        observer));
  }

  @Nonnull
  public CompletableFuture<List<OrderState>> getOrders(@Nonnull String accountId) {
    return Helpers.<GetOrdersResponse>unaryAsyncCall(
        observer -> ordersStub.getOrders(
          GetOrdersRequest.newBuilder()
            .setAccountId(accountId)
            .build(),
          observer))
      .thenApply(GetOrdersResponse::getOrdersList);
  }

  /** Последовательное выполнение 2 операций - отмены и выставления нового ордера
   *
   * @param accountId Номер счета
   * @param quantity Количество лотов
   * @param price Цена за 1 инструмент
   * @param idempotencyKey Новый идентификатор запроса выставления поручения для целей идемпотентности. Максимальная длина 36 символов. Перезатирает старый ключ
   * @param orderId Идентификатор заявки на бирже
   * @param priceType Тип цены. Пока не используется (можно передавать null)
   * @return Информация о выставлении поручения
   */
  @Nonnull
  public CompletableFuture<PostOrderResponse> replaceOrder(@Nonnull String accountId,
                                                           long quantity,
                                                           @Nonnull Quotation price,
                                                           @Nullable String idempotencyKey,
                                                           @Nonnull String orderId,
                                                           @Nullable PriceType priceType) {
    var request = ReplaceOrderRequest.newBuilder()
      .setAccountId(accountId)
      .setPrice(price)
      .setQuantity(quantity)
      .setIdempotencyKey(idempotencyKey == null ? "" : idempotencyKey)
      .setOrderId(orderId)
      .setPriceType(priceType == null ? PriceType.PRICE_TYPE_UNSPECIFIED : priceType)
      .build();
    return Helpers.unaryAsyncCall(
      observer -> ordersStub.replaceOrder(request, observer));
  }

  /** Последовательное выполнение 2 операций - отмены и выставления нового ордера
   *
   * @param accountId Номер счета
   * @param quantity Количество лотов
   * @param price Цена за 1 инструмент
   * @param idempotencyKey Новый идентификатор запроса выставления поручения для целей идемпотентности. Максимальная длина 36 символов. Перезатирает старый ключ
   * @param orderId Идентификатор заявки на бирже
   * @param priceType Тип цены. Пока не используется (можно передавать null)
   * @return Информация о выставлении поручения
   */
  @Nonnull
  public PostOrderResponse replaceOrderSync(@Nonnull String accountId,
                                            long quantity,
                                            @Nonnull Quotation price,
                                            @Nullable String idempotencyKey,
                                            @Nonnull String orderId,
                                            @Nullable PriceType priceType) {
    var request = ReplaceOrderRequest.newBuilder()
      .setAccountId(accountId)
      .setPrice(price)
      .setQuantity(quantity)
      .setIdempotencyKey(idempotencyKey == null ? "" : idempotencyKey)
      .setOrderId(orderId)
      .setPriceType(priceType == null ? PriceType.PRICE_TYPE_UNSPECIFIED : priceType)
      .build();
    return Helpers.unaryCall(() -> ordersBlockingStub.replaceOrder(request));
  }
}
