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
    const milliseconds = this.elapsedTime;
    const seconds = Math.floor(milliseconds / 1000);
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);

    const secondsLeft = seconds % 60;
    const minutesLeft = minutes % 60;

    return [
      //
      hours.toString(),
      minutesLeft.toString().padStart(2, '0'),
      secondsLeft.toString().padStart(2, '0'),
      (milliseconds % 1000).toString().padStart(3, '0'),
    ].join(':');
  },
};
