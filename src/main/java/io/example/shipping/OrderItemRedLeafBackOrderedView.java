package io.example.shipping;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import io.example.shipping.OrderItemRedLeafEntity.OrderItemRedLeafId;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;

@ViewId("orderItemRedLeafBackOrdered")
@Table("order_item_red_leaf_back_ordered")
@Subscribe.EventSourcedEntity(value = OrderItemRedLeafEntity.class, ignoreUnknown = true)
public class OrderItemRedLeafBackOrderedView extends View<OrderItemRedLeafBackOrderedView.OrderItemRedLeafRow> {
  private static final Logger log = LoggerFactory.getLogger(OrderItemRedLeafBackOrderedView.class);

  @GetMapping("/order-item-red-leaf-back-ordered/{skuId}")
  @Query("""
      SELECT * AS orderItemRedLeafRows
        FROM order_item_red_leaf_back_ordered
       LIMIT 100
       WHERE skuId = :skuId
         AND backOrdered = true
      """)
  public OrderItemRedLeafRows getOrderItemRedLeafBackOrdered(@PathVariable String skuId) {
    return null;
  }

  public UpdateEffect<OrderItemRedLeafRow> on(OrderItemRedLeafEntity.OrderItemRedLeafCreatedEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new OrderItemRedLeafRow(event.orderItemRedLeafId(), event.orderItemRedLeafId().skuId(), false));
  }

  public UpdateEffect<OrderItemRedLeafRow> on(OrderItemRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new OrderItemRedLeafRow(event.orderItemRedLeafId(), event.orderItemRedLeafId().skuId(), event.backOrderedAt() != null));
  }

  public UpdateEffect<OrderItemRedLeafRow> on(OrderItemRedLeafEntity.StockOrderReleasedOrderSkuItemsEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new OrderItemRedLeafRow(event.orderItemRedLeafId(), event.orderItemRedLeafId().skuId(), false));
  }

  public UpdateEffect<OrderItemRedLeafRow> on(OrderItemRedLeafEntity.OrderItemConsumedStockSkuItemsEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new OrderItemRedLeafRow(event.orderItemRedLeafId(), event.orderItemRedLeafId().skuId(), event.backOrderedAt() != null));
  }

  public UpdateEffect<OrderItemRedLeafRow> on(OrderItemRedLeafEntity.OrderItemReleasedStockSkuItemsEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new OrderItemRedLeafRow(event.orderItemRedLeafId(), event.orderItemRedLeafId().skuId(), false));
  }

  public record OrderItemRedLeafRow(OrderItemRedLeafId orderItemRedLeafId, String skuId, boolean backOrdered) {}

  public record OrderItemRedLeafRows(List<OrderItemRedLeafRow> orderItemRedLeafRows) {}
}