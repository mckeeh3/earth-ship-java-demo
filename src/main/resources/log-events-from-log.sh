#!/bin/bash
grep LogEvent /tmp/earth-ship.log | awk '{for(i=6;i<=NF;i++) printf("%s ",$i);printf("\n")}'
