package io.example.shipping;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import kalix.javasdk.view.View;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;

@ViewId("shipping-orders-by-ordered-at")
@Table("shipping_orders_by_ordered_at")
@Subscribe.EventSourcedEntity(value = ShippingOrderEntity.class, ignoreUnknown = true)
public class ShippingOrdersByOrderedAtView extends View<ShippingOrderEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(ShippingOrdersByOrderedAtView.class);

  @PostMapping("/shipping-orders-by-ordered-at")
  @Query("""
      SELECT * AS shippingOrders, next_page_token() AS nextPageToken, has_more() AS hasMore
        FROM shipping_orders_by_ordered_at
      OFFSET page_token_offset(:nextPageToken)
       LIMIT 100
       WHERE orderedAt >= :orderedAtFrom
         AND orderedAt < :orderedAtTo
      """)
  public ShippingOrders getShippingOrdersByOrderedAt(@RequestBody QueryRequest query, @RequestParam String nextPageToken) {
    return null;
  }

  @Override
  public ShippingOrderEntity.State emptyState() {
    return ShippingOrderEntity.State.emptyState();
  }

  public UpdateEffect<ShippingOrderEntity.State> on(ShippingOrderEntity.CreatedShippingOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ShippingOrderEntity.State> on(ShippingOrderEntity.ReadyToShipOrderItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ShippingOrderEntity.State> on(ShippingOrderEntity.ReadyToShipOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ShippingOrderEntity.State> on(ShippingOrderEntity.ReleasedOrderItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ShippingOrderEntity.State> on(ShippingOrderEntity.ReleasedOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ShippingOrderEntity.State> on(ShippingOrderEntity.BackOrderedOrderItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ShippingOrderEntity.State> on(ShippingOrderEntity.BackOrderedOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public record QueryRequest(Instant orderedAtFrom, Instant orderedAtTo) {}

  public record ShippingOrders(List<ShippingOrderEntity.State> shippingOrders, String nextPageToken, boolean hasMore) {}
}
