package io.example.stock;

import java.time.Instant;
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

@ViewId("stockSkuItemsAvailable")
@Table("stock_sku_items_available")
@Subscribe.EventSourcedEntity(value = StockSkuItemEntity.class, ignoreUnknown = true)
public class stockSkuItemsAvailableView extends View<stockSkuItemsAvailableView.StockSkuItemRow> {
  private static final Logger log = LoggerFactory.getLogger(stockSkuItemsAvailableView.class);

  @GetMapping("/stock-sku-items-available/{skuId}")
  @Query("""
      SELECT * AS stockSkuItems
        FROM stock_sku_items_available
       LIMIT 100
       WHERE skuId = :skuId
         AND readyToShipAt = 0
      """)
  public StockSkuItems getStockSkuItemsAvailable(@PathVariable String skuId) {
    return null;
  }

  public UpdateEffect<StockSkuItemRow> on(StockSkuItemEntity.CreatedStockSkuItemEvent event) {
    log.info("State: {}\nEvent: {}", viewState(), event);
    return effects()
        .updateState(new StockSkuItemRow(event.stockSkuItemId(), event.skuId(), event.skuName(), Instant.EPOCH, viewState().stockOrderId()));
  }

  public UpdateEffect<StockSkuItemRow> on(StockSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    log.info("State: {}\nEvent: {}", viewState(), event);
    return effects()
        .updateState(new StockSkuItemRow(viewState().stockSkuItemId(), viewState().skuId(), viewState().skuName(), Instant.now(), viewState().stockOrderId()));
  }

  public UpdateEffect<StockSkuItemRow> on(StockSkuItemEntity.OrderRequestedJoinToStockRejectedEvent event) {
    log.info("State: {}\nEvent: {}", viewState(), event);
    return effects()
        .updateState(new StockSkuItemRow(viewState().stockSkuItemId(), viewState().skuId(), viewState().skuName(), Instant.EPOCH, viewState().stockOrderId()));
  }

  public UpdateEffect<StockSkuItemRow> on(StockSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    log.info("State: {}\nEvent: {}", viewState(), event);
    return effects()
        .updateState(new StockSkuItemRow(viewState().stockSkuItemId(), viewState().skuId(), viewState().skuName(), Instant.now(), viewState().stockOrderId()));
  }

  public UpdateEffect<StockSkuItemRow> on(StockSkuItemEntity.StockRequestedJoinToOrderRejectedEvent event) {
    log.info("State: {}\nEvent: {}", viewState(), event);
    return effects()
        .updateState(new StockSkuItemRow(viewState().stockSkuItemId(), viewState().skuId(), viewState().skuName(), Instant.EPOCH, viewState().stockOrderId()));
  }

  public record StockSkuItemRow(StockSkuItemId stockSkuItemId, String skuId, String skuName, Instant readyToShipAt, String stockOrderId) {}

  public record StockSkuItems(List<StockSkuItemRow> stockSkuItems) {}
}
