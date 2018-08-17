package cz.jenda.benchmark.streaming

import scala.util.Random

object DataGenerator {
  private val stream = Stream(Random.nextPrintableChar()).map(_.toByte)

  def get(bytes: Int): Array[Byte] = {
    stream.take(bytes).toArray
  }
}
