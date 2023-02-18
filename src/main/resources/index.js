const geoOrdersQueryIntervalMs = 1000;
const generatorQueryIntervalMs = 1000;
const regionQueryIntervalMs = 1000;

const labelColor = [200, 0, 0, 255];
const labelColorRadius = labelColor;
const labelColorRate = labelColor;
const labelColorGenerate = labelColor;
const labelColorGenerated = [20, 80, 40, 255];
const labelColorMapCrossHairs = [200, 0, 0, 255];

const generatorAreaFill = [255, 165, 0, 40];
const generatorAreaStroke = [200, 0, 0, 255];
const generatorRadiusStroke = [200, 0, 0, 255];
const generatorCrossHairsStroke = [200, 0, 0, 255];
const generatorRateStroke = [200, 0, 0, 255];
const generatorLowResolutionStroke = [200, 0, 0, 255];
const generatorGeoOrdersStroke = [200, 0, 0, 255];
const generatorGeneratedStroke = [94, 115, 0, 255];
const generateTotalStroke = [50, 255];
const mapCrossHairsStroke = [0, 0, 150, 255];
const mapCrossHairsDistanceStroke = [200, 0, 0, 255];
const rateGraphFill = [0, 0, 255, 64];
const rateGraphStroke = [0, 0, 50, 255];

const generatorRateMin = 100;
const generatorRateMax = 1_000;
const quadrantTopRight = 1;
const quadrantBottomRight = 2;
const quadrantBottomLeft = 3;
const quadrantTopLeft = 4;

// const urlPrefix = 'https://green-voice-3640.us-east1.kalix.app';
const urlPrefix = 'http://localhost:80';
let queryResponseGeoOrders = [];
let queryResponseGenerators = [];
let queryResponseRegions = [];

const grid = {
  borderWidth: 20,
  ticksHorizontal: 0,
  ticksVertical: 0,
  tickWidth: 0,
  resize: function () {
    const gridWidth = windowWidth - 2 * this.borderWidth;
    this.ticksHorizontal = max(100, (windowWidth / 1920) * 100);
    this.tickWidth = gridWidth / this.ticksHorizontal;
    this.ticksVertical = (windowHeight / windowWidth) * this.ticksHorizontal;
  },
  toX: function (gridX) {
    // convert from grid scale to canvas scale
    return this.borderWidth + gridX * this.tickWidth;
  },
  toY: function (gridY) {
    return this.borderWidth + gridY * this.tickWidth;
  },
  toLength: function (gridLength) {
    return gridLength * this.tickWidth;
  },
  toGridX: function (x) {
    return (x - this.borderWidth) / this.tickWidth;
  },
  toGridY: function (y) {
    return (y - this.borderWidth) / this.tickWidth;
  },
  toGridLength: function (length) {
    return length / this.tickWidth;
  },
  line: function (x1, y1, x2, y2) {
    line(grid.toX(x1), grid.toY(y1), grid.toX(x2), grid.toY(y2));
  },
  rect: function (x, y, w, h) {
    rect(grid.toX(x), grid.toY(y), grid.toLength(w), grid.toLength(h));
  },
};

class Label {
  constructor() {
    this.borderWidth = 20;
    this._x = 0;
    this._y = 0;
    this._w = 0;
    this._h = 0;
    this._border = 0;
    this._text;
    this._key;
    this._value;
    this._textColor;
    this.textStyle;
    this._bgColor;
    this._borderColor;
    this._keyColor;
    this._valueColor;
    this.textHorizontal = LEFT;
    this.textVertical = CENTER;
  }

  x(x) {
    this._x = x;
    return this;
  }

  y(y) {
    this._y = y;
    return this;
  }

  w(w) {
    this._w = w;
    return this;
  }

  h(h) {
    this._h = h;
    return this;
  }

  border(border) {
    this._border = border;
    return this;
  }

  text(text) {
    this._text = text;
    return this;
  }

  key(key) {
    this._key = key;
    return this;
  }

  value(value) {
    this._value = value;
    return this;
  }

  bgColor(bgColor) {
    this._bgColor = bgColor;
    return this;
  }

  borderColor(borderColor) {
    this._borderColor = borderColor;
    return this;
  }

  textColor(textColor) {
    this._textColor = textColor;
    return this;
  }

  textStyle(textStyle) {
    this._textStyle = textStyle;
    return this;
  }

  keyColor(keyColor) {
    this._keyColor = keyColor;
    return this;
  }

  valueColor(valueColor) {
    this._valueColor = valueColor;
    return this;
  }

  textAlign(horizontal, vertical = CENTER) {
    this.textHorizontal = horizontal;
    this.textVertical = vertical;
    return this;
  }

  draw() {
    const cx = grid.toX(this._x);
    const cy = grid.toY(this._y);
    const cw = grid.toLength(this._w);
    const ch = grid.toLength(this._h);
    const cb = grid.toLength(this._border);

    strokeWeight(0);
    fill(this._bgColor || color(0, 0));
    rect(cx, cy, cw, ch);

    textSize(ch - cb * 2);

    if (this._text) {
      textAlign(this.textHorizontal, this.textVertical);
      textStyle(this._textStyle || NORMAL);
      fill(this._textColor || color(0, 0));
      text(this._text, cx, cy);
    }

    if (this._key) {
      textAlign(LEFT, CENTER);
      fill(this._keyColor || color(0, 0));
      text(this._key, cx + cb, cy + ch / 2);
    }

    if (this._value) {
      textAlign(RIGHT, CENTER);
      fill(this._valueColor || color(0, 0));
      text(this._value, cx + cw - cb, cy + ch / 2);
    }
  }
}

class Generator {
  constructor() {
    this.zoom = 0;
    this.lat = 0.0;
    this.lng = 0.0;
    this.radiusLat = 0.0;
    this.radiusLng = 0.0;
    this.radiusKm = 0.0;
    this.ratePerSecond = 0;
    this.rateAngle = 0.0;
    this.geoOrderCountLimit = 0;
    this.geoOrderCountLimitMin = 0;
    this.geoOrderCountLimitMax = 0;
    this.geoOrderCountCurrent = 0;
    this.geoOrderCountAngle = 0.0;
    this.generatedAngle = 0.0;
  }

  position() {
    const latLng = worldMap.pixelToLatLng(width / 2, height / 2);
    const zoom = worldMap.map.getZoom();
    return this.location(latLng.lat, latLng.lng, zoom);
  }

  location(lat, lng, zoom) {
    this.lat = lat;
    this.lng = lng;
    this.zoom = zoom;
    return this;
  }

  setGeoOrderCountLimits() {
    this.geoOrderCountLimitMin = 1000;
    this.geoOrderCountLimitMax = max(2000, round(this.radiusKm / 4) * 1000);
  }

