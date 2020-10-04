package mongo4cats

object errors {

  sealed trait MongoError extends Throwable {
    val message: String

    override def getMessage: String = message
  }

  final case class OperationError(message: String) extends MongoError
}
