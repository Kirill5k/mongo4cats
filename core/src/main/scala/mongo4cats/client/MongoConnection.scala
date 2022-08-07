package mongo4cats.client

sealed abstract class MongoConnectionType(val `type`: String)

object MongoConnectionType {
  final case object Classic extends MongoConnectionType("mongodb")

  final case object Srv extends MongoConnectionType("mongodb+srv")
}

final case class MongoCredential(username: String, password: String)

/** A data model representation of a MongoDB Connection String
  *
  * @param host
  *   The host that serves MongoDB
  * @param port
  *   Port where the MongoDB is served in the host
  * @param credential
  *   Optional credentials that maybe used to establish authentication with the MongoDB
  * @param connectionType
  *   For switching between different MongoDB connection types, see [[MongoConnectionType]] for possible options
  */
sealed abstract class MongoConnection(
    host: String,
    port: Int,
    credential: Option[MongoCredential],
    connectionType: MongoConnectionType
) {
  override def toString: String = {
    val credentialString = credential.map(cred => s"${cred.username}:${cred.password}@").getOrElse("")
    s"${connectionType.`type`}://$credentialString$host:$port"
  }
}

object MongoConnection {

  import MongoConnectionType.Classic

  def apply(
      host: String,
      port: Int = 27017,
      credential: Option[MongoCredential] = None,
      connectionType: MongoConnectionType = Classic
  ): MongoConnection =
    new MongoConnection(host = host, port = port, credential = credential, connectionType = connectionType) {}
}
