package io.example;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Validator {

  private static ValidatorBuilder newBuilder(String reason) {
    return new ValidatorBuilder(List.of(reason));
  }

  private static ValidatorBuilder empty() {
    return new ValidatorBuilder(List.of());
  }

  public static ValidatorBuilder isTrue(boolean test, String reason) {
    return test ? newBuilder(reason) : empty();
  }

  public static ValidatorBuilder isFalse(boolean test, String reason) {
    return !test ? newBuilder(reason) : empty();
  }

  public static ValidatorBuilder isFutureDate(Long dateTestMillis, Long thresholdMillis, Long baselineMillis, String reason) {
    return dateTestMillis + thresholdMillis > baselineMillis ? newBuilder(reason) : empty();
  }

  public static ValidatorBuilder isNull(Object test, String reason) {
    return test == null ? newBuilder(reason) : empty();
  }

  public static ValidatorBuilder isNotNull(Object test, String reason) {
    return test != null ? newBuilder(reason) : empty();
  }

  public static ValidatorBuilder isEmpty(String test, String reason) {
    return test == null || test.isEmpty() ? newBuilder(reason) : empty();
  }

  public static ValidatorBuilder isNotEmpty(String test, String reason) {
    return test != null && !test.isEmpty() ? newBuilder(reason) : empty();
  }

  public static ValidatorBuilder isEmpty(List<?> test, String reason) {
    return test == null || test.isEmpty() ? newBuilder(reason) : empty();
  }

  public static ValidatorBuilder isLtEqZero(int test, String reason) {
    return test <= 0 ? newBuilder(reason) : empty();
  }

  public static ValidatorBuilder isGtLimit(int test, int limit, String reason) {
    return test > limit ? newBuilder(reason) : empty();
  }


  public record ValidatorBuilder(List<String> reasons) {

    public ValidatorBuilder isTrue(boolean test, String reason) {
      return test ? addError(reason) : this;
    }

    public ValidatorBuilder isFalse(boolean test, String reason) {
      return !test ? addError(reason) : this;
    }

    public ValidatorBuilder isFutureDate(Long dateTestMillis, Long thresholdMillis, Long baselineMillis, String reason) {
      return dateTestMillis + thresholdMillis > baselineMillis ? addError(reason) : this;
    }

    public ValidatorBuilder isNull(Object test, String reason) {
      return test == null ? addError(reason) : this;
    }

    public ValidatorBuilder isNotNull(Object test, String reason) {
      return test != null ? addError(reason) : this;
    }

    public ValidatorBuilder isEmpty(String test, String reason) {
      return test == null || test.isEmpty() ? addError(reason) : this;
    }

    public ValidatorBuilder isNotEmpty(String test, String reason) {
      return test != null && !test.isEmpty() ? addError(reason) : this;
    }

    public ValidatorBuilder isEmpty(List<?> test, String reason) {
      return test == null || test.isEmpty() ? addError(reason) : this;
    }

    public ValidatorBuilder isLtEqZero(int test, String reason) {
      return test <= 0 ? addError(reason) : this;
    }

    public ValidatorBuilder isGtLimit(int test, int limit, String reason) {
      return test > limit ? addError(reason) : this;
    }
    public static ValidatorBuilder start() {
      return new ValidatorBuilder(List.of());
    }

    private ValidatorBuilder addError(String message) {
      var newReasons = Stream.concat(reasons.stream(), Stream.of(message)).toList();
      return new ValidatorBuilder(newReasons);
    }

    public <T> SuccessOrError<T>  onSuccess(Supplier<T> success) {
      return new SuccessOrError<>(success, reasons);
    }

    public record SuccessOrError<T>(Supplier<T> supplier, List<String> reasons) {
      public T onError(Function<String, T> error) {
        if (reasons.isEmpty()) {
          return supplier.get();
        } else {
          var message = reasons.stream().reduce("", (a, b) -> "%s\n%s".formatted(a, b));
          return error.apply(message);
        }
      }
    }
  }

}
