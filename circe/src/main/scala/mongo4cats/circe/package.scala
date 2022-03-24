package mongo4cats

import com.mongodb.MongoClientException

package object circe extends MongoJsonCodecs {

  final case class MongoJsonParsingException(jsonString: String, message: String) extends MongoClientException(message)
}