  draw() {
    if (this.zoom > 0 && this.radiusKm == 0) {
      this.position();

      const centerXY = worldMap.latLngToPixel(this.lat, this.lng);
      const radiusXY = min(width, height, max(mouseX, 200));
      const xRadius = (sin((45 * PI) / 180) * radiusXY) / 2;
      const yRadius = (cos((45 * PI) / 180) * radiusXY) / 2;
      const radiusLatLng = worldMap.pixelToLatLng(centerXY.x + xRadius, centerXY.y - yRadius);
      this.radiusLat = radiusLatLng.lat;
      this.radiusLng = radiusLatLng.lng;

      this.#drawArea();
    } else if (this.radiusKm > 0.0 && this.geoOrderCountLimit == 0) {
      this.#drawArea();
      this.#drawGeoOrdersToGenerate();
    } else if (this.geoOrderCountLimit > 0 && this.ratePerSecond == 0) {
      this.#drawArea();
      this.#drawGeoOrdersToGenerate();
      this.#drawRatePerSecond();
    } else if (this.geoOrderCountLimit > 0 && this.#isGeneratorVisible()) {
      const centerXY = worldMap.latLngToPixel(this.lat, this.lng);
      const radiusXY = worldMap.latLngToPixel(this.radiusLat, this.radiusLng);
      const radiusInPixels = sqrt(pow(centerXY.x - radiusXY.x, 2) + pow(centerXY.y - radiusXY.y, 2));

      if (radiusInPixels < 25) {
        this.#drawAreaLowResolution();
      } else {
        this.#drawArea();
        this.#drawGeoOrdersToGenerate();
        this.#drawRatePerSecond();
        this.#drawGeoOrdersGenerated();
      }
    }
  }

  #drawArea() {
    const centerXY = worldMap.latLngToPixel(this.lat, this.lng);
    const radiusXY = worldMap.latLngToPixel(this.radiusLat, this.radiusLng);
    const radiusInPixels = sqrt(pow(centerXY.x - radiusXY.x, 2) + pow(centerXY.y - radiusXY.y, 2));

