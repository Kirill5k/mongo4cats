package mongo4cats.bench

import cats.syntax.all._

import java.time.Instant
import java.time.temporal.ChronoUnit

final case class BenchCC(
    st: Option[BenchST] = none,
    s1: String = "case class string 1",
    s2: String = "case class string 2",
    s3: String = "case class string 3",
    s4: String = "case class string 4",
    s5: String = "case class string 5",
    i: Int = 10,
    instant: Instant = Instant.now.truncatedTo(ChronoUnit.MILLIS) // Mongo precision is Millis.
)

sealed trait BenchST
object BenchST {
  final case class BenchST1(st2Short: Short) extends BenchST
  final case class BenchST2(
      st1S1: String = "sealed trait string 1",
      st1S2: String = "sealed trait string 2",
      st1S3: String = "sealed trait string 3",
      st1S4: String = "sealed trait string 4",
      st1S5: String = "sealed trait string 5",
      st1I: Int = 20
  ) extends BenchST
}
