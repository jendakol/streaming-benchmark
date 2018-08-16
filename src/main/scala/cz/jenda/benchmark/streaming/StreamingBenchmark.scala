package cz.jenda.benchmark.streaming

import java.util.concurrent.{ArrayBlockingQueue, ThreadPoolExecutor, TimeUnit}

import cz.jenda.benchmark.streaming.StreamingBenchmark._
import fs2.Stream
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import monix.reactive.Observable
import org.openjdk.jmh.annotations._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
class StreamingBenchmark {
  @Param(Array("2", "5", "10", "20", "50"))
  var parallelism: Int = _

  @Param(Array("10", "50", "100", "500", "1000", "5000"))
  var items: Int = _

  @Param(Array("100", "500", "1000", "5000", "10000", "50000", "100000", "500000"))
  var size: Int = _

  @Param(Array("5", "10", "20", "50", "100"))
  var threads: Int = _

  var schMonix: SchedulerService = _
  var schFs2: SchedulerService = _

  @Setup
  def setup(): Unit = {
    schMonix = Scheduler {
      new ThreadPoolExecutor(threads, threads, 1, TimeUnit.MINUTES, new ArrayBlockingQueue[Runnable](1000))
    }
    schFs2 = Scheduler {
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
      .mapParallelUnordered[Vector[Byte]](parallelism) { _ =>
        Task {
          DataGenerator.get(size)
        }
      }
      .map(_.size)
      .reduce(_ + _)
      .toListL
      .runAndWait
  }

  @Benchmark
  def testFs2(): Unit = {
    implicit val sch: SchedulerService = schFs2

    Stream
      .range(1, items)
      .mapAsyncUnordered[Task, Vector[Byte]](parallelism) { _ =>
        Task {
          DataGenerator.get(size)
        }
      }
      .map(_.size)
      .reduce(_ + _)
      .compile
      .toList
      .runAndWait
  }

}

object StreamingBenchmark {

  implicit class BlockingOps[A](val ta: Task[A]) extends AnyVal {
    def runAndWait(implicit sch: Scheduler): A = {
      Await.result(ta.runAsync, Duration.Inf)
    }
  }

}
