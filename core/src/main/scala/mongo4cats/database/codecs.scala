package mongo4cats.database

import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries.fromProviders
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.bson.codecs.DocumentCodecProvider
import org.mongodb.scala.bson.codecs.Macros._

object codecs {

  sealed trait MongoCodecRegistry[T] {
    def get: CodecRegistry
  }

  val documentCodecRegistry: MongoCodecRegistry[Document] = new MongoCodecRegistry[Document] {
    override def get: CodecRegistry = fromProviders(DocumentCodecProvider())
  }

  def codecRegistry[T]: MongoCodecRegistry[T] = new MongoCodecRegistry[T] {
    override def get: CodecRegistry = fromProviders(classOf[T])
  }
}
