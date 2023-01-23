package io.example.stock;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;

import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.springsdk.annotations.EntityKey;
import kalix.springsdk.annotations.EntityType;

@EntityKey("stockSkuItemId")
@EntityType("stockSkuItem")
@RequestMapping("/stock-sku-item/{stockSkuItemId}")
public class StockSkuItemEntity extends EventSourcedEntity<StockSkuItemEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(StockSkuItemEntity.class);

  public record State(
      String stockSkuItemId,
      String skuId,
      String skuName,
      String orderId,
      String orderSkuItemId,
      String stockOrderId,
      Instant readyToShipAt) {

    State empState() {
      return new State(null, null, null, null, null, null, null);
    }

    boolean isEmpty() {
      return stockSkuItemId == null || stockSkuItemId.isBlank();
    }

    List<?> eventsFor(CreateStockSkuItemCommand command) {
      var events = new ArrayList<>();
      events.add(new CreatedStockSkuItemEvent(command.stockSkuItemId, command.skuId, command.skuName, command.stockOrderId));
      events.add(new StockRequestedJoinToOrderEvent(command.stockSkuItemId, command.skuId, command.stockOrderId));

      return events;
    }

    Object eventFor(OrderRequestsJoinToStockCommand command) {
      if (orderSkuItemId == null || orderSkuItemId.equals(command.orderSkuItemId)) {
        return new OrderRequestedJoinToStockAcceptedEvent(command.stockSkuItemId, command.skuId, command.orderId, command.orderSkuItemId, command.stockOrderId);
      } else {
        return new OrderRequestedJoinToStockRejectedEvent(command.stockSkuItemId, command.skuId, command.orderId, command.orderSkuItemId, command.stockOrderId);
      }
    }

    OrderRequestedJoinToStockRejectedEvent eventFor(OrderRequestsJoinToStockRejectedCommand command) {
      return new OrderRequestedJoinToStockRejectedEvent(command.stockSkuItemId, command.skuId, command.orderId, command.orderSkuItemId, command.stockOrderId);
    }

    List<?> eventsFor(StockRequestsJoinToOrderAcceptedCommand command) {
      if (orderSkuItemId == null) {
        return List.of((new StockRequestedJoinToOrderAcceptedEvent(command.stockSkuItemId, command.skuId, command.orderId, command.orderSkuItemId, command.stockOrderId, command.readyToShipAt)));
      } else {
        var events = new ArrayList<>();
        events.add(new StockRequestedJoinToOrderEvent(command.stockSkuItemId, command.skuId, command.stockOrderId));
        events.add(new StockRequestedJoinToOrderRejectedEvent(command.stockSkuItemId, command.skuId, command.orderId, command.orderSkuItemId, command.stockOrderId));

        return events;
      }
    }

    StockRequestedJoinToOrderEvent eventFor(StockRequestsJoinToOrderRejectedCommand command) {
      return new StockRequestedJoinToOrderEvent(command.stockSkuItemId, command.skuId, command.stockOrderId);
    }

    State on(CreatedStockSkuItemEvent event) {
      return new State(event.stockSkuItemId, event.skuId, event.skuName, null, null, event.stockOrderId, null);
    }

    State on(OrderRequestedJoinToStockAcceptedEvent event) {
      return new State(stockSkuItemId, skuId, skuName, event.orderId, event.orderSkuItemId, stockOrderId, null);
    }

    State on(OrderRequestedJoinToStockRejectedEvent event) {
      return new State(stockSkuItemId, skuId, skuName, null, null, stockOrderId, null);
    }

    State on(StockRequestedJoinToOrderAcceptedEvent event) {
      return new State(stockSkuItemId, skuId, skuName, event.orderId, event.orderSkuItemId, stockOrderId, event.readyToShipAt);
    }

    State on(StockRequestedJoinToOrderRejectedEvent event) {
      return new State(stockSkuItemId, skuId, skuName, null, null, stockOrderId, null);
    }
  }

  public record CreateStockSkuItemCommand(String stockSkuItemId, String skuId, String skuName, String stockOrderId) {}

  public record CreatedStockSkuItemEvent(String stockSkuItemId, String skuId, String skuName, String stockOrderId) {}

  public record StockRequestedJoinToOrderEvent(String stockSkuItemId, String skuId, String stockOrderId) {}

  public record OrderRequestsJoinToStockCommand(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId) {}

  public record OrderRequestedJoinToStockAcceptedEvent(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId) {}

  public record OrderRequestsJoinToStockRejectedCommand(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId) {}

  public record OrderRequestedJoinToStockRejectedEvent(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId) {}

  public record StockRequestsJoinToOrderAcceptedCommand(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId, Instant readyToShipAt) {}

  public record StockRequestedJoinToOrderAcceptedEvent(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId, Instant readyToShipAt) {}

  public record StockRequestsJoinToOrderRejectedCommand(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId) {}

  public record StockRequestedJoinToOrderRejectedEvent(String stockSkuItemId, String skuId, String orderId, String orderSkuItemId, String stockOrderId) {}
}
