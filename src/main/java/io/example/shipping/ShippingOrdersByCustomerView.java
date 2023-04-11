package io.example.shipping;

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

@ViewId("shipping-orders-by-customer")
@Table("shipping_orders_by_customer")
@Subscribe.EventSourcedEntity(value = ShippingOrderEntity.class, ignoreUnknown = true)
public class ShippingOrdersByCustomerView extends View<ShippingOrderEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(ShippingOrdersByCustomerView.class);

  @GetMapping("/shipping-orders-by-customer/{customerId}")
  @Query("""
        SELECT * AS shippingOrders
          FROM shipping_orders_by_customer
         WHERE customerId = :customerId
      ORDER BY orderedAt DESC
        """)
  public ShippingOrders getShippingOrdersByCustomer(@PathVariable String customerId) {
    return null;
  }

  @Override
  public ShippingOrderEntity.State emptyState() {
    return ShippingOrderEntity.State.emptyState();
  }

  public UpdateEffect<ShippingOrderEntity.State> on(ShippingOrderEntity.CreatedOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ShippingOrderEntity.State> on(ShippingOrderEntity.ReadyToShipOrderSkuItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ShippingOrderEntity.State> on(ShippingOrderEntity.ReleasedOrderSkuItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ShippingOrderEntity.State> on(ShippingOrderEntity.BackOrderedOrderSkuItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);

    return effects()
        .updateState(viewState().on(event));
  }

  public record ShippingOrders(List<ShippingOrderEntity.State> shippingOrders) {}
}
