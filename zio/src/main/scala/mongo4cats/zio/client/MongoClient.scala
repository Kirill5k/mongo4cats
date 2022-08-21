package mongo4cats.zio.client

import com.mongodb.reactivestreams.client.{MongoClient => JMongoClient}
import mongo4cats.bson.Document
import mongo4cats.client.ClientSessionOptions
import mongo4cats.zio.database.MongoDatabase
import zio.Task

final private class ZioMongoClient(
    val underlying: JMongoClient
) extends MongoClient {
  def getDatabase(name: String): Task[MongoDatabase]                   = ???
  def listDatabaseNames: Task[Iterable[String]]                        = ???
  def listDatabases: Task[Iterable[Document]]                          = ???
  def listDatabases(session: ClientSession): Task[Iterable[Document]]  = ???
  def startSession(options: ClientSessionOptions): Task[ClientSession] = ???
}

object MongoClient {}
