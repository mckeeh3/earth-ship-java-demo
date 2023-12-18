package io.example.common;

public record Result(boolean success, String message) {

  public static Result successful() {
    return new Result(true, null);
  }

  public static Result failure(String message) {
    return new Result(false, message);
  }

  public boolean isSuccess() {
    return success;
  }
}
