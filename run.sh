#!/usr/bin/env bash

JAVA_OPTS="-Xmx4096m" bash -c "sbt jmh:run -i 5 -wi 5 -f1 -t1 \".*StreamingBenchmark.*\""