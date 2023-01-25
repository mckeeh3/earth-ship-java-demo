package io.example;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public record Validator2<T>(List<String> reasons, Function<String, T> error) {

  public static <T> Validator2<T> start() {
    return new Validator2<T>(List.of(), null);
  }

  public Validator2<T> isTrue(boolean test, String reason) {
    return test ? addError(reason) : this;
  }

  public Validator2<T> isNull(Object test, String reason) {
    return test == null ? addError(reason) : this;
  }

  public Validator2<T> isNotNull(Object test, String reason) {
    return test != null ? addError(reason) : this;
  }

  public Validator2<T> isEmpty(String test, String reason) {
    return test == null || test.isEmpty() ? addError(reason) : this;
  }

  public Validator2<T> isNotEmpty(String test, String reason) {
    return test != null && !test.isEmpty() ? addError(reason) : this;
  }

  public Validator2<T> isEmpty(List<?> test, String reason) {
    return test == null || test.isEmpty() ? addError(reason) : this;
  }

  public Validator2<T> isLtEqZero(int test, String reason) {
    return test <= 0 ? addError(reason) : this;
  }

  public Validator2<T> isGtLimit(int test, int limit, String reason) {
    return test > limit ? addError(reason) : this;
  }

  private Validator2<T> addError(String message) {
    var newReasons = new ArrayList<String>(reasons);
    newReasons.add(message);
    return new Validator2<T>(newReasons, error);
  }

  public ErrorOrSuccess<T> onError(Function<String, T> error) {
    var message = reasons.stream().reduce("", (a, b) -> "%s\n%s".formatted(a, b));
    return new ErrorOrSuccess<T>(message, error);
  }

  public record ErrorOrSuccess<T>(String errorMessage, Function<String, T> error) {
    public T onSuccess(Supplier<T> success) {
      if (errorMessage == null || errorMessage.isEmpty()) {
        return success.get();
      } else {
        return error.apply(errorMessage);
      }
    }
  }
}
