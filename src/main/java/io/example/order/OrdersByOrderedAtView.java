package io.example.order;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import kalix.javasdk.view.View;
import kalix.springsdk.annotations.Query;
import kalix.springsdk.annotations.Subscribe;
import kalix.springsdk.annotations.Table;
import kalix.springsdk.annotations.ViewId;

@ViewId("orders-by-ordered-at")
@Table("orders_by_ordered_at")
@Subscribe.EventSourcedEntity(value = OrderEntity.class, ignoreUnknown = true)
public class OrdersByOrderedAtView extends View<OrderEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(OrdersByOrderedAtView.class);

  @PostMapping("/orders-by-ordered-at")
  @Query("""
      SELECT * AS orders, next_page_token() AS nextPageToken, has_more() AS hasMore
        FROM orders_by_ordered_at
       WHERE orderedAt >= :orderedAtFrom
         AND orderedAt < :orderedAtTo
      OFFSET page_token_offset(:nextPageToken)
       LIMIT 100
      """)
  public Orders getOrdersByOrderedAt(@RequestBody QueryRequest queryRequest, @RequestParam String nextPageToken) {
    return null;
  }

  @Override
  public OrderEntity.State emptyState() {
    return OrderEntity.State.emptyState();
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.CreatedOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.ReadyToShipOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.ReleasedOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.BackOrderedOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.ReadyToShipOrderItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.ReleasedOrderItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.BackOrderedOrderItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.DeliveredOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.ReturnedOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.CanceledOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public record QueryRequest(Instant orderedAtFrom, Instant orderedAtTo) {}

  public record Orders(List<OrderEntity.State> orders, String nextPageToken, boolean hasMore) {}
}
