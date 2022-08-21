package mongo4cats.zio.database

import com.mongodb.{ReadConcern, ReadPreference, WriteConcern}
import com.mongodb.reactivestreams.client.{MongoDatabase => JMongoDatabase}
import mongo4cats.bson.Document
import mongo4cats.codecs.CodecRegistry
import mongo4cats.database.CreateCollectionOptions
import mongo4cats.zio.client.ClientSession
import mongo4cats.zio.collection.MongoCollection
import org.bson.conversions.Bson
import zio.Task

import scala.reflect.ClassTag

final private class ZioMongoDatabase(
    val underlying: JMongoDatabase
) extends MongoDatabase {
  def withReadPreference(readPreference: ReadPreference): MongoDatabase = ???
  def withWriteConcern(writeConcert: WriteConcern): MongoDatabase       = ???
  def witReadConcern(readConcern: ReadConcern): MongoDatabase           = ???
  def withAddedCodec(codecRegistry: CodecRegistry): MongoDatabase       = ???

  def listCollectionNames: Task[Iterable[String]]                                                       = ???
  def listCollectionNames(session: ClientSession): Task[Iterable[String]]                               = ???
  def listCollections: Task[Iterable[Document]]                                                         = ???
  def listCollections(session: ClientSession): Task[Iterable[Document]]                                 = ???
  def createCollection(name: String, options: CreateCollectionOptions): Task[Unit]                      = ???
  def getCollection[T: ClassTag](name: String, codecRegistry: CodecRegistry): Task[MongoCollection[T]]  = ???
  def runCommand(command: Bson, readPreference: ReadPreference): Task[Document]                         = ???
  def runCommand(session: ClientSession, command: Bson, readPreference: ReadPreference): Task[Document] = ???
  def drop: Task[Unit]                                                                                  = ???
  def drop(clientSession: ClientSession): Task[Unit]                                                    = ???
}

object MongoDatabase {}
