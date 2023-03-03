const stopwatch = {
  startTime: 0,
  elapsedTime: 0,
  locationX: 0,
  locationY: 0,
  intervalId: null,

  stopwatchVisible: false,
  locationSet: false,

  setup: function () {},

  draw: function () {
    if (this.stopwatchVisible && !this.locationSet) {
      this.locationX = mouseX;
      this.locationY = mouseY;
    }
    if (this.stopwatchVisible) {
      fill(255, 251, 51, 100);
      rect(this.locationX - 175, this.locationY - 32, 350, 60);
      fill(55);
      textAlign(CENTER);
      textFont('Courier New', 44);
      text(this.formatElapsedTime(), this.locationX, this.locationY);
    }
  },

  keyTyped: function () {
    if (key === 's') {
      if (this.stopwatchVisible) {
        this.stopTimer();
      } else {
        this.showStopwatchStartTimer();
      }
    }
    if (key === 'r') {
      this.hideStopwatch();
    }
  },

  mouseClicked: function () {
    if (this.stopwatchVisible && !this.locationSet) {
      this.locationSet = true;
    }
  },

  showStopwatchStartTimer: function () {
    this.stopwatchVisible = true;
    this.startTimer();
  },

  hideStopwatch: function () {
    this.stopwatchVisible = false;
    this.locationSet = false;
  },

  startTimer: function () {
    this.startTime = Date.now();
    this.intervalId = setInterval(() => {
      this.elapsedTime = Date.now() - this.startTime;
    }, 10);
  },

  stopTimer: function () {
    clearInterval(this.intervalId);
  },

  resetTimer: function () {
    this.startTime = 0;
  },

  formatElapsedTime: function () {
    const hours = Math.floor(this.elapsedTime / (60 * 60 * 1000));
    const minutes = Math.floor(this.elapsedTime / (60 * 1000));
    const seconds = Math.floor((this.elapsedTime % (60 * 1000)) / 1000);
    const milliseconds = Math.floor(this.elapsedTime % 1000);

    return `${this.formatTime(hours, 2)}:${this.formatTime(minutes, 2)}:${this.formatTime(seconds, 2)}:${this.formatTime(milliseconds, 3)}`;
  },

  formatTime: function (time, digits) {
    let timeString = time.toString();
    while (timeString.length < (digits ? digits : 2)) {
      timeString = '0' + timeString;
    }
    return timeString;
  },
};
