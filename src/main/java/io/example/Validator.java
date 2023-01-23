package io.example;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import kalix.javasdk.eventsourcedentity.EventSourcedEntity.Effect;

public record Validator<T>(List<String> reasons) {
  public static <T> Validator<T> start() {
    return new Validator<T>(List.of());
  }

  public Validator<T> isTrue(boolean test, String reason) {
    return test ? addError(reason) : this;
  }

  public Validator<T> isNull(Object test, String reason) {
    return test == null ? addError(reason) : this;
  }

  public Validator<T> isNotNull(Object test, String reason) {
    return test != null ? addError(reason) : this;
  }

  public Validator<T> isEmpty(String test, String reason) {
    return test == null || test.isEmpty() ? addError(reason) : this;
  }

  public Validator<T> notEmpty(String test, String reason) {
    return test != null && !test.isEmpty() ? addError(reason) : this;
  }

  public Validator<T> isEmpty(List<?> test, String reason) {
    return test == null || test.isEmpty() ? addError(reason) : this;
  }

  public Validator<T> ltEqZero(int test, String reason) {
    return test <= 0 ? addError(reason) : this;
  }

  private Validator<T> addError(String message) {
    var newReasons = new ArrayList<String>(reasons);
    newReasons.add(message);
    return new Validator<T>(newReasons);
  }

  public Effect<T> ifErrorOrElse(Function<String, Effect<T>> error, Supplier<Effect<T>> process) {
    if (reasons.size() > 0) {
      var message = reasons.stream().reduce("", (a, b) -> "%s\n%s".formatted(a, b));
      return error.apply(message);
    } else {
      return process.get();
    }
  }
}
