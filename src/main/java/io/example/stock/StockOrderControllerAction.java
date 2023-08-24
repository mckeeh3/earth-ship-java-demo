package io.example.stock;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.product.ProductEntity;
import io.example.product.ProductEntity.State;
import kalix.javasdk.action.Action;
import kalix.javasdk.client.ComponentClient;

@RequestMapping("/stock-order-ui/{stockOrderId}")
public class StockOrderControllerAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockOrderControllerAction.class);
  private final ComponentClient componentClient;

  public StockOrderControllerAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @PutMapping("/create")
  public Effect<String> createStockOrder(@RequestBody CreateStockOrderCommand command) {
    log.info("Event: {}", command);

    return validateProduct(command);
  }

  private Effect<String> validateProduct(CreateStockOrderCommand command) {
    return effects().asyncEffect(
        componentClient.forEventSourcedEntity(command.skuId())
            .call(ProductEntity::get)
            .execute()
            .thenApply(response -> createStockOrder(command, response)));
  }

  private Effect<String> createStockOrder(CreateStockOrderCommand command, State result) {
    var stockOrderId = "stock-order-%s-%d".formatted(command.skuId(), Instant.now().toEpochMilli());
    var commandOut = new StockOrderEntity.CreateStockOrderCommand(stockOrderId, command.skuId(), result.skuName(), command.quantityTotal());
    return effects().forward(
        componentClient.forEventSourcedEntity(stockOrderId)
            .call(StockOrderEntity::create)
            .params(commandOut));
  }

  public record CreateStockOrderCommand(String skuId, int quantityTotal) {}
}
