package io.example.map;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import io.example.map.DeviceEntity.AlarmChangedEvent;
import io.example.map.WorldMap.LatLng;
import kalix.javasdk.view.View;
import kalix.springsdk.annotations.Query;
import kalix.springsdk.annotations.Subscribe;
import kalix.springsdk.annotations.Table;
import kalix.springsdk.annotations.ViewId;

@ViewId("devices-by-location-v1")
@Table("devices_by_location")
@Subscribe.EventSourcedEntity(value = DeviceEntity.class, ignoreUnknown = true)
public class DevicesByLocationView extends View<DevicesByLocationView.DeviceViewRow> {
  private static final Logger log = LoggerFactory.getLogger(DevicesByLocationView.class);

  @GetMapping("/devices/by-location/{topLeftLat}/{topLeftLng}/{botRightLat}/{botRightLng}")
  @Query("""
      SELECT * AS devices, next_page_token() AS nextPageToken, has_more() AS hasMore
        FROM devices_by_location
      OFFSET page_token_offset(:nextPageToken)
       LIMIT 1000
       WHERE position.lat <= :topLeftLat
         AND position.lng >= :topLeftLng
         AND position.lat >= :botRightLat
         AND position.lng <= :botRightLng
      """)
  public Devices getDevicesByLocation(
      @PathVariable Double topLeftLat,
      @PathVariable Double topLeftLng,
      @PathVariable Double botRightLat,
      @PathVariable Double botRightLng,
      @RequestParam(required = false) String nextPageToken) {
    return null;
  }

  public UpdateEffect<DeviceViewRow> on(DeviceEntity.DeviceCreatedEvent event) {
    log.debug("State: {}\nEvent: {}", viewState(), event);
    return effects().updateState(new DeviceViewRow(event.deviceId(), event.position(), false));
  }

  public UpdateEffect<DeviceViewRow> on(DeviceEntity.AlarmChangedEvent event) {
    log.debug("State: {}\nEvent: {}", viewState(), event);
    return effects().updateState(viewState().on(event));
  }

  public record DeviceViewRow(String deviceId, LatLng position, boolean alarmOn) {

    public DeviceViewRow on(AlarmChangedEvent event) {
      return new DeviceViewRow(deviceId, position, event.alarmOn());
    }
  }

  public record Devices(Collection<DeviceViewRow> devices, String nextPageToken, boolean hasMore) {}
}
