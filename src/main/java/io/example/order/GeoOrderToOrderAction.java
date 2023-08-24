package io.example.order;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.example.LogEvent;
import io.example.map.GeoOrderEntity;
import io.example.map.GeoOrderEntity.GeoOrderCreatedEvent;
import io.example.product.ProductEntity;
import io.example.product.ProductEntity.State;
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.client.ComponentClient;

@Subscribe.EventSourcedEntity(value = GeoOrderEntity.class, ignoreUnknown = true)
public class GeoOrderToOrderAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(GeoOrderToOrderAction.class);
  private final ComponentClient componentClient;

  public GeoOrderToOrderAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect<String> on(GeoOrderEntity.GeoOrderCreatedEvent event) {
    log.info("Event: {}", event);
    LogEvent.log("GeoOrder", event.geoOrderId(), "Order", event.geoOrderId(), "");

    return callFor(event);
  }

  private Effect<String> callFor(GeoOrderEntity.GeoOrderCreatedEvent event) {
    try {
      return effects().forward(
          componentClient.forEventSourcedEntity(event.geoOrderId())
              .call(OrderEntity::createOrder)
              .params(toCommand(event)));
    } catch (Exception e) {
      return effects().error(e.getMessage());
    }
  }

  private OrderEntity.CreateOrderCommand toCommand(GeoOrderCreatedEvent event) {
    var orderId = event.geoOrderId();
    var customerId = randomCustomerId();
    var items = orderItems(randomItemCount());

    return new OrderEntity.CreateOrderCommand(orderId, customerId, items);
  }

  private int randomOneTo(int max) {
    return (int) Math.floor(1 + Math.random() * max);
  }

  private String randomCustomerId() {
    return "customer-%d".formatted(randomOneTo(100));
  }

  private int randomItemCount() {
    return randomOneTo(5);
  }

  private List<OrderEntity.OrderItem> orderItems(int itemCount) {
    return randomProductIds(itemCount)
        .map(productId -> orderItem(productId))
        .map(orderItem -> orderItem.toCompletableFuture())
        .map(CompletableFuture::join)
        .toList();
  }

  private String randomProductId() {
    return "P%04d".formatted(randomOneTo(30));
  }

  private Stream<String> randomProductIds(int itemCount) {
    return IntStream.range(0, itemCount).mapToObj(i -> randomProductId()).distinct();
  }

  private int randomQuantity() {
    return randomOneTo(5);
  }

  private CompletionStage<OrderEntity.OrderItem> orderItem(String productId) {
    return product(productId)
        .thenApply(product -> {
          return new OrderEntity.OrderItem(
              product.skuId(),
              product.skuName(),
              product.skuDescription(),
              product.skuPrice(),
              randomQuantity(),
              null,
              null);
        });
  }

  private CompletionStage<State> product(String productId) {
    return componentClient.forEventSourcedEntity(productId)
        .call(ProductEntity::get)
        .execute();
  }
}
