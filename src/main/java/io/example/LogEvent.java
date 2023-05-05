package io.example;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogEvent {
  private static final Logger log = LoggerFactory.getLogger(LogEvent.class);

  public static void log(String fromType, String fromId, String toType, String toId) {
    log.info("LogEvent: {}, {}, {}, {}, {}", Instant.now().toEpochMilli(), fromType, fromId, toType, toId);
  }
}
