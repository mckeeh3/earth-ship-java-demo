package io.example.shipping;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import kalix.javasdk.view.View;
import kalix.springsdk.annotations.Query;
import kalix.springsdk.annotations.Subscribe;
import kalix.springsdk.annotations.Table;
import kalix.springsdk.annotations.ViewId;

@ViewId("order-sku-items-back-ordered")
@Table("order_sku_items_back_ordered")
@Subscribe.EventSourcedEntity(value = OrderSkuItemEntity.class, ignoreUnknown = true)
public class OrderSkuItemsBackOrderedView extends View<OrderSkuItemsBackOrderedView.OrderSkuItemRow> {
  private static final Logger log = LoggerFactory.getLogger(OrderSkuItemsBackOrderedView.class);

  @GetMapping("/order-sku-items-back-ordered/{skuId}")
  @Query("""
      SELECT * AS orderSkuItemRows
        FROM order_sku_items_back_ordered
       LIMIT 100
       WHERE skuId = :skuId
         AND backOrdered = true
      """)
  public OrderSkuItemRows getOrderSkuItemsBackOrdered(@PathVariable String skuId) {
    return null;
  }

  public UpdateEffect<OrderSkuItemRow> on(OrderSkuItemEntity.CreatedOrderSkuItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new OrderSkuItemRow(event.orderSkuItemId(), event.skuId(), null, false, null));
  }

  public UpdateEffect<OrderSkuItemRow> on(OrderSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new OrderSkuItemRow(viewState().orderSkuItemId(), viewState().skuId(), event.readyToShipAt().toString(), false, null));
  }

  public UpdateEffect<OrderSkuItemRow> on(OrderSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new OrderSkuItemRow(viewState().orderSkuItemId(), viewState().skuId(), event.readyToShipAt().toString(), false, null));
  }

  public UpdateEffect<OrderSkuItemRow> on(OrderSkuItemEntity.BackOrderedSkuItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new OrderSkuItemRow(viewState().orderSkuItemId(), viewState().skuId(), null, true, event.backOrderedAt().toString()));
  }

  public record OrderSkuItemRow(OrderSkuItemId orderSkuItemId, String skuId, String readyToShipAt, boolean backOrdered, String backOrderAt) {}

  public record OrderSkuItemRows(List<OrderSkuItemRow> orderSkuItemRows) {}
}
