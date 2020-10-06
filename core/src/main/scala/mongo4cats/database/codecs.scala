package mongo4cats.database

import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.bson.codecs.DocumentCodecProvider
//import org.mongodb.scala.bson.codecs.Macros._

//import scala.reflect.ClassTag

object codecs {

  sealed trait MongoCodecRegistry[T] {
    def get: CodecRegistry
  }

  implicit val documentCodecRegistry: MongoCodecRegistry[Document] = new MongoCodecRegistry[Document] {
    override def get: CodecRegistry = fromRegistries(fromProviders(DocumentCodecProvider()))
  }

//  def codecRegistry[T](implicit ct: ClassTag[T]): MongoCodecRegistry[T] = new MongoCodecRegistry[T] {
//    override def get: CodecRegistry = fromRegistries(fromProviders(ct.runtimeClass))
//  }
}
