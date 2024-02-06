package io.example.order;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import kalix.javasdk.view.View;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;

@ViewId("orders-by-customer")
@Table("orders_by_customer")
@Subscribe.EventSourcedEntity(value = OrderEntity.class, ignoreUnknown = true)
public class OrdersByCustomerView extends View<OrderEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(OrdersByCustomerView.class);

  @GetMapping("/orders-by-customer/{customerId}")
  @Query("""
        SELECT * AS orders
          FROM orders_by_customer
         WHERE customerId = :customerId
      ORDER BY orderedAt DESC
         LIMIT 100
        """)
  public Orders getOrdersByCustomer(@PathVariable String customerId) {
    return null;
  }

  @Override
  public OrderEntity.State emptyState() {
    return OrderEntity.State.emptyState();
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.CreatedOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects().updateState(viewState().on(event));
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.OrderItemUpdatedEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects().updateState(viewState().on(event));
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.DeliveredOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects().updateState(viewState().on(event));
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.ReturnedOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects().updateState(viewState().on(event));
  }

  public UpdateEffect<OrderEntity.State> on(OrderEntity.CanceledOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects().updateState(viewState().on(event));
  }

  public record Orders(List<OrderEntity.State> orders) {}
}
