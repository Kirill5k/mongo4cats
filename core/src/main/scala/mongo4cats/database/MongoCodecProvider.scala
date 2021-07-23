package mongo4cats.database

import org.bson.codecs.configuration.CodecProvider

trait MongoCodecProvider[T] {
  def get: CodecProvider
}
