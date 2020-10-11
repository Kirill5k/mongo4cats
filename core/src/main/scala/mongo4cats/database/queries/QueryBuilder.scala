package mongo4cats.database.queries

import cats.effect.{Async, Concurrent, Sync}
import fs2.concurrent.Queue
import mongo4cats.database.helpers.{multipleItemsObserver, streamObserver}
import org.mongodb.scala.Observable

private[queries] trait QueryBuilder[O[_] <: Observable[_], T] {
  def observable: O[T]
  def commands: List[QueryCommand[O, T]]

  def all[F[_]: Async]: F[Iterable[T]] =
    Async[F].async { k =>
      applyCommands().subscribe(multipleItemsObserver(k))
    }

  def stream[F[_]: Concurrent]: fs2.Stream[F, T] =
    for {
      q   <- fs2.Stream.eval(Queue.noneTerminated[F, T])
      _   <- fs2.Stream.eval(Sync[F].delay(applyCommands().subscribe(streamObserver(q))))
      doc <- q.dequeue
    } yield doc

  protected def applyCommands(): O[T] =
    commands.reverse.foldLeft(observable) {
      case (obs, comm) => comm.run(obs)
    }
}
