package cz.jenda.benchmark.streaming

import scala.util.Random

object DataGenerator {
  def get(bytes: Int): Vector[Byte] = Stream(Random.nextPrintableChar()).map(_.toByte).take(bytes).toVector
}
