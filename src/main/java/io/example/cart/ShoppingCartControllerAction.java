package io.example.cart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import io.example.product.ProductEntity;
import io.example.product.ProductEntity.State;
import kalix.javasdk.action.Action;
import kalix.javasdk.client.ComponentClient;

@RequestMapping("/cart-ui/{customerId}")
public class ShoppingCartControllerAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ShoppingCartControllerAction.class);
  private final ComponentClient componentClient;

  public ShoppingCartControllerAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @PutMapping("/items/add")
  public Effect<String> addLineItem(@RequestBody AddLineItemCommand command) {
    log.info("Command: {}", command);

    return validateProduct(command);
  }

  @PutMapping("/items/{sku_id}/change")
  public Effect<String> changeLineItem(@RequestBody ChangeLineItemCommand command) {
    log.info("Command: {}", command);

    return callFor(command);
  }

  @PutMapping("/items/{sku_id}/remove")
  public Effect<String> removeLineItem(@RequestBody RemoveLineItemCommand command) {
    log.info("Command: {}", command);

    return callFor(command);
  }

  @PutMapping("/checkout")
  public Effect<String> checkout(@RequestBody CheckoutCommand command) {
    log.info("Command: {}", command);

    return callFor(command);
  }

  @GetMapping()
  public Effect<ShoppingCartEntity.State> get(@PathVariable("customerId") String customerId) {
    log.info("EntityId: {}\n_Command: {}", customerId, "GetShoppingCart");

    return callFor(customerId);
  }

  private Effect<String> validateProduct(AddLineItemCommand command) {
    return effects().asyncEffect(
        componentClient.forEventSourcedEntity(command.skuId())
            .call(ProductEntity::get)
            .execute()
            .thenApply(result -> addLineItem(command, result))
            .exceptionally(e -> effects().error(e.getMessage())));
  }

  private Effect<String> addLineItem(AddLineItemCommand commandIn, ProductEntity.State product) {
    return effects().forward(componentClient.forEventSourcedEntity(commandIn.customerId())
        .call(ShoppingCartEntity::addLineItem)
        .params(toCommand(commandIn, product)));
  }

  private ShoppingCartEntity.AddLineItemCommand toCommand(AddLineItemCommand commandIn, State product) {
    return new ShoppingCartEntity.AddLineItemCommand(
        commandIn.customerId(),
        commandIn.skuId(),
        product.skuName(),
        product.skuDescription(),
        product.skuPrice(),
        commandIn.quantity());
  }

  private Effect<String> callFor(ChangeLineItemCommand commandIn) {
    var commandOut = new ShoppingCartEntity.ChangeLineItemCommand(commandIn.customerId(), commandIn.skuId(), commandIn.quantity());
    return effects().forward(
        componentClient.forEventSourcedEntity(commandIn.customerId())
            .call(ShoppingCartEntity::changeLineItem)
            .params(commandOut));
  }

  private Effect<String> callFor(RemoveLineItemCommand commandIn) {
    var commandOut = new ShoppingCartEntity.RemoveLineItemCommand(commandIn.customerId(), commandIn.skuId());
    return effects().forward(
        componentClient.forEventSourcedEntity(commandIn.customerId())
            .call(ShoppingCartEntity::removeLineItem)
            .params(commandOut));
  }

  private Effect<String> callFor(CheckoutCommand commandIn) {
    var commandOut = new ShoppingCartEntity.CheckoutCommand(commandIn.customerId());
    return effects().forward(
        componentClient.forEventSourcedEntity(commandIn.customerId())
            .call(ShoppingCartEntity::checkout)
            .params(commandOut));
  }

  private Effect<io.example.cart.ShoppingCartEntity.State> callFor(String customerId) {
    return effects().forward(
        componentClient.forEventSourcedEntity(customerId)
            .call(ShoppingCartEntity::get));
  }

  public record AddLineItemCommand(String customerId, String skuId, int quantity) {}

  public record ChangeLineItemCommand(String customerId, String skuId, int quantity) {}

  public record RemoveLineItemCommand(String customerId, String skuId) {}

  public record CheckoutCommand(String customerId) {}
}