    this.#drawAreaCircle(centerXY, radiusInPixels);
    this.#drawAreaRadiusDiagonal(centerXY, radiusInPixels);
    this.#drawAreaCrossHairs(centerXY, radiusInPixels);
  }

  #drawAreaCircle(centerXY, radiusInPixels) {
    fill(generatorAreaFill);
    stroke(generatorAreaStroke);
    strokeWeight(1);
    ellipse(centerXY.x, centerXY.y, radiusInPixels * 2);
  }

  #drawAreaRadiusDiagonal(centerXY, radiusInPixels) {
    push();
    translate(centerXY.x, centerXY.y);
    rotate((-45 * PI) / 180);
    strokeWeight(1.25);
    line(radiusInPixels + 20, 0, 50, 0);
    strokeWeight(4);
    line(radiusInPixels, 0, radiusInPixels - 10, 0);
    pop();

    const labelX = centerXY.x + radiusInPixels * cos((45 * PI) / 180) + 20;
    const labelY = centerXY.y - radiusInPixels * sin((45 * PI) / 180) - 20;
    const radiusKm = haversineDistance(this.lat, this.lng, this.radiusLat, this.radiusLng);

    label() //
      .x(grid.toGridX(labelX))
      .y(grid.toGridY(labelY))
      .w(6.5)
      .h(1.6)
      .border(0.3)
      .textColor(labelColorRadius)
      .textAlign(LEFT, CENTER)
      .textStyle(BOLD)
      .text(`R ${radiusKm.toLocaleString()} km`)
      .draw();
  }

  #drawAreaCrossHairs(centerXY, radiusInPixels) {
    const strokeWeightLine = radiusInPixels < 200 ? 1 : 1.25;
    const strokeWeightTick = radiusInPixels < 200 ? 3 : 4;

    push();
    stroke(generatorCrossHairsStroke);
    strokeWeight(strokeWeightLine);
    translate(centerXY.x, centerXY.y);

    rotate((45 * PI) / 180);
    line(-20, 0, -10, 0);
    line(10, 0, 20, 0);
    if (radiusInPixels > 50) {
      line(-radiusInPixels, 0, -50, 0);
      line(radiusInPixels, 0, 50, 0);
    }
    strokeWeight(strokeWeightTick);
    line(-radiusInPixels, 0, -radiusInPixels + 10, 0);
    line(radiusInPixels, 0, radiusInPixels - 10, 0);

    rotate((90 * PI) / 180);
    strokeWeight(strokeWeightLine);
    line(-20, 0, -10, 0);
    line(10, 0, 20, 0);
    if (radiusInPixels > 50) {
      line(radiusInPixels, 0, 50, 0);
    }
    strokeWeight(strokeWeightTick);
    line(radiusInPixels, 0, radiusInPixels - 10, 0);

    if (radiusInPixels > 50) {
      rotate((45 * PI) / 180);
      strokeWeight(strokeWeightLine);
      line(-radiusInPixels, 0, -radiusInPixels - 20, 0);
      line(radiusInPixels, 0, radiusInPixels + 20, 0);
      strokeWeight(strokeWeightTick);
      line(-radiusInPixels, 0, -radiusInPixels + 10, 0);
      line(radiusInPixels, 0, radiusInPixels - 10, 0);

      rotate((90 * PI) / 180);
      strokeWeight(strokeWeightLine);
      line(-radiusInPixels, 0, -radiusInPixels - 20, 0);
      line(radiusInPixels, 0, radiusInPixels + 20, 0);
      strokeWeight(strokeWeightTick);
      line(-radiusInPixels, 0, -radiusInPixels + 10, 0);
      line(radiusInPixels, 0, radiusInPixels - 10, 0);
    }
    pop();
  }

  #drawRatePerSecond() {
    const centerXY = worldMap.latLngToPixel(this.lat, this.lng);
    const radiusXY = worldMap.latLngToPixel(this.radiusLat, this.radiusLng);
    const radiusInPixels = sqrt(pow(centerXY.x - radiusXY.x, 2) + pow(centerXY.y - radiusXY.y, 2));
    const radiusOffset = radiusInPixels + 10;
    const angles = this.quadrantAngles(quadrantBottomLeft);
    const angleRate = this.rateAngle > 0.0 ? this.rateAngle : this.#mouseXtoAngle(angles.start, angles.stop);
    const rate =
      this.ratePerSecond > 0.0
        ? this.ratePerSecond //
        : max(generatorRateMin, min(generatorRateMax, round(map(angleRate, angles.start, angles.stop, generatorRateMin, generatorRateMax))));

    this.#drawAmountGauge({
      centerXY: centerXY,
      radiusXY: radiusXY,
      radiusInPixels: radiusInPixels,
      radiusOffset: radiusOffset,
      angleStart: angles.start,
      angleStop: angles.stop,
      angle: angleRate,
      count: rate,
      countMin: generatorRateMin,
      countMax: generatorRateMax,
      stroke: generatorRateStroke,
      labelColor: labelColorRate,
      labelText: `Rate ${rate.toLocaleString()}`,
      labelAlign: RIGHT,
      textStyle: BOLD,
    });
  }

  #drawGeoOrdersToGenerate() {
    const centerXY = worldMap.latLngToPixel(this.lat, this.lng);
    const radiusXY = worldMap.latLngToPixel(this.radiusLat, this.radiusLng);
    const radiusInPixels = sqrt(pow(centerXY.x - radiusXY.x, 2) + pow(centerXY.y - radiusXY.y, 2));
    const radiusOffset = radiusInPixels + 10;
    const angles = this.quadrantAngles(quadrantTopLeft);
    const angleGeoOrders = this.geoOrderCountAngle > 0.0 ? this.geoOrderCountAngle : this.#mouseXtoAngle(angles.start, angles.stop);
    const geoOrders =
      this.geoOrderCountLimit > 0.0
        ? this.geoOrderCountLimit //
        : max(this.geoOrderCountLimitMin, min(this.geoOrderCountLimitMax, round(map(angleGeoOrders, angles.start, angles.stop, this.geoOrderCountLimitMin, this.geoOrderCountLimitMax))));

    this.#drawAmountGauge({
      centerXY: centerXY,
      radiusXY: radiusXY,
      radiusInPixels: radiusInPixels,
      radiusOffset: radiusOffset,
      angleStart: angles.start,
      angleStop: angles.stop,
      angle: angleGeoOrders,
      count: geoOrders,
      countMin: this.geoOrderCountLimitMin,
      countMax: this.geoOrderCountLimitMax,
      stroke: generatorGeoOrdersStroke,
      labelColor: labelColorGenerate,
      labelText: `Generate ${geoOrders.toLocaleString()}`,
      labelAlign: RIGHT,
      textStyle: BOLD,
    });
  }

  #drawGeoOrdersGenerated() {
    const centerXY = worldMap.latLngToPixel(this.lat, this.lng);
    const radiusXY = worldMap.latLngToPixel(this.radiusLat, this.radiusLng);
    const radiusInPixels = sqrt(pow(centerXY.x - radiusXY.x, 2) + pow(centerXY.y - radiusXY.y, 2));
    const radiusOffset = radiusInPixels + 10;
    const angles = this.quadrantAngles(quadrantBottomRight);
    const angleGenerated = map(this.geoOrderCountCurrent, 0, this.geoOrderCountLimit, angles.start, angles.stop);
    const generated = this.geoOrderCountCurrent;

    this.#drawAmountGauge({
      centerXY: centerXY,
      radiusXY: radiusXY,
      radiusInPixels: radiusInPixels,
      radiusOffset: radiusOffset,
      angleStart: angles.start,
      angleStop: angles.stop,
      angle: angleGenerated,
      count: generated,
      countMin: 0,
      countMax: this.geoOrderCountLimit,
      stroke: generatorGeneratedStroke,
      labelColor: labelColorGenerated,
      labelText: `Generated ${generated.toLocaleString()}`,
      labelAlign: LEFT,
      textStyle: BOLD,
    });
  }

  #drawAmountGauge(gauge) {
    const g = gauge;
    noFill();
    stroke(g.stroke);
    strokeWeight(2);
    arc(g.centerXY.x, g.centerXY.y, g.radiusOffset * 2, g.radiusOffset * 2, g.angleStart, g.angleStop);
    if (g.count >= g.countMin && g.angle > g.angleStart) {
      strokeWeight(5);
      arc(g.centerXY.x, g.centerXY.y, g.radiusOffset * 2, g.radiusOffset * 2, g.angleStart, g.angle);
    }

    push();
    translate(g.centerXY.x, g.centerXY.y);
    rotate(g.angle);
    stroke(g.stroke);
    strokeWeight(2);
    line(g.radiusOffset - 5, 0, g.radiusOffset + 10, 0);
    pop();

    const labelX = g.centerXY.x + (g.radiusOffset + 25) * cos(g.angle);
    const labelY = g.centerXY.y + (g.radiusOffset + 25) * sin(g.angle);

    label() //
      .x(grid.toGridX(labelX))
      .y(grid.toGridY(labelY))
      .h(1.6)
      .border(0.3)
      .textColor(g.labelColor)
      .textStyle(g.textStyle)
      .textAlign(g.labelAlign, CENTER)
      .text(g.labelText)
      .draw();
  }

  #drawAreaLowResolution() {
    const centerXY = worldMap.latLngToPixel(this.lat, this.lng);
    stroke(generatorLowResolutionStroke);
    strokeWeight(10);
    point(centerXY.x, centerXY.y);
  }

  #isGeneratorVisible() {
    const windowXY = { x: 0, y: 0 };
    const circleXY = worldMap.latLngToPixel(this.lat, this.lng);
    const radiusXY = worldMap.latLngToPixel(this.radiusLat, this.radiusLng);
    const circleR = sqrt(pow(circleXY.x - radiusXY.x, 2) + pow(circleXY.y - radiusXY.y, 2));

    let textXY = { x: circleXY.x, y: circleXY.y };

    if (textXY.x < windowXY.x) textXY.x = windowXY.x;
    else if (textXY.x > width) textXY.x = width;
    if (textXY.y < windowXY.y) textXY.y = windowXY.y;
    else if (textXY.y > height) textXY.y = height;

    const distanceXY = { x: circleXY.x - textXY.x, y: circleXY.y - textXY.y };
    const distance = sqrt(distanceXY.x * distanceXY.x + distanceXY.y * distanceXY.y);

    return distance <= circleR;
  }

  quadrantAngles(quadrant) {
    const border = PI * 0.02;
    const start = ((quadrant - 2) * PI) / 2;
    const stop = start + PI / 2;
    return { start: start + border, stop: stop - border };
  }

  #mouseXtoAngle(angleStart, angleStop) {
    const border = 100;
    const mX = mouseX < border ? border : mouseX > width - border ? width - border : mouseX;
    return map(mX, border, width - border, angleStart, angleStop);
  }

  click() {
    if (this.zoom > 0 && this.radiusKm == 0) {
      const centerXY = worldMap.latLngToPixel(this.lat, this.lng);
      const radiusXY = min(width, height, max(mouseX, 200));
      const xRadius = (sin((45 * PI) / 180) * radiusXY) / 2;
      const yRadius = (cos((45 * PI) / 180) * radiusXY) / 2;
      const radiusLatLng = worldMap.pixelToLatLng(centerXY.x + xRadius, centerXY.y - yRadius);
      this.radiusLat = radiusLatLng.lat;
      this.radiusLng = radiusLatLng.lng;
      this.radiusKm = haversineDistance(this.lat, this.lng, radiusLatLng.lat, radiusLatLng.lng);
      this.setGeoOrderCountLimits();
      return;
    } else if (this.radiusKm > 0 && this.geoOrderCountLimit == 0.0) {
      const angles = this.quadrantAngles(quadrantTopLeft);
      const angleGeoOrders = this.#mouseXtoAngle(angles.start, angles.stop);
      this.geoOrderCountAngle = angleGeoOrders;
      // this.geoOrderCountLimit = round(map(angleGeoOrders, angles.start, angles.stop, generatorGeoOrdersMin, generatorGeoOrdersMax));
      this.geoOrderCountLimit = round(map(angleGeoOrders, angles.start, angles.stop, this.geoOrderCountLimitMin, this.geoOrderCountLimitMax));
    } else if (this.geoOrderCountLimit > 0 && this.ratePerSecond == 0.0) {
      const angles = this.quadrantAngles(quadrantBottomLeft);
      const angleRate = this.#mouseXtoAngle(angles.start, angles.stop);
      this.rateAngle = angleRate;
      this.ratePerSecond = round(map(angleRate, angles.start, angles.stop, generatorRateMin, generatorRateMax));
    }
  }
}

