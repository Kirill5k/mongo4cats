package mongo4cats.database.queries

import org.mongodb.scala.Observable

private[queries] trait QueryBuilder[O[_] <: Observable[_], T] {
  protected def observable: O[T]
  protected def commands: List[QueryCommand[O, T]]

  protected def applyCommands(): O[T] =
    commands.reverse.foldLeft(observable) {
      case (obs, comm) => comm.run(obs)
    }
}
