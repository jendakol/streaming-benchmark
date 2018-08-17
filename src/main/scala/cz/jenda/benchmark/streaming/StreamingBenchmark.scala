package cz.jenda.benchmark.streaming

import java.util.concurrent.{ArrayBlockingQueue, ThreadPoolExecutor, TimeUnit}

import cats.effect.IO
import cz.jenda.benchmark.streaming.StreamingBenchmark._
import fs2.Stream
import fs2.interop.reactivestreams._
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import monix.reactive.Observable
import org.openjdk.jmh.annotations._
import org.reactivestreams.Publisher

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
class StreamingBenchmark {
  @Param(Array("10")) // it's turned out it doesn't affect results in significant way
  var parallelism: Int = _

  @Param(Array("500", "1000", "5000"))
  var items: Int = _

  @Param(Array("1000", "10000")) // it's turned out it doesn't affect results in significant way
  var size: Int = _

  @Param(Array("10")) // it's turned out it doesn't affect results in significant way
  var threads: Int = _

  var schMonix: SchedulerService = _
  var schFs2FromMonix: SchedulerService = _
  var schFs2: SchedulerService = _
  var ecFs2: ExecutionContext = _

  @Setup
  def setup(): Unit = {
    schMonix = Scheduler {
      new ThreadPoolExecutor(threads, threads, 1, TimeUnit.MINUTES, new ArrayBlockingQueue[Runnable](1000))
    }
    schFs2FromMonix = Scheduler {
      new ThreadPoolExecutor(threads, threads, 1, TimeUnit.MINUTES, new ArrayBlockingQueue[Runnable](1000))
    }
    schFs2 = Scheduler {
      new ThreadPoolExecutor(threads, threads, 1, TimeUnit.MINUTES, new ArrayBlockingQueue[Runnable](1000))
    }
    ecFs2 = ExecutionContext.fromExecutor {
      new ThreadPoolExecutor(threads, threads, 1, TimeUnit.MINUTES, new ArrayBlockingQueue[Runnable](1000))
    }
  }

  @TearDown
  def tearDown(): Unit = {
    schMonix.shutdown()
  }

  @Benchmark
  def testMonix(): Unit = {
    implicit val sch: SchedulerService = schMonix

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
    implicit val sch: SchedulerService = schFs2FromMonix

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
    implicit val sch: SchedulerService = schFs2

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
    implicit val ec: ExecutionContext = ecFs2

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