class RateGraph {
  constructor() {
    this.lastUpdate = Date.now();
    this.lastGeneratedTotal = 0;
    this.border = 50;
    this.ratesMaxLength = 1000;
    this.rateGraphHeight = 100;

    this.rates = new Array(this.ratesMaxLength).fill({ rate: 0, time: 0 });
  }

  draw() {
    const generatedTotal = generators.reduce((acc, generator) => acc + generator.generated, 0);
    const generateTotal = generators.reduce((acc, generator) => acc + generator.geoOrdersToGenerate, 0);

    if (generatedTotal > 0) {
      this.#drawRates(generatedTotal);
      this.#drawRateGraph(generateTotal, generatedTotal);
    }
  }

  #drawRates(generatedTotal) {
    const now = Date.now();
    const elapsed = now - this.lastUpdate;
    const generatedThisInterval = generatedTotal - this.lastGeneratedTotal;
    const ratePerMs = generatedThisInterval / elapsed;
    const ratePerSecond = ratePerMs * 1000;
    const ratePerSecondMin = round(this.rates.reduce((acc, r) => (r.rate < acc ? r.rate : acc), ratePerSecond));
    const ratePerSecondMax = round(this.rates.reduce((acc, r) => (r.rate > acc ? r.rate : acc), ratePerSecond));

    this.rates.push({ rate: ratePerSecond, time: now });
    this.lastUpdate = now;
    this.lastGeneratedTotal = generatedTotal;
    if (this.rates.length > this.ratesMaxLength) this.rates.shift();

    fill(rateGraphFill);
    noStroke();

    beginShape();
    vertex(this.border, height - this.border);

    this.rates.forEach((r, i) => {
      const x = map(i, 0, this.rates.length, this.border, width - this.border);
      const y = map(r.rate, ratePerSecondMin, ratePerSecondMax, height - this.border, height - this.border - this.rateGraphHeight);
      vertex(x, y);
    });

    vertex(width - this.border, height - this.border);
    endShape(CLOSE);

    if (ratePerSecondMax > 0) {
      strokeWeight(2);
      stroke(rateGraphStroke);
      const x = width - this.border;
      const y1 = height - this.border - this.rateGraphHeight - 20;
      const y2 = y1 + 20;
      const y3 = y2 + this.rateGraphHeight;
      const y4 = y3 + 20;

      line(x - 20, y1, x + 20, y1);
      line(x + 20, y1, x + 20, y2);
      line(x + 20, y3, x + 20, y4);
      line(x - 20, y4, x + 20, y4);
      strokeWeight(0.5);
      line(this.border, y2, x, y2);

      strokeWeight(6);
      point(x + 20, y2);
      point(x + 20, y3);

      label() //
        .x(grid.toGridX(x - 30))
        .y(grid.toGridY(y1))
        .h(1.2)
        .border(0.2)
        .textColor(rateGraphStroke)
        .textStyle(BOLD)
        .textAlign(RIGHT, CENTER)
        .text(`Rate max/s ${ratePerSecondMax.toLocaleString()}`)
        .draw();

      label() //
        .x(grid.toGridX(x - 30))
        .y(grid.toGridY(y4))
        .h(1.2)
        .border(0.2)
        .textColor(rateGraphStroke)
        .textStyle(BOLD)
        .textAlign(RIGHT, CENTER)
        .text(`Rate min/s ${ratePerSecondMin.toLocaleString()}`)
        .draw();

      this.#drawRateAtIndex(this.ratesMaxLength / 4);
      this.#drawRateAtIndex(this.ratesMaxLength / 2);
      this.#drawRateAtIndex((this.ratesMaxLength / 4) * 3);
    }
  }

  #drawRateAtIndex(index) {
    const rate = round(this.rates[index].rate);
    const rateX = map(index, 0, this.rates.length, this.border, width - this.border);
    const rateY = height - (this.border + this.rateGraphHeight + 20);

    strokeWeight(6);
    point(rateX, rateY + 20);

    label() //
      .x(grid.toGridX(rateX))
      .y(grid.toGridY(rateY))
      .h(1.2)
      .border(0.2)
      .textColor(rateGraphStroke)
      .textStyle(BOLD)
      .textAlign(CENTER, CENTER)
      .text(`${rate.toLocaleString()}/s`)
      .draw();
  }

  #drawRateGraph(generateTotal, generatedTotal) {
    if (generatedTotal > 0) {
      const offset = this.border;
      const generatedX = map(generatedTotal, 0, generateTotal, offset, width - offset + 2);
      if (generatedX > offset) {
        strokeWeight(5);
        stroke(generatorGeneratedStroke);
        line(offset, height - offset, generatedX, height - offset);
        strokeWeight(2);
        line(generatedX, height - offset + 5, generatedX, height - offset - 20);

        label() //
          .x(grid.toGridX(generatedX - 10))
          .y(grid.toGridY(height - offset - 20))
          .h(1.6)
          .border(0.3)
          .textColor(generatorGeneratedStroke)
          .textStyle(BOLD)
          .textAlign(RIGHT, CENTER)
          .text(`Generated ${generatedTotal.toLocaleString()}`)
          .draw();

        strokeWeight(2);
        stroke(generateTotalStroke);
        line(generatedX, height - offset, width - offset, height - offset);
      }
      line(offset, height - offset, width - offset, height - offset);
    }
  }
}

