package io.example.cart;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;

import kalix.javasdk.view.View;
import kalix.javasdk.annotations.Query;
import kalix.javasdk.annotations.Subscribe;
import kalix.javasdk.annotations.Table;
import kalix.javasdk.annotations.ViewId;

@ViewId("shopping-carts")
@Table("shopping_carts")
@Subscribe.EventSourcedEntity(value = ShoppingCartEntity.class, ignoreUnknown = true)
public class ShoppingCartsView extends View<ShoppingCartEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(ShoppingCartsView.class);

  @GetMapping("/shopping-carts")
  @Query("""
        SELECT * AS shoppingCarts
          FROM shopping_carts
      ORDER BY createdAt DESC
         LIMIT 100
        """)
  public ShoppingCarts getShoppingCarts() {
    return null;
  }

  @Override
  public ShoppingCartEntity.State emptyState() {
    return ShoppingCartEntity.State.emptyState();
  }

  public UpdateEffect<ShoppingCartEntity.State> on(ShoppingCartEntity.AddedLineItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ShoppingCartEntity.State> on(ShoppingCartEntity.ChangedLineItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ShoppingCartEntity.State> on(ShoppingCartEntity.RemovedLineItemEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ShoppingCartEntity.State> on(ShoppingCartEntity.CheckedOutEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public record ShoppingCarts(List<ShoppingCartEntity.State> shoppingCarts) {}
}
