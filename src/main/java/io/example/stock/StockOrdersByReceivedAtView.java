package io.example.stock;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;
import kalix.javasdk.view.View;

@ViewId("stock-orders_by-received-at")
@Table("stock_orders_by_received_at")
@Subscribe.EventSourcedEntity(value = StockOrderEntity.class, ignoreUnknown = true)
public class StockOrdersByReceivedAtView extends View<StockOrderEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(StockOrdersByReceivedAtView.class);

  @PostMapping("/stock-orders-by-received-at")
  @Query("""
        SELECT * AS stockOrders
          FROM stock_orders_by_received_at
         WHERE stockOrderReceivedAt >= :stockOrderReceivedAtFrom
           AND stockOrderReceivedAt < :stockOrderReceivedAtTo
      ORDER BY stockOrderReceivedAt
         LIMIT 100
        """)
  public StockOrders getStockOrders(@RequestBody QueryRequest queryRequest) {
    return null;
  }

  @Override
  public StockOrderEntity.State emptyState() {
    return StockOrderEntity.State.emptyState();
  }

  public UpdateEffect<StockOrderEntity.State> on(StockOrderEntity.CreatedStockOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<StockOrderEntity.State> on(StockOrderEntity.UpdatedStockOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<StockOrderEntity.State> on(StockOrderEntity.GeneratedStockSkuItemIdsEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public record QueryRequest(Instant stockOrderReceivedAtFrom, Instant stockOrderReceivedAtTo) {}

  public record StockOrders(List<StockOrderEntity.State> stockOrders) {}
}