let worldMap;
let canvas;

const gridLatLines = [];
const gridLngLines = [];

const drawFPS = 30;
const options = {
  lat: 0,
  lng: 0,
  zoom: 3,
  style: 'http://{s}.tile.osm.org/{z}/{x}/{y}.png',
};

const generators = [];

let currentGenerator = generator();
let worldWideGeoOrderCounts = { geoOrders: 0, alarms: 0 };

function setup() {
  canvas = createCanvas(windowWidth, windowHeight);
  frameRate(drawFPS);

  const mappa = new Mappa('Leaflet');
  worldMap = mappa.tileMap(options);
  worldMap.overlay(canvas);
  worldMap.onChange(mapChanged);
  grid.resize();

  scheduleNextGeoOrderQuery(0);
  scheduleNextGeneratorQuery(0);
  scheduleNextRegionQuery(0);
  scheduleNextRegionGet();
}

function draw() {
  clear();
  drawMapOverlay();
}

const rateGraph = new RateGraph();

// Order is important here
function drawMapOverlay() {
  drawLatLngGrid();
  drawRegions();
  drawGeoOrders();
  drawCrossHairs();
  drawGenerators();
  drawMouseLocation();
  drawZoomAndMouseLocation();
  drawGeoOrderCounts();
  rateGraph.draw();
}

function drawCrossHairs() {
  const x = width / 2;
  const y = height / 2;
  const length = min(width, height) / 4;
  const y1 = y - length;
  const y2 = y - 50;
  const y3 = y + 50;
  const y4 = y + length;
  const x1 = x - length;
  const x2 = x - 50;
  const x3 = x + 50;
  const x4 = x + length;
  const lineStrokeWeight = 1.5;

  stroke(mapCrossHairsStroke);
  strokeWeight(lineStrokeWeight);
  line(x, y - 20, x, y - 10);
  line(x, y + 10, x, y + 20);
  line(x - 20, y, x - 10, y);
  line(x + 10, y, x + 20, y);

  strokeWeight(lineStrokeWeight);
  line(x, y1, x, y2);
  line(x, y3, x, y4);
  line(x1, y, x2, y);

  strokeWeight(5);
  point(x1 - 15, y);
  point(x, y1 - 15);
  point(x, y4 + 15);

  stroke(mapCrossHairsDistanceStroke);
  strokeWeight(lineStrokeWeight * 2);
  line(x3, y, x4, y);
  line(x3, y + 5, x3, y - 10);
  line(x4, y + 5, x4, y - 10);

  const latLng1 = worldMap.pixelToLatLng(x3, y);
  const latLng2 = worldMap.pixelToLatLng(x4, y);

  const distance = haversineDistance(latLng1.lat, latLng1.lng, latLng2.lat, latLng2.lng);

  label() //
    .x(grid.toGridX(x4 + 10))
    .y(grid.toGridY(y))
    .w(6.5)
    .h(1.6)
    .border(0.3)
    .textColor(labelColorMapCrossHairs)
    .textAlign(LEFT, CENTER)
    .textStyle(BOLD)
    .text(`${(distance > 100 ? round(distance) : distance).toLocaleString()} km`)
    .draw();
}

function drawGenerators() {
  if (currentGenerator.ratePerSecond > 0) {
    const generatorId = `generator_${currentGenerator.lat}_${currentGenerator.lng}`;
    currentGenerator.generatorId = generatorId;
    generators.push(currentGenerator);
    postCreateGenerator(currentGenerator);
    currentGenerator = generator();
  }

  currentGenerator.draw();
  generators.forEach((generator) => {
    generator.draw();
  });
}

function drawRegions() {
  stroke(0, 200, 200);
  strokeWeight(0.25);
  queryResponseRegions.forEach((region) => {
    const topLeft = { lat: region.region.topLeft.lat || 0.0, lng: region.region.topLeft.lng || 0.0 };
    const botRight = { lat: region.region.botRight.lat || 0.0, lng: region.region.botRight.lng || 0.0 };
    const topLeftXY = worldMap.latLngToPixel(topLeft);
    const botRightXY = worldMap.latLngToPixel(botRight);
    fill(region.geoOrderAlarmCount && region.geoOrderAlarmCount > 0 ? [200, 0, 0, 50] : [0, 200, 200, 50]);
    rect(topLeftXY.x, topLeftXY.y, botRightXY.x - topLeftXY.x, botRightXY.y - topLeftXY.y);
  });
}

function drawGeoOrders() {
  const zoom = worldMap.zoom();
  const weight = map(zoom, 1, 18, 0, 12);
  strokeWeight(weight);
  queryResponseGeoOrders.forEach((geoOrder) => {
    const geoOrderXY = worldMap.latLngToPixel(geoOrder.position.lat, geoOrder.position.lng);
    stroke(
      geoOrder.readyToShipAt && geoOrder.readyToShipAt.length > 0 //
        ? [50, 180, 80]
        : geoOrder.backOrderedAt && geoOrder.backOrderedAt.length > 0
        ? [230, 0, 0]
        : [70, 110, 230]
    );
    point(geoOrderXY.x, geoOrderXY.y);
  });
}

function drawZoomAndMouseLocation() {
  const zoom = worldMap.zoom();
  const latLng = worldMap.pixelToLatLng(mouseX, mouseY);
  const lat = latLng.lat.toFixed(8);
  const lng = latLng.lng.toFixed(8);
  const h = 1.2;
  const border = 0.2;
  const keyColor = color(255, 255, 0);
  const valueColor = color(255, 255, 255);
  const bgColor = color(0, 0, 75, 125);

  label() //
    .x(2)
    .y(0.1)
    .w(5)
    .h(h)
    .key('Zoom')
    .value(zoom)
    .border(border)
    .bgColor(bgColor)
    .keyColor(keyColor)
    .valueColor(valueColor)
    .draw();
  label() //
    .x(7.05)
    .y(0.1)
    .w(8)
    .h(h)
    .key('Lat')
    .value(lat)
    .border(border)
    .bgColor(bgColor)
    .keyColor(keyColor)
    .valueColor(valueColor)
    .draw();
  label() //
    .x(15.1)
    .y(0.1)
    .w(8)
    .h(h)
    .key('Lng')
    .value(lng)
    .border(border)
    .bgColor(bgColor)
    .keyColor(keyColor)
    .valueColor(valueColor)
    .draw();
}

