package io.example.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import kalix.javasdk.action.Action;
import kalix.javasdk.client.ComponentClient;

@RequestMapping("/product-ui")
public class ProductControllerAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ProductControllerAction.class);
  private final ComponentClient componentClient;

  public ProductControllerAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @PutMapping("/create-products")
  public Effect<String> createProducts(@RequestBody CreateProductsCommand command) {
    log.info("Command: {}", command);

    return onOneCommandInToManyCommandsOut(command);
  }

  private Effect<String> onOneCommandInToManyCommandsOut(CreateProductsCommand createProductCommand) {
    var results = createProductCommand.products().stream()
        .map(item -> toCommand(item))
        .map(command -> callFor(command))
        .toList();

    return effects().asyncReply(waitForCallsToFinish(results));
  }

  private ProductEntity.CreateProductCommand toCommand(Product item) {
    return new ProductEntity.CreateProductCommand(
        item.skuId(),
        item.skuName(),
        item.description(),
        item.msrp());
  }

  private CompletionStage<String> callFor(ProductEntity.CreateProductCommand command) {
    return componentClient.forEventSourcedEntity(command.skuId())
        .call(ProductEntity::create)
        .params(command)
        .execute();
  }

  private CompletableFuture<String> waitForCallsToFinish(List<CompletionStage<String>> results) {
    return CompletableFuture.allOf(results.toArray(new CompletableFuture[results.size()]))
        .thenApply(__ -> "OK");
  }

  public record Product(String skuId, String skuName, String description, BigDecimal msrp) {}

  public record CreateProductsCommand(List<Product> products) {}
}
