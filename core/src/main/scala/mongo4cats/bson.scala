package mongo4cats

import scala.jdk.CollectionConverters._
import org.bson.{Document => JDocument}

object bson {

  type Document = JDocument
  object Document {
    val empty: Document                                = new JDocument()
    def apply[A](key: String, value: String): Document = new JDocument(key, value)
    def apply(entries: Map[String, AnyRef]): Document  = new JDocument(entries.asJava)
    def fromJson(json: String): Document               = JDocument.parse(json)
    def parse(json: String): Document                  = fromJson(json)
  }
}