function drawGeoOrderCounts() {
  const h = 1.2;
  const border = 0.2;
  const keyColor = color(255, 255, 0);
  const valueColor = color(255, 255, 255);
  const bgColor = color(0, 0, 75, 125);
  const bgColorAlarms = color(255, 0, 0, 150);

  label() //
    .x(grid.ticksHorizontal - 21.13)
    .y(0.1)
    .w(13)
    .h(h)
    .key('World wide geoOrders')
    .value(worldWideGeoOrderCounts.geoOrders.toLocaleString())
    .border(border)
    .bgColor(bgColor)
    .keyColor(keyColor)
    .valueColor(valueColor)
    .draw();

  label() //
    .x(grid.ticksHorizontal - 8)
    .y(0.1)
    .w(7)
    .h(h)
    .key('Alarms')
    .value(worldWideGeoOrderCounts.alarms.toLocaleString())
    .border(border)
    .bgColor(bgColorAlarms)
    .keyColor(keyColor)
    .valueColor(valueColor)
    .draw();

  const inViewGeoOrders = queryResponseRegions.reduce((acc, region) => acc + region.geoOrderCount, 0);
  const inViewAlarms = queryResponseRegions.reduce((acc, region) => acc + (region.geoOrderAlarmCount || 0), 0);

  label() //
    .x(grid.ticksHorizontal - 21.13)
    .y(1.4)
    .w(13)
    .h(h)
    .key('In view geoOrders')
    .value(inViewGeoOrders.toLocaleString())
    .border(border)
    .bgColor(bgColor)
    .keyColor(keyColor)
    .valueColor(valueColor)
    .draw();

  label() //
    .x(grid.ticksHorizontal - 8)
    .y(1.4)
    .w(7)
    .h(h)
    .key('')
    .value(inViewAlarms.toLocaleString())
    .border(border)
    .bgColor(bgColorAlarms)
    .keyColor(keyColor)
    .valueColor(valueColor)
    .draw();
}

function drawLatLngGrid() {
  stroke(125);
  strokeWeight(0.5);

  gridLatLines.forEach((latLine) => line(0, latLine.y, windowWidth - 1, latLine.y));
  gridLngLines.forEach((lngLine) => line(lngLine.x, 0, lngLine.x, windowHeight - 1));
}

function drawMouseLocation() {
  drawMouseGridLocation();
}

function drawMouseGridLocation() {
  const region = findRegionUnderMouse();
  if (region) {
    drawRegion(region);
  }

  function findRegionUnderMouse() {
    const mouseLatLng = worldMap.pixelToLatLng(mouseX, mouseY);
    const region = queryResponseRegions.find((r) => {
      const topLeftLat = r.region.topLeft.lat || 0.0;
      const topLeftLng = r.region.topLeft.lng || 0.0;
      const botRightLat = r.region.botRight.lat || 0.0;
      const botRightLng = r.region.botRight.lng || 0.0;
      return (
        topLeftLat > mouseLatLng.lat && //
        topLeftLng < mouseLatLng.lng &&
        botRightLat < mouseLatLng.lat &&
        botRightLng > mouseLatLng.lng
      );
    });
    return region;
  }

  function drawRegion(region) {
    const geoOrderCounts = { geoOrders: region.region.geoOrderCount || 0.0, alarms: region.region.geoOrderAlarmCount || 0.0 };
    const topLeft = { lat: region.region.topLeft.lat || 0.0, lng: region.region.topLeft.lng || 0.0 };
    const botRight = { lat: region.region.botRight.lat || 0.0, lng: region.region.botRight.lng || 0.0 };
    const topLeftXY = worldMap.latLngToPixel(topLeft);
    const botRightXY = worldMap.latLngToPixel(botRight);
    const x = grid.toGridX(topLeftXY.x);
    const yGeoOrders = grid.toGridY(topLeftXY.y) + 0.1;
    const yAlarms = grid.toGridY(botRightXY.y) - 1.1;
    const w = grid.toGridLength(botRightXY.x - topLeftXY.x);
    const h = 1;

    const border = 0.1;
    const bgColorGeoOrders = color(255, 251, 51, 100);
    const bgColorAlarms = color(255, 51, 51, 100);
    const keyColor = color(10, 20, 0);
    const valueColor = color(10, 20, 0);

    if (geoOrderCounts.geoOrders > 0) {
      label() //
        .x(x)
        .y(yGeoOrders)
        .w(w)
        .h(h)
        .key('GeoOrders')
        .value(geoOrderCounts.geoOrders.toLocaleString())
        .border(border)
        .bgColor(bgColorGeoOrders)
        .keyColor(keyColor)
        .valueColor(valueColor)
        .draw();
    }
    if (geoOrderCounts.alarms >= 0) {
      label() //
        .x(x)
        .y(yAlarms)
        .w(w)
        .h(h)
        .key('Alarms')
        .value(geoOrderCounts.alarms.toLocaleString())
        .border(border)
        .bgColor(bgColorAlarms)
        .keyColor(keyColor)
        .valueColor(valueColor)
        .draw();
    }

    stroke(255, 251, 51);
    strokeWeight(2);
    noFill();
    rect(topLeftXY.x, topLeftXY.y, botRightXY.x - topLeftXY.x, botRightXY.y - topLeftXY.y);
  }
}

function mouseClicked(event) {
  currentGenerator.click();
  return false;
}

function keyTyped() {
  if (key === 'g') {
    currentGenerator = generator().position();
  } else if (key === 't') {
    toggleClickedGeoOrder();
  }
}

function keyPressed() {
  if (keyCode === ESCAPE) {
    currentGenerator = generator();
  }
}

function mapReady() {
  worldMap.map.setMinZoom(3);
}

function mapChanged() {
  recalculateLatLngGrid();
}

function recalculateLatLngGrid() {
  const zoom = worldMap.getZoom();
  const totalLatLines = 9 * pow(2, zoom - 3);
  const totalLngLines = 18 * pow(2, zoom - 3);
  const tickLenLat = 180 / totalLatLines;
  const tickLenLng = 360 / totalLngLines;
  const latLngTopLeft = worldMap.pixelToLatLng(0, 0);
  const latLngBotRight = worldMap.pixelToLatLng(windowWidth - 1, windowHeight - 1);
  const topLatGridLine = tickLenLat * Math.trunc(latLngTopLeft.lat / tickLenLat);
  const leftLngGridLine = tickLenLng * Math.trunc(latLngTopLeft.lng / tickLenLng);
  console.log(`zoom ${zoom}, tick len lat ${tickLenLat}, lng ${tickLenLng}`);

  gridLatLines.length = 0;
  gridLngLines.length = 0;

  let latGridLine = topLatGridLine;
  while (latGridLine > latLngBotRight.lat) {
    const latY = worldMap.latLngToPixel({ lat: latGridLine, lng: 0 }).y;
    gridLatLines.push({ lat: latGridLine, y: latY });
    latGridLine -= tickLenLat;
  }
  let lngGridLine = leftLngGridLine;
  while (lngGridLine < latLngBotRight.lng) {
    const lngX = worldMap.latLngToPixel({ lat: 0, lng: lngGridLine }).x;
    gridLngLines.push({ lng: lngGridLine, x: lngX });
    lngGridLine += tickLenLng;
  }
}

