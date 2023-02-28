package io.example.stock;

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
public class StockSkuItemsAvailableView extends View<StockSkuItemsAvailableView.StockSkuItemRow> {
  private static final Logger log = LoggerFactory.getLogger(StockSkuItemsAvailableView.class);

  @GetMapping("/stock-sku-items-available/{skuId}")
  @Query("""
      SELECT * AS stockSkuItemRows
        FROM stock_sku_items_available
       LIMIT 1000
       WHERE skuId = :skuId
         AND available = true
      """)
  public StockSkuItemRows getStockSkuItemsAvailable(@PathVariable String skuId) {
    return null;
  }

  public UpdateEffect<StockSkuItemRow> on(StockSkuItemEntity.CreatedStockSkuItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new StockSkuItemRow(event.stockSkuItemId(), event.skuId(), event.skuName(), false));
  }

  public UpdateEffect<StockSkuItemRow> on(StockSkuItemEntity.StockSkuItemActivatedEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new StockSkuItemRow(event.stockSkuItemId(), event.skuId(), viewState().skuName(), true));
  }

  public UpdateEffect<StockSkuItemRow> on(StockSkuItemEntity.OrderRequestedJoinToStockAcceptedEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new StockSkuItemRow(viewState().stockSkuItemId(), viewState().skuId(), viewState().skuName(), false));
  }

  public UpdateEffect<StockSkuItemRow> on(StockSkuItemEntity.OrderRequestedJoinToStockRejectedEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new StockSkuItemRow(viewState().stockSkuItemId(), viewState().skuId(), viewState().skuName(), true));
  }

  public UpdateEffect<StockSkuItemRow> on(StockSkuItemEntity.StockRequestedJoinToOrderAcceptedEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new StockSkuItemRow(viewState().stockSkuItemId(), viewState().skuId(), viewState().skuName(), false));
  }

  public UpdateEffect<StockSkuItemRow> on(StockSkuItemEntity.StockRequestedJoinToOrderReleasedEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(new StockSkuItemRow(viewState().stockSkuItemId(), viewState().skuId(), viewState().skuName(), true));
  }

  public record StockSkuItemRow(StockSkuItemId stockSkuItemId, String skuId, String skuName, boolean available) {}

  public record StockSkuItemRows(List<StockSkuItemRow> stockSkuItemRows) {}
}
