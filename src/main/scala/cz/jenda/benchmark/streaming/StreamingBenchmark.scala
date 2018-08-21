package cz.jenda.benchmark.streaming

import java.util.concurrent._

import cats.Traverse
import cats.effect.{Concurrent, IO}
import cats.syntax.all._
import cz.jenda.benchmark.streaming.StreamingBenchmark._
import fs2.async.mutable.Semaphore
import fs2.interop.reactivestreams._
import fs2.{Chunk, Pure, Stream}
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import monix.reactive.Observable
import org.openjdk.jmh.annotations._
import org.reactivestreams.Publisher

import scala.concurrent._
import scala.concurrent.duration.Duration

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
class StreamingBenchmark {
  @Param(Array("10")) // it's turned out it doesn't affect results in significant way
  var parallelism: Int = _

  @Param(Array("1000", "5000"))
  var items: Int = _

  @Param(Array("1000", "10000")) // it's turned out it doesn't affect results in significant way
  var size: Int = _

  @Param(Array("10")) // it's turned out it doesn't affect results in significant way
  var threads: Int = _

  var scheduler: SchedulerService = _
  var execContext: ExecutionContextExecutorService = _

  @Setup
  def setup(): Unit = {
    scheduler = Scheduler {
      new ThreadPoolExecutor(threads, threads, 1, TimeUnit.MINUTES, new ArrayBlockingQueue[Runnable](1000))
    }
    execContext = ExecutionContext.fromExecutorService {
      new ThreadPoolExecutor(threads, threads, 1, TimeUnit.MINUTES, new ArrayBlockingQueue[Runnable](1000))
    }
  }

  @TearDown
  def tearDown(): Unit = {
    scheduler.shutdown()
    execContext.shutdown()
  }

  @Benchmark
  def testMonix(): Unit = {
    implicit val sch: SchedulerService = scheduler

    Observable
      .range(1, items)
      .mapParallelUnordered[Array[Byte]](parallelism) { _ =>
        Task {
          DataGenerator.get(size)
        }
      }
      .map(_.length)
      .reduce(_ + _)
      .toListL
      .runAndWait
  }

  @Benchmark
  def testFs2FromMonix(): Unit = {
    implicit val sch: SchedulerService = scheduler

    val publisher: Publisher[Array[Byte]] = {
      Observable
        .range(1, items)
        .mapParallelUnordered[Array[Byte]](parallelism) { _ =>
          Task {
            DataGenerator.get(size)
          }
        }
        .toReactivePublisher[Array[Byte]]
    }

    publisher
      .toStream[IO]()
      .map(_.length)
      .reduce(_ + _)
      .compile
      .toList
      .runAndWait
  }

  @Benchmark
  def testFs2Task(): Unit = {
    implicit val sch: SchedulerService = scheduler

    Stream
      .range(1, items)
      .mapAsyncUnordered[Task, Array[Byte]](parallelism) { _ =>
        Task {
          DataGenerator.get(size)
        }
      }
      .map(_.length)
      .reduce(_ + _)
      .compile
      .toList
      .runAndWait
  }

  @Benchmark
  def testFs2IO(): Unit = {
    implicit val ec: ExecutionContext = execContext

    Stream
      .range(1, items)
      .mapAsyncUnordered[IO, Array[Byte]](parallelism) { _ =>
        IO {
          DataGenerator.get(size)
        }
      }
      .map(_.length)
      .reduce(_ + _)
      .compile
      .toList
      .runAndWait
  }

  @Benchmark
  def testFs2Workaround(): Unit = {
    implicit val ec: ExecutionContext = execContext

    val itemsStream: Stream[Pure, Int] = Stream.range(1, items)

    Stream
      .eval(Semaphore[IO](parallelism))
      .flatMap { guard =>
        Stream.eval(fs2.async.unboundedQueue[IO, Option[Array[Byte]]]).flatMap { queue =>
          queue.dequeue.unNoneTerminate.concurrently {
            itemsStream
              .evalMap[IO, Any] { _ =>
                guard.decrement >>
                  Concurrent[IO].start {
                    IO {
                      DataGenerator.get(size)
                    }.flatMap(x => queue.enqueue1(x.some)).attempt >> guard.increment
                  }
              }
              .onFinalize(queue.enqueue1(None))
          }
        }
      }
      .map(_.length)
      .reduce(_ + _)
      .compile
      .toList
      .runAndWait
  }

  @Benchmark
  def testFs2WorkaroundChunk20(): Unit = {
    implicit val ec: ExecutionContext = execContext

    val itemsStream: Stream[Pure, Chunk[Int]] = Stream.chunkLimit$extension(Stream.range(1, items))(20)

    Stream
      .eval(Semaphore[IO](parallelism))
      .flatMap { guard =>
        Stream.eval(fs2.async.unboundedQueue[IO, Option[Array[Byte]]]).flatMap { queue =>
          queue.dequeue.unNoneTerminate.concurrently {
            itemsStream
              .evalMap[IO, Chunk[Any]] { chunk =>
                Traverse[Chunk].sequence {
                  chunk.map { _ =>
                    guard.decrement >>
                      Concurrent[IO].start {
                        IO {
                          DataGenerator.get(size)
                        }.flatMap(x => queue.enqueue1(x.some)).attempt >> guard.increment
                      }
                  }
                }
              }
              .onFinalize(queue.enqueue1(None))
          }
        }
      }
      .map(_.length)
      .reduce(_ + _)
      .compile
      .toList
      .runAndWait
  }

  @Benchmark
  def testFs2WorkaroundChunk100(): Unit = {
    implicit val ec: ExecutionContext = execContext

    val itemsStream: Stream[Pure, Chunk[Int]] = Stream.chunkLimit$extension(Stream.range(1, items))(100)

    Stream
      .eval(Semaphore[IO](parallelism))
      .flatMap { guard =>
        Stream.eval(fs2.async.unboundedQueue[IO, Option[Array[Byte]]]).flatMap { queue =>
          queue.dequeue.unNoneTerminate.concurrently {
            itemsStream
              .evalMap[IO, Chunk[Any]] { chunk =>
                Traverse[Chunk].sequence {
                  chunk.map { _ =>
                    guard.decrement >>
                      Concurrent[IO].start {
                        IO {
                          DataGenerator.get(size)
                        }.flatMap(x => queue.enqueue1(x.some)).attempt >> guard.increment
                      }
                  }
                }
              }
              .onFinalize(queue.enqueue1(None))
          }
        }
      }
      .map(_.length)
      .reduce(_ + _)
      .compile
      .toList
      .runAndWait
  }

}

object StreamingBenchmark {

  implicit class BlockingOpsTask[A](val ta: Task[A]) extends AnyVal {
    def runAndWait(implicit sch: Scheduler): A = {
      Await.result(ta.runAsync, Duration.Inf)
    }
  }

  implicit class BlockingOpsIO[A](val ta: IO[A]) extends AnyVal {
    def runAndWait: A = {
      Await.result(ta.unsafeToFuture(), Duration.Inf)
    }
  }

}
