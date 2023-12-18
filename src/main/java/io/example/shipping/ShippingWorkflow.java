package io.example.shipping;

import io.example.stock.StockSkuItemEntity;
import io.example.stock.StockSkuItemsAvailableView;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Id("shippingId")
@TypeId("shipping-workflow")
@RequestMapping("/shipping-workflow/{shippingId}")
public class ShippingWorkflow extends Workflow<ShippingWorkflow.State> {

  private Logger logger = LoggerFactory.getLogger(getClass());

  final private ComponentClient componentClient;

  public ShippingWorkflow(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public record CreateWorkflow(String orderId,
                               List<OrderItem> orderItems) {
  }


  public record OrderItem(
    String skuId,
    String skuName,
    int quantity
  ) {
  }

  enum Status {
    OPEN,
    TO_BACK_ORDER,
    BACK_ORDERED,
    READY_TO_SHIP
  }

  public record OrderSkuItem(
    String id,
    String skuId,
    String skuName,
    Status status,
    Optional<String> stockSkuItemId
  ) {
    boolean isOpen() {
      return status == Status.OPEN;
    }

    OrderSkuItem toBackOrder() {
      return new OrderSkuItem(id, skuId, skuName, Status.TO_BACK_ORDER, Optional.empty());
    }

    OrderSkuItem readyToShip(String stockSkuItemId) {
      return new OrderSkuItem(id, skuId, skuName, Status.READY_TO_SHIP, Optional.of(stockSkuItemId));
    }

    public boolean isReadyToShip() {
      return status == Status.READY_TO_SHIP;
    }
  }

  public record State(String orderId, Map<String, List<OrderSkuItem>> items) {

    Optional<List<OrderSkuItem>> nextBatch() {
      return items.values().stream()
        .filter(items -> items.stream().anyMatch(OrderSkuItem::isOpen))
        .findFirst();
    }

    State markToBackOrder(String skuId) {
      var skuItems = items.get(skuId);
      var modified =
        skuItems
          .stream()
          .map(i -> i.isOpen() ? i.toBackOrder() : i)
          .toList();
      items.put(skuId, modified);
      return new State(orderId, items);
    }

    // replace a batch with updated order OrderSkuItem
    public State updateBatch(List<OrderSkuItem> orderSkuItems) {
      var skuId = orderSkuItems.get(0).skuId;
      items.put(skuId, orderSkuItems);
      return new State(orderId, items);
    }
  }

  // step labels
  final private String reserveStepLabel = "reserve";
  final private String searchItemsStepLabel = "search-orderSkuItems";
  final private String backOrderStepLabel = "back-order";


  @PostMapping
  public Effect<String> initiateWorkflow(@RequestBody CreateWorkflow cmd) {
  logger.info("Creating Order Workflow " + cmd);
    var items =
      cmd.orderItems
        .stream()
        .flatMap(item ->
          IntStream.range(0, item.quantity())
            .mapToObj(i ->
              new OrderSkuItem(
                UUID.randomUUID().toString(),
                item.skuId(), item.skuName(), Status.OPEN, Optional.empty())
            )
        ).collect(Collectors.groupingBy(i -> i.skuId));

    return effects()
      .updateState(new State(cmd.orderId, items))
      .transitionTo(searchItemsStepLabel)
      .thenReply("OK");

  }

  record Searching(List<OrderSkuItem> orderSkuItems,
                   List<StockSkuItemsAvailableView.StockSkuItemRow> stockSkuItemRows) {

    static Searching asEmpty() {
      return new Searching(List.of(), List.of());
    }

    boolean noOpenItems() {
      return orderSkuItems.isEmpty();
    }

    String getSkuId() {
      return orderSkuItems.get(0).skuId;
    }

    boolean noStockAvailable() {
      return stockSkuItemRows.isEmpty();
    }
  }

  record ReservationBatch(List<OrderSkuItem> orderSkuItems,
                          List<StockSkuItemsAvailableView.StockSkuItemRow> stockSkuItemRows) {

    String getSkuId() {
      return orderSkuItems.get(0).skuId;
    }

    OrderSkuItem orderSkuItemToMatch() {
      return orderSkuItems
        .stream()
        .filter(OrderSkuItem::isOpen)
        .toList().get(0);
    }

    boolean allReadyToShip() {
      return orderSkuItems.stream().allMatch(OrderSkuItem::isReadyToShip);
    }

    boolean isStockEmpty() {
      return stockSkuItemRows.isEmpty();
    }

    /**
     * Pick the first item from the list to try to reserve it
     */
    StockSkuItemsAvailableView.StockSkuItemRow firstStockSkuItem() {
      return stockSkuItemRows.get(0);
    }

    /**
     * We call this method when we succeed to make a reservation.
     * This will remove the reserved item from the list of StockSkuItemRow
     * and set one of the order items to reserve.
     */
    ReservationBatch stockSkuItemReserved(String stockSkuItemId) {
      var firstReadyToShip = orderSkuItemToMatch();
      // if empty, we are ready with this batch
      var readyToShipItem = firstReadyToShip.readyToShip(stockSkuItemId);
      // replace old item with the new ReadyToShip one
      var newItemList =
        orderSkuItems
          .stream()
          .map(i -> i.id.equals(readyToShipItem.id) ? readyToShipItem : i)
          .toList();
      // continue reservation process
      return new ReservationBatch(newItemList, stockSkuItemRows.stream().skip(1).toList());

    }


    /**
     * Head is not available, so we drop it and continue searching
     */
    ReservationBatch stockSkuItemNotAvailable() {
      return new ReservationBatch(orderSkuItems, stockSkuItemRows.stream().skip(1).toList());
    }

  }


  @Override
  public WorkflowDef<State> definition() {
    Step searchItems =
      step(searchItemsStepLabel)
        .asyncCall(() -> {
          logger.info("Creating Order Workflow " + cmd);
          var batch = currentState().nextBatch();
          if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(Searching.asEmpty());
          } else {
            var openItems = batch.get();
            var skuId = openItems.get(0).skuId;
            return componentClient.forView()
              .call(StockSkuItemsAvailableView::getStockSkuItemsAvailable)
              .params(skuId)
              .execute()
              .thenApply(res -> new Searching(openItems, res.stockSkuItemRows()));
          }
        })
        .andThen(Searching.class, results -> {
            if (results.noOpenItems()) {
              // if there is no open orderSkuItems, we can move to back-ordering step
              return effects().transitionTo(backOrderStepLabel);
            } else if (results.noStockAvailable()) {
              // when here is no stock available, we mark the orderSkuItems in this batch for back-ordering
              // we search for the next batch of open orderSkuItems
              return effects()
                .updateState(currentState().markToBackOrder(results.getSkuId()))
                .transitionTo(searchItemsStepLabel);
            } else {
              // we have some orderSkuItems in stock, so we will move on and try to reserve them
              Collections.shuffle(results.stockSkuItemRows); // shuffling the list diminish the chance of concurrent item reservation
              return effects()
                .transitionTo(reserveStepLabel, new ReservationBatch(results.orderSkuItems, results.stockSkuItemRows));
            }
          }
        );

    Step reserveStep =
      step(reserveStepLabel)
        .asyncCall(ReservationBatch.class, toReserve -> {
          // list is guaranteed to be non-empty, so we always pick the first one
          var stockSkuItem = toReserve.firstStockSkuItem();
          var stockSkuItemId = stockSkuItem.stockSkuItemId().toEntityId();

          var cmd = new StockSkuItemEntity.ReserveStockSkuItemCommand(
            stockSkuItem.stockSkuItemId(),
            toReserve.getSkuId(),
            OrderSkuItemEntity.OrderSkuItemId.of(currentState().orderId(), toReserve.orderSkuItemToMatch().id())
          );

          return componentClient
            .forEventSourcedEntity(stockSkuItemId)
            .call(StockSkuItemEntity::reserve).params(cmd)
            .execute().thenApply(res -> {
              if (res.isSuccess()) {
                return toReserve.stockSkuItemReserved(stockSkuItemId);
              } else {
                return toReserve.stockSkuItemNotAvailable();
              }
            });
        })
        .andThen(ReservationBatch.class, toReserve -> {
          if (toReserve.allReadyToShip() || toReserve.isStockEmpty()) {
            // if all items in this batch are ready, or we run out of stock
            // we update the state with whatever we have and go back to searching mode
            return effects()
              .updateState(currentState().updateBatch(toReserve.orderSkuItems))
              .transitionTo(searchItemsStepLabel);
          } else {
            return effects().transitionTo(reserveStepLabel, toReserve);
          }
        });

    Step backOrderStep =
      step(backOrderStepLabel)
        .asyncCall(() -> CompletableFuture.completedFuture("ok"))
        .andThen(String.class, __ -> effects().pause());

    return workflow()
      .addStep(searchItems)
      .addStep(reserveStep)
      .addStep(backOrderStep);
  }

}