function haversineDistance(lat1, lng1, lat2, lng2) {
  const R = 6371; // km
  const dLat = radians(lat2 - lat1);
  const dLng = radians(lng2 - lng1);
  const lat1Rad = radians(lat1);
  const lat2Rad = radians(lat2);

  const a = sin(dLat / 2) * sin(dLat / 2) + sin(dLng / 2) * sin(dLng / 2) * cos(lat1Rad) * cos(lat2Rad);
  const c = 2 * atan2(sqrt(a), sqrt(1 - a));
  const d = R * c;
  return d;
}

function destinationPoint(lat, lng, bearing, distanceKm) {
  const R = 6371; // km
  const d = distanceKm / R;
  const φ1 = lat;
  const λ1 = lng;
  const φ2 = asin(sin(φ1) * cos(d) + cos(φ1) * sin(d) * cos(bearing));
  const λ2 = λ1 + atan2(sin(bearing) * sin(d) * cos(φ1), cos(d) - sin(φ1) * sin(φ2));

  return { lat: φ2, lng: λ2 };
}

function radiusLocation(lat, lng, distanceKm, bearingDeg) {
  const bearing = bearingDeg * (Math.PI / 180);
  const lat1 = lat * (Math.PI / 180);
  const lon1 = lng * (Math.PI / 180);
  const position = destinationPoint(lat1, lon1, bearing, distanceKm);

  return { lat: position.lat * (180 / Math.PI), lng: position.lng * (180 / Math.PI) };
}

function windowResized() {
  const style = getStyleByClassName('leaflet-container');
  if (style) {
    style.width = windowWidth + 'px';
    style.height = windowHeight + 'px';
  }
  worldMap.map.invalidateSize();
  resizeCanvas(windowWidth, windowHeight);
  grid.resize();
}

function getStyleByClassName(className) {
  const element = document.getElementsByClassName(className);
  return element && element[0] ? element[0].style : undefined;
}

function label() {
  return new Label();
}

function generator() {
  return new Generator();
}

function postCreateGenerator(generator) {
  const path = `${urlPrefix}/generator/${generator.generatorId}/create`;
  const body = {
    generatorId: generator.generatorId,
    position: { lat: generator.lat, lng: generator.lng },
    radiusKm: generator.radiusKm,
    geoOrderCountLimit: generator.geoOrderCountLimit,
    ratePerSecond: generator.ratePerSecond,
  };
  httpPost(path, 'json', body, responseCreateGenerator, errorCreateGenerator);

  function responseCreateGenerator(json) {
    console.log('HTTP response, create generator:', json);
  }

  function errorCreateGenerator(error) {
    console.log('HTTP error, create generator:', error);
  }
}

function scheduleNextGeoOrderQuery(lastQueryDurationMs) {
  const timeout = max(1, geoOrdersQueryIntervalMs - lastQueryDurationMs);
  setTimeout(queryGeoOrders, timeout);
}

function queryGeoOrders() {
  const zoom = worldMap.getZoom();
  if (zoom < 10) {
    queryResponseGeoOrders = [];
    scheduleNextGeoOrderQuery(0);
    return;
  }
  const startTimeMs = performance.now();
  const topLeft = worldMap.pixelToLatLng(0, 0);
  const botRight = worldMap.pixelToLatLng(windowWidth - 1, windowHeight - 1);
  let nextPageToken = '';
  let hasMore = false;
  let path = '';
  let geoOrders = [];

  path = `${urlPrefix}/geo-orders/by-location/${topLeft.lat}/${topLeft.lng}/${botRight.lat}/${botRight.lng}?nextPageToken=${nextPageToken}`;
  httpGet(path, 'json', responseGeoOrders, errorGeoOrders);

  function isNotEmpty(json) {
    return json && json.geoOrders && json.geoOrders.length > 0;
  }

  function responseGeoOrders(json) {
    if (isNotEmpty(json)) {
      nextPageToken = json.nextPageToken || '';
      hasMore = json.hasMore || false;
      geoOrders = geoOrders.concat(json.geoOrders);

      if (hasMore) {
        path = `${urlPrefix}/geo-orders/by-location/${topLeft.lat}/${topLeft.lng}/${botRight.lat}/${botRight.lng}?nextPageToken=${nextPageToken}`;
        httpGet(path, 'json', responseGeoOrders, errorGeoOrders);
      } else {
        queryResponseGeoOrders = geoOrders;
        logQueryResponse(startTimeMs, queryResponseGeoOrders, 'geoOrders');
        scheduleNextGeoOrderQuery(performance.now() - startTimeMs);
      }
    } else {
      queryResponseGeoOrders = [];
      logQueryResponse(startTimeMs, queryResponseGeoOrders, 'geoOrders');
      scheduleNextGeoOrderQuery(performance.now() - startTimeMs);
    }
  }

  function errorGeoOrders(error) {
    console.log('HTTP error, query geoOrders:', error);
    scheduleNextGeoOrderQuery(0);
  }
}

function scheduleNextGeneratorQuery(lastQueryDurationMs) {
  const timeout = max(1, generatorQueryIntervalMs - lastQueryDurationMs);
  setTimeout(queryGenerators, timeout);
}

