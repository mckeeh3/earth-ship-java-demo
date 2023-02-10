package io.example.product;

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

@ViewId("products")
@Table("products")
@Subscribe.EventSourcedEntity(value = ProductEntity.class, ignoreUnknown = true)
public class ProductsView extends View<ProductEntity.State> {
  private static final Logger log = LoggerFactory.getLogger(ProductsView.class);

  @GetMapping("/products/{nextPageToken}")
  @Query("""
      SELECT * AS products, next_page_token() AS nextPageToken, has_more() AS hasMore
        FROM products
      OFFSET page_token_offset(:nextPageToken)
       LIMIT 100
      """)
  public Products getProducts(@PathVariable String nextPageToken) {
    return null;
  }

  @Override
  public ProductEntity.State emptyState() {
    return ProductEntity.State.emptyState();
  }

  public UpdateEffect<ProductEntity.State> on(ProductEntity.CreatedProductEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ProductEntity.State> on(ProductEntity.AddedStockOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ProductEntity.State> on(ProductEntity.UpdatedStockOrderEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public UpdateEffect<ProductEntity.State> on(ProductEntity.UpdatedProductsBackOrderedEvent event) {
    log.info("State: {}\n_Event: {}", viewState(), event);
    return effects()
        .updateState(viewState().on(event));
  }

  public record Products(List<ProductEntity.State> products, String nextPageToken, boolean hasMore) {}
}
