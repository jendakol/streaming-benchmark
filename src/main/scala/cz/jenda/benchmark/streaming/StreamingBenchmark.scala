package cz.jenda.benchmark.streaming

import java.util.concurrent.{ArrayBlockingQueue, ForkJoinPool, ThreadPoolExecutor, TimeUnit}

import fs2.Stream
import cz.jenda.benchmark.streaming.StreamingBenchmark._
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
  @Param(Array("2"))
//  @Param(Array("2", "5", "10", "20", "50"))
  var parallelism: Int = _

  @Param(Array("10", "20"))
//  @Param(Array("10", "50", "100", "500", "1000", "5000"))
  var items: Int = _

  @Param(Array("100", "200"))
//  @Param(Array("100", "500", "1000", "5000", "10000", "50000", "100000", "500000"))
  var size: Int = _

  @Param(Array("10"))
//  @Param(Array("5", "10", "20", "50", "100"))
  var threads: Int = _

  implicit var sch: SchedulerService = _

  @Setup
  def setup(): Unit = {
    sch = Scheduler {
      new ThreadPoolExecutor(threads, threads, 1, TimeUnit.MINUTES, new ArrayBlockingQueue[Runnable](1000))
    }
  }

  @TearDown
  def tearDown(): Unit = {
    sch.shutdown()
  }

  @Benchmark
  def testMonix(): Unit = {
    Observable
      .range(1, items)
      .mapParallelUnordered(parallelism) { _ =>
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