function queryGenerators() {
  const startTimeMs = performance.now();
  const topLeft = worldMap.pixelToLatLng(0, 0);
  const botRight = worldMap.pixelToLatLng(windowWidth - 1, windowHeight - 1);
  const path = `${urlPrefix}/generators/by-location/${topLeft.lat}/${topLeft.lng}/${botRight.lat}/${botRight.lng}`;
  httpGet(path, 'json', responseGenerators, errorGenerators);

  function isNotEmpty(json) {
    return json && json.generators && json.generators.length > 0;
  }

  function responseGenerators(json) {
    queryResponseGenerators = isNotEmpty(json) ? json.generators : [];
    updateGenerators(queryResponseGenerators);
    logQueryResponse(startTimeMs, queryResponseGenerators, 'generators');
    scheduleNextGeneratorQuery(performance.now() - startTimeMs);
  }

  function errorGenerators(error) {
    console.log('HTTP error, query generators:', error);
    scheduleNextGeneratorQuery(performance.now() - startTimeMs);
  }

  function updateGenerators(queryResponse) {
    queryResponse.forEach(updateGenerator);
  }

  function updateGenerator(queryGenerator) {
    if (!queryGenerator.geoOrderCountCurrent) {
      queryGenerator.geoOrderCountCurrent = 0; // TODO remove this when the missing view fields issue is fixed
    }
    const found = generators.find((g) => equal(g, queryGenerator));
    if (found) {
      found.geoOrderCountCurrent = queryGenerator.geoOrderCountCurrent;
    } else {
      const newGenerator = new Generator();
      const radiusLatLng = radiusLocation(queryGenerator.position.lat, queryGenerator.position.lng, queryGenerator.radiusKm, 0);
      const geoOrderAngles = newGenerator.quadrantAngles(quadrantTopLeft);
      const rateAngles = newGenerator.quadrantAngles(quadrantBottomLeft);
      const rateAngle = map(queryGenerator.ratePerSecond, 100, 1000, rateAngles.start, rateAngles.stop);
      newGenerator.generatorId = queryGenerator.generatorId;
      newGenerator.zoom = 18;
      newGenerator.lat = queryGenerator.position.lat;
      newGenerator.lng = queryGenerator.position.lng;
      newGenerator.radiusLat = radiusLatLng.lat;
      newGenerator.radiusLng = radiusLatLng.lng;
      newGenerator.radiusKm = queryGenerator.radiusKm;
      newGenerator.setGeoOrderCountLimits();
      newGenerator.ratePerSecond = queryGenerator.ratePerSecond;
      newGenerator.rateAngle = rateAngle;
      newGenerator.geoOrderCountLimit = queryGenerator.geoOrderCountLimit;
      newGenerator.geoOrderCountCurrent = queryGenerator.geoOrderCountCurrent;
      const geoOrderCountLimitMax = max(queryGenerator.geoOrderCountLimit, newGenerator.geoOrderCountLimitMax);
      const geoOrderAngle = map(queryGenerator.geoOrderCountLimit, newGenerator.geoOrderCountLimitMin, geoOrderCountLimitMax, geoOrderAngles.start, geoOrderAngles.stop);
      newGenerator.geoOrderCountAngle = geoOrderAngle;
      generators.push(newGenerator);
    }
  }

  function equal(gen, queryGen) {
    return gen.generatorId === queryGen.generatorId && gen.lat === queryGen.position.lat && gen.lng === queryGen.position.lng;
  }
}

function scheduleNextRegionQuery(lastQueryDurationMs) {
  const timeout = max(1, regionQueryIntervalMs - lastQueryDurationMs);
  setTimeout(queryRegions, timeout);
}

function queryRegions() {
  const startTimeMs = performance.now();
  const zoom = worldMap.getZoom();
  const topLeft = worldMap.pixelToLatLng(0, 0);
  const botRight = worldMap.pixelToLatLng(windowWidth - 1, windowHeight - 1);
  const path = `${urlPrefix}/regions/by-location/${zoom}/${topLeft.lat}/${topLeft.lng}/${botRight.lat}/${botRight.lng}`;
  httpGet(path, 'json', responseRegions, errorRegions);

  function isNotEmpty(json) {
    return json && json.regions && json.regions.length > 0;
  }

  function responseRegions(json) {
    queryResponseRegions = isNotEmpty(json) ? json.regions : [];
    logQueryResponse(startTimeMs, queryResponseRegions, 'regions');
    scheduleNextRegionQuery(performance.now() - startTimeMs);
  }

  function errorRegions(error) {
    console.log('HTTP error, query regions:', error);
    scheduleNextRegionQuery(performance.now() - startTimeMs);
  }
}

function logQueryResponse(startTimeMs, response, label) {
  const endTimeMs = performance.now();
  const elapsedMs = endTimeMs - startTimeMs;
  console.log(`${new Date().toISOString()} ${elapsedMs.toFixed(0)}ms - ${response.length.toLocaleString()} ${label}`);
}

function scheduleNextRegionGet() {
  setTimeout(getWorldWideGeoOrderCounts, 1000);
}

function getWorldWideGeoOrderCounts() {
  const startTimeMs = performance.now();
  const regionId = '0_90.0000000000000_-180.0000000000000_-90.0000000000000_180.0000000000000';
  const path = `${urlPrefix}/region/${regionId}`;
  httpGet(path, 'json', responseWorldWideGeoOrderCount, errorWorldWideGeoOrderCount);

  function isNotEmpty(json) {
    return json && json.region && json.region.geoOrderCount;
  }

  function responseWorldWideGeoOrderCount(json) {
    worldWideGeoOrderCounts = isNotEmpty(json) //
      ? { geoOrders: json.region.geoOrderCount, alarms: json.region.geoOrderAlarmCount }
      : { geoOrders: 0, alarms: 0 };
    scheduleNextRegionGet();
    logGet(startTimeMs);
  }

  function errorWorldWideGeoOrderCount(error) {
    console.error('HTTP error, query world wide geoOrder count:', error);
    scheduleNextRegionGet();
  }

  function logGet(startTimeMs) {
    const endTimeMs = performance.now();
    const elapsedMs = endTimeMs - startTimeMs;
    const counts = `${worldWideGeoOrderCounts.geoOrders.toLocaleString()} geoOrders, ${worldWideGeoOrderCounts.alarms.toLocaleString()} alarms`;
    console.log(`${new Date().toISOString()} ${elapsedMs.toFixed(0)}ms - ${counts}`);
  }
}

function toggleClickedGeoOrder() {
  if (queryResponseGeoOrders.length === 0) {
    return;
  }
  const mouseLetLng = worldMap.pixelToLatLng(mouseX, mouseY);
  const distanceFromMouse = queryResponseGeoOrders.map((geoOrder) => {
    const distance = haversineDistance(mouseLetLng.lat, mouseLetLng.lng, geoOrder.position.lat, geoOrder.position.lng);
    return { distance, geoOrder };
  });
  const closest = distanceFromMouse.reduce((prev, curr) => (prev.distance < curr.distance ? prev : curr));
  putToggleGeoOrderAlarm(closest.geoOrder);
  console.log('closest', closest);
}

function putToggleGeoOrderAlarm(geoOrder) {
  const path = `${urlPrefix}/geo-order/${geoOrder.geoOrderId}/toggle-alarm`;
  httpDo(path, 'PUT', '', responseToggleGeoOrderAlarm, errorToggleGeoOrderAlarm);

  function responseToggleGeoOrderAlarm(json) {
    console.log('HTTP response, toggle geoOrder alarm:', json);
  }

  function errorToggleGeoOrderAlarm(error) {
    console.log('HTTP error, toggle geoOrder alarm', error);
  }
}
