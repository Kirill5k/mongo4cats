package mongo4cats

object errors {

  sealed trait MongoError extends Throwable {
    val message: String

    override def getMessage: String = message
  }

  final case class InsertionError(message: String) extends MongoError
}
