package mongo4cats.database

import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.bson.codecs.DocumentCodecProvider


object codecs {

  sealed trait MongoCodecRegistry[T] {
    def get: CodecRegistry
  }

  implicit val documentCodecRegistry: MongoCodecRegistry[Document] = new MongoCodecRegistry[Document] {
    override def get: CodecRegistry = fromRegistries(fromProviders(DocumentCodecProvider()))
  }
}
