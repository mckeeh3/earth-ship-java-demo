package io.example.map;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

interface WorldMap {
  static final int zoomMax = 18;
  static final int earthRadiusKm = 6371;

  static String regionIdFor(Region region) {
    return "%d_%1.13f_%1.13f_%1.13f_%1.13f".formatted(
        region.zoom(),
        region.topLeft().lat(),
        region.topLeft().lng(),
        region.botRight().lat(),
        region.botRight().lng());
  }

  static Region regionAbove(Region subRegion) {
    var zoomAbove = subRegion.zoom() - 1;
    if (zoomAbove < 0) {
      return null;
    }

    return regionAtLatLng(zoomAbove, atCenter(subRegion));
  }

  static Region regionForZoom0() {
    return region(0, topLeft(90, -180), botRight(-90, 180));
  }

  // zoom 0 - 1 region 180 / 360
  // zoom 1 - 2 regions 180 / 1 x 180 / 2, 180 lat x 180 lng, on either side of lng 0 meridian
  // zoom 2 - 9 regions 180 / 3 x 180 / 3, 60 lat x 60 lng
  // zoom 3 - 9 regions 60 / 3 x 60 / 3, 20 lat x 20 lng
  // zoom 4 - 4 regions 20 / 2 x 20 / 2, 10 lat x 10 lng
  // zoom 5 - 4 regions 10 / 2 x 10 / 2, 5 lat x 5 lng, subdivide by 4 down to zoom 18
  //
  static List<Region> subRegionsFor(Region region) {
    switch (region.zoom) {
    case 0:
      return subRegionsForZoom1();
    case 1:
    case 2:
      return subRegionsForZoomX(region, 3);
    default:
      return subRegionsForZoomX(region, 2);
    }
  }

  static Region region(int zoom, LatLng topLeft, LatLng botRight) {
    return new Region(zoom, topLeft, botRight, 0, 0);
  }

  private static List<Region> subRegionsForZoom1() {
    var regions = new ArrayList<Region>();
    regions.add(region(1, topLeft(90, -180), botRight(-90, 0)));
    regions.add(region(1, topLeft(90, 0), botRight(-90, 180)));
    return regions;
  }

  private static List<Region> subRegionsForZoomX(Region region, int splits) {
    var length = (region.topLeft.lat - region.botRight.lat) / splits;
    var regions = new ArrayList<Region>();
    if (region.zoom >= zoomMax) {
      return regions;
    }
    IntStream.range(0, splits).forEach(latIndex -> IntStream.range(0, splits).forEach(lngIndex -> {
      var topLeft = topLeft(region.topLeft.lat - latIndex * length, region.topLeft.lng + lngIndex * length);
      var botRight = botRight(region.topLeft.lat - (latIndex + 1) * length, region.topLeft.lng + (lngIndex + 1) * length);
      regions.add(region(region.zoom + 1, topLeft, botRight));
    }));
    return regions;
  }

  static Region regionAtLatLng(int zoom, LatLng latLng) {
    return regionAtLatLng(zoom, latLng, regionForZoom0());
  }

  private static Region regionAtLatLng(int zoom, LatLng latLng, Region region) {
    if (zoom == region.zoom) {
      return region;
    }
    var subRegions = subRegionsFor(region);
    var subRegionOpt = subRegions.stream().filter(r -> r.contains(latLng)).findFirst();
    return subRegionOpt.map(subRegion -> regionAtLatLng(zoom, latLng, subRegion)).orElse(null);
  }

  static LatLng latLng(double lat, double lng) {
    return new LatLng(lat, lng);
  }

  static LatLng topLeft(double lat, double lng) {
    return latLng(lat, lng);
  }

  static LatLng botRight(double lat, double lng) {
    return latLng(lat, lng);
  }

  record LatLng(double lat, double lng) {
    static WorldMap.LatLng fromRadians(double lat, double lng) {
      return new WorldMap.LatLng(Math.toDegrees(lat), Math.toDegrees(lng));
    }
  }

  static LatLng atCenter(Region region) {
    return latLng(region.topLeft.lat - (region.topLeft.lat - region.botRight.lat) / 2,
        region.topLeft.lng + (region.botRight.lng - region.topLeft.lng) / 2);
  }

  record Region(int zoom, LatLng topLeft, LatLng botRight, int deviceCount, int deviceAlarmCount) {
    static Region empty() {
      return from(0, 0, 0, 0, 0);
    }

    boolean isEmpty() {
      return zoom == 0 && topLeft.lat == 0 && topLeft.lng == 0 && botRight.lat == 0 && botRight.lng == 0;
    }

    static Region from(int zoom, LatLng topLeft, LatLng botRight) {
      return new Region(zoom, topLeft, botRight, 0, 0);
    }

    static Region from(int zoom, double topLeftLat, double topLeftLng, double botRightLat, double botRightLng) {
      return new Region(zoom, latLng(topLeftLat, topLeftLng), latLng(botRightLat, botRightLng), 0, 0);
    }

    Region updateCounts(List<Region> subRegions) {
      var deviceCount = subRegions.stream().mapToInt(Region::deviceCount).sum();
      var deviceAlarmCount = subRegions.stream().mapToInt(Region::deviceAlarmCount).sum();
      return new Region(zoom, topLeft, botRight, deviceCount, deviceAlarmCount);
    }

    Region updateCounts(int deviceCount, int deviceAlarmCount) {
      return new Region(zoom, topLeft, botRight, deviceCount, deviceAlarmCount);
    }

    boolean contains(LatLng latLng) {
      return topLeft.lat >= latLng.lat && botRight.lat <= latLng.lat
          && topLeft.lng <= latLng.lng && botRight.lng >= latLng.lng;
    }

    boolean eqShape(Region region) {
      return zoom == region.zoom
          && topLeft.lat == region.topLeft.lat && topLeft.lng == region.topLeft.lng
          && botRight.lat == region.botRight.lat && botRight.lng == region.botRight.lng;
    }
  }
}
