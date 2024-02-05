package io.example.stock;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import io.example.stock.StockOrderRedLeafEntity.StockOrderRedLeafId;
import kalix.javasdk.view.View;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;

@ViewId("stockOrderRedLeafAvailable")
@Table("stock_order_red_leaf_available")
@Subscribe.EventSourcedEntity(value = StockOrderRedLeafEntity.class, ignoreUnknown = true)
public class StockOrderRedLeafAvailableView extends View<StockOrderRedLeafAvailableView.StockOrderRedLeafRow> {
  private static final Logger log = LoggerFactory.getLogger(StockOrderRedLeafAvailableView.class);

  @GetMapping("/stock-order-red-leaf-available/{skuId}")
  @Query("""
      SELECT * AS stockOrderRedLeafRows
        FROM stock_order_red_leaf_available
       LIMIT 100
       WHERE skuId = :skuId
         AND available = true
      """)
  public StockOrderRedLeafRows getStockOrderRedLeafAvailable(@PathVariable String skuId) {
    return null;
  }

  public UpdateEffect<StockOrderRedLeafRow> on(StockOrderRedLeafEntity.StockOrderCreatedEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new StockOrderRedLeafRow(event.stockOrderRedLeafId(), event.stockOrderRedLeafId().skuId(), false));
  }

  public UpdateEffect<StockOrderRedLeafRow> on(StockOrderRedLeafEntity.OrderItemConsumedStockSkuItemsEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new StockOrderRedLeafRow(event.stockOrderRedLeafId(), event.stockOrderRedLeafId().skuId(), event.availableToBeConsumed()));
  }

  public UpdateEffect<StockOrderRedLeafRow> on(StockOrderRedLeafEntity.OrderItemReleasedStockSkuItemsEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new StockOrderRedLeafRow(event.stockOrderRedLeafId(), event.stockOrderRedLeafId().skuId(), true));
  }

  public UpdateEffect<StockOrderRedLeafRow> on(StockOrderRedLeafEntity.StockOrderConsumedOrderSkuItemsEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new StockOrderRedLeafRow(event.stockOrderRedLeafId(), event.stockOrderRedLeafId().skuId(), event.availableToBeConsumed()));
  }

  public UpdateEffect<StockOrderRedLeafRow> on(StockOrderRedLeafEntity.StockOrderReleasedOrderSkuItemsEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new StockOrderRedLeafRow(event.stockOrderRedLeafId(), event.stockOrderRedLeafId().skuId(), true));
  }

  public UpdateEffect<StockOrderRedLeafRow> on(StockOrderRedLeafEntity.StockOrderSetAvailableToBeConsumedEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new StockOrderRedLeafRow(event.stockOrderRedLeafId(), event.stockOrderRedLeafId().skuId(), event.availableToBeConsumed()));
  }

  public record StockOrderRedLeafRow(StockOrderRedLeafId stockOrderRedLeafId, String skuId, boolean available) {}

  public record StockOrderRedLeafRows(List<StockOrderRedLeafRow> stockOrderRedLeafRows) {}
}