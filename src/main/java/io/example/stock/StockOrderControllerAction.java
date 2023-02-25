package io.example.stock;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.product.ProductEntity;
import io.example.product.ProductEntity.State;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;

@RequestMapping("/stock-order-ui/{stockOrderId}")
public class StockOrderControllerAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(StockOrderControllerAction.class);
  private final KalixClient kalixClient;

  public StockOrderControllerAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  @PutMapping("/create")
  public Effect<String> createStockOrder(@RequestBody CreateStockOrderCommand command) {
    log.info("Event: {}", command);

    return effects().asyncEffect(validateProduct(command));
  }

  private CompletionStage<Effect<String>> validateProduct(CreateStockOrderCommand command) {
    var path = "/product/%s".formatted(command.skuId());
    var returnType = ProductEntity.State.class;
    var deferredCall = kalixClient.get(path, returnType);

    return handleProductResponse(command, deferredCall.execute());
  }

  private CompletionStage<Effect<String>> handleProductResponse(CreateStockOrderCommand command, CompletionStage<State> response) {
    return response
        .thenApply(result -> createStockOrder(command, result))
        .exceptionally(e -> effects().error(e.getMessage()));
  }

  private Effect<String> createStockOrder(CreateStockOrderCommand command, State result) {
    var stockOrderId = "stock-order-%s-%d".formatted(command.skuId(), Instant.now().toEpochMilli());

    var path = "/stock-order/%s/create".formatted(stockOrderId);
    var commandOut = new StockOrderEntity.CreateStockOrderCommand(stockOrderId, command.skuId(), result.skuName(), command.quantityTotal());
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, commandOut, returnType);

    return effects().forward(deferredCall);
  }

  public record CreateStockOrderCommand(String skuId, int quantityTotal) {}
}
