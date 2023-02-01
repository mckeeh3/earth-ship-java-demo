package io.example;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public record Validator<T>(List<String> reasons, Function<String, T> error) {

  public static <T> Validator<T> start() {
    return new Validator<T>(List.of(), null);
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

  public Validator<T> isNotEmpty(String test, String reason) {
    return test != null && !test.isEmpty() ? addError(reason) : this;
  }

  public Validator<T> isEmpty(List<?> test, String reason) {
    return test == null || test.isEmpty() ? addError(reason) : this;
  }

  public Validator<T> isLtEqZero(int test, String reason) {
    return test <= 0 ? addError(reason) : this;
  }

  public Validator<T> isGtLimit(int test, int limit, String reason) {
    return test > limit ? addError(reason) : this;
  }

  private Validator<T> addError(String message) {
    var newReasons = Stream.concat(reasons.stream(), Stream.of(message)).toList();
    return new Validator<T>(newReasons, error);
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
