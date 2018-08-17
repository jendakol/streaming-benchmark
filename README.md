# Streaming benchmark

## TLDR

We have discovered that parallel mapping over bigger amount of data in FS2 `Stream` is much slower then in Monix `Observable`. This repo contains
a benchmark which should prove it.

## (Full) Scenario:
We have huge database (1 billion rows, each row few kB of data) split into 2048 shards, spread across 42 DB instances. There is a need to
have a library which will be able to select some rows across all shards/DB instances. We use [Slick](https://github.com/slick/slick) as DB
library under the hood, beside other things because it supports streaming of results (through `org.reactivestreams.Publisher`).

We originally did the implementation using `Observable` and `Task` from Monix which gave us quite good results but than we decided to make our library
finally tagless - so we changed the API to `F[A]` and `fs2.Stream[F, A]`.  
Reimplementing the library with FS2 was quite easy but the performance has started to be poor. We did some in-place testing and then decided
to create a standalone benchmark using [SBT JMH](https://github.com/ktoso/sbt-jmh).

Sad thing is the data will be served through HTTP API with http4s which uses FS2 `Stream` so we cannot avoid it's usage in final consequence.

## Usage
```bash
./run.sh
```
or
```bash
JAVA_OPTS="-Xmx4096m"
sbt "jmh:run -i 10 -wi 10 -w 3 -f 2 -t 2 \".*StreamingBenchmark.*\""
```

## Example results

Running with only few iterations and limited set of input combinations gave this result:
```
[info] Benchmark                            (items)  (parallelism)  (size)  (threads)  Mode  Cnt    Score   Error  Units
[info] StreamingBenchmark.testFs2FromMonix      500              5   10000         10  avgt    2   16,107          ms/op
[info] StreamingBenchmark.testFs2IO             500              5   10000         10  avgt    2   90,676          ms/op
[info] StreamingBenchmark.testFs2Task           500              5   10000         10  avgt    2  126,431          ms/op
[info] StreamingBenchmark.testMonix             500              5   10000         10  avgt    2    3,665          ms/op
```

It's turned out that the only thing really affecting results is number of items in original set however the overhead is still +- the same.
I ran complex testing with big set of inputs with results being stored in [GSheet](https://docs.google.com/spreadsheets/d/1GwfXBOrdICDowPqZSifzJ54MSApUhO1jnUvYx8sc6TU/edit?usp=sharing).

Since these results I did some benchmark optimizations but it improved the results only a little bit (and only for FS2).