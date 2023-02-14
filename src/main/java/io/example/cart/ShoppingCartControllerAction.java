package io.example.cart;

import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.google.protobuf.any.Any;

import io.example.product.ProductEntity;
import io.example.product.ProductEntity.State;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.action.Action;
import kalix.springsdk.KalixClient;

@RequestMapping("/cart-ui/{customerId}")
public class ShoppingCartControllerAction extends Action {
  private static final Logger log = LoggerFactory.getLogger(ShoppingCartControllerAction.class);
  private final KalixClient kalixClient;

  public ShoppingCartControllerAction(KalixClient kalixClient) {
    this.kalixClient = kalixClient;
  }

  @PutMapping("/items/add")
  public Effect<String> addLineItem(@RequestBody AddLineItemCommand command) {
    log.info("Command: {}", command);

    return effects().asyncEffect(validateProduct(command));
  }

  @PutMapping("/items/{sku_id}/change")
  public Effect<String> changeLineItem(@RequestBody ChangeLineItemCommand command) {
    log.info("Command: {}", command);

    return effects().forward(callFor(command));
  }

  @PutMapping("/items/{sku_id}/remove")
  public Effect<String> removeLineItem(@RequestBody RemoveLineItemCommand command) {
    log.info("Command: {}", command);

    return effects().forward(callFor(command));
  }

  @PutMapping("/checkout")
  public Effect<String> checkout(@RequestBody CheckoutCommand command) {
    log.info("Command: {}", command);

    return effects().forward(callFor(command));
  }

  @GetMapping()
  public Effect<ShoppingCartEntity.State> get(@PathVariable("customerId") String customerId) {
    log.info("EntityId: {}\n_Command: {}", customerId, "GetShoppingCart");

    return effects().forward(callFor(customerId));
  }

  private CompletionStage<Effect<String>> validateProduct(AddLineItemCommand command) {
    var path = "/product/%s".formatted(command.skuId());
    var returnType = ProductEntity.State.class;
    var deferredCall = kalixClient.get(path, returnType);

    return handleProductResponse(command, deferredCall.execute());
  }

  private CompletionStage<Effect<String>> handleProductResponse(AddLineItemCommand command, CompletionStage<ProductEntity.State> response) {
    return response
        .thenApply(result -> addLineItem(command, result))
        .exceptionally(e -> effects().error(e.getMessage()));
  }

  private Effect<String> addLineItem(AddLineItemCommand commandIn, ProductEntity.State product) {
    var path = "/cart/%s/items/add".formatted(commandIn.customerId());
    var commandOut = toCommand(commandIn, product);
    var returnType = String.class;
    var deferredCall = kalixClient.put(path, commandOut, returnType);

    return effects().forward(deferredCall);
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

  private DeferredCall<Any, String> callFor(ChangeLineItemCommand commandIn) {
    var path = "/cart/%s/items/%s/change".formatted(commandIn.customerId(), commandIn.skuId());
    var commandOut = new ShoppingCartEntity.ChangeLineItemCommand(commandIn.customerId(), commandIn.skuId(), commandIn.quantity());
    var returnType = String.class;

    return kalixClient.put(path, commandOut, returnType);
  }

  private DeferredCall<Any, String> callFor(RemoveLineItemCommand commandIn) {
    var path = "/cart/%s/items/%s/remove".formatted(commandIn.customerId(), commandIn.skuId());
    var commandOut = new ShoppingCartEntity.RemoveLineItemCommand(commandIn.customerId(), commandIn.skuId());
    var returnType = String.class;

    return kalixClient.put(path, commandOut, returnType);
  }

  private DeferredCall<Any, String> callFor(CheckoutCommand commandIn) {
    var path = "/cart/%s/checkout".formatted(commandIn.customerId());
    var commandOut = new ShoppingCartEntity.CheckoutCommand(commandIn.customerId());
    var returnType = String.class;

    return kalixClient.put(path, commandOut, returnType);
  }

  private DeferredCall<Any, ShoppingCartEntity.State> callFor(String customerId) {
    var path = "/cart/%s".formatted(customerId);
    var returnType = ShoppingCartEntity.State.class;

    return kalixClient.get(path, returnType);
  }

  public record AddLineItemCommand(String customerId, String skuId, int quantity) {}

  public record ChangeLineItemCommand(String customerId, String skuId, int quantity) {}

  public record RemoveLineItemCommand(String customerId, String skuId) {}

  public record CheckoutCommand(String customerId) {}
}
