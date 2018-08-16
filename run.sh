#!/usr/bin/env bash

JAVA_OPTS="-Xmx4096m"
sbt "jmh:run -prof jmh.extras.JFR -i 10 -wi 10 -w 3 -f 2 -t 2 \".*StreamingBenchmark.*\""