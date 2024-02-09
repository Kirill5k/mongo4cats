/*
 * Copyright 2020 Kirill5k
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mongo4cats.bson

import mongo4cats.codecs.{CodecRegistry, DocumentCodecProvider, MongoCodecProvider}
import org.bson.codecs.{DecoderContext, EncoderContext}
import org.bson.{BsonDocument => JBsonDocument, BsonDocumentWrapper, Document => JDocument}
import org.bson.codecs.configuration.CodecProvider
import org.bson.conversions.Bson
import org.bson.json.{JsonMode, JsonReader, JsonWriter, JsonWriterSettings}

import java.io.StringWriter
import scala.annotation.tailrec
import scala.collection.immutable.ListMap

sealed abstract class Document extends Bson {
  def keys: Set[String]
  def size: Int
  def isEmpty: Boolean
  def contains(key: String): Boolean
  def remove(key: String): Document
  def filterKeys(predicate: String => Boolean): Document

  def add(key: String, value: BsonValue): Document

  def add[A](keyValuePair: (String, A))(implicit e: BsonValueEncoder[A]): Document = add(keyValuePair._1, e.encode(keyValuePair._2))
  def +=[A](keyValuePair: (String, A))(implicit e: BsonValueEncoder[A]): Document  = add(keyValuePair)

  def merge(other: Document): Document

  def apply(key: String): Option[BsonValue]
  def get(key: String): Option[BsonValue]                               = apply(key)
  def getList(key: String): Option[List[BsonValue]]                     = apply(key).flatMap(_.asList)
  def getObjectId(key: String): Option[ObjectId]                        = apply(key).flatMap(_.asObjectId)
  def getDocument(key: String): Option[Document]                        = apply(key).flatMap(_.asDocument)
  def getBoolean(key: String): Option[Boolean]                          = apply(key).flatMap(_.asBoolean)
  def getString(key: String): Option[String]                            = apply(key).flatMap(_.asString)
  def getDouble(key: String): Option[Double]                            = apply(key).flatMap(_.asDouble)
  def getLong(key: String): Option[Long]                                = apply(key).flatMap(_.asLong)
  def getInt(key: String): Option[Int]                                  = apply(key).flatMap(_.asInt)
  def getAs[A](key: String)(implicit d: BsonValueDecoder[A]): Option[A] = apply(key).flatMap(d.decode)

  def getNested(jsonPath: String): Option[BsonValue] = {
    @tailrec
    def go(currentPath: String, remainingPaths: Array[String], nestedDoc: Document): Option[BsonValue] =
      if (remainingPaths.isEmpty) nestedDoc(currentPath)
      else
        nestedDoc.getDocument(currentPath) match {
          case Some(anotherDoc) => go(remainingPaths.head, remainingPaths.tail, anotherDoc)
          case _                => None
        }

    val paths = jsonPath.split("\\.")
    go(paths.head, paths.tail, this)
  }

  def getNestedAs[A](jsonPath: String)(implicit d: BsonValueDecoder[A]): Option[A] = getNested(jsonPath).flatMap(d.decode)

  def toList: List[(String, BsonValue)]
  def toMap: Map[String, BsonValue]
  def toSet: Set[(String, BsonValue)]

  def toJson: String = {
    val writerSettings = JsonWriterSettings.builder.outputMode(JsonMode.RELAXED).build
    val writer         = new JsonWriter(new StringWriter(), writerSettings)
    DocumentCodecProvider.DefaultCodec.encode(writer, this, EncoderContext.builder.build)
    writer.getWriter.toString
  }

  override def toBsonDocument: JBsonDocument = toBsonDocument(classOf[Document], CodecRegistry.Default)
  override def toBsonDocument[TDocument](documentClass: Class[TDocument], codecRegistry: CodecRegistry): JBsonDocument =
    new BsonDocumentWrapper[Document](this, codecRegistry.get(classOf[Document]))
}

final private class ListMapDocument(
    private val fields: ListMap[String, BsonValue]
) extends Document {
  def apply(key: String): Option[BsonValue]        = fields.get(key)
  def keys: Set[String]                            = fields.keySet
  def size: Int                                    = fields.size
  def isEmpty: Boolean                             = fields.isEmpty
  def contains(key: String): Boolean               = fields.contains(key)
  def filterKeys(predicate: String => Boolean)     = new ListMapDocument(fields.filter(kv => predicate(kv._1)))
  def remove(key: String): Document                = new ListMapDocument(fields - key)
  def merge(other: Document): Document             = new ListMapDocument(fields ++ other.toMap)
  def add(key: String, value: BsonValue): Document = new ListMapDocument(fields + (key -> value))

  def toList: List[(String, BsonValue)] = fields.toList
  def toSet: Set[(String, BsonValue)]   = fields.toSet
  def toMap: Map[String, BsonValue]     = fields

  override def toString: String = toJson
  override def hashCode(): Int  = fields.hashCode()
  override def equals(other: Any): Boolean =
    Option(other) match {
      case Some(doc: Document) if doc.keys == this.keys =>
        doc.toMap.forall { case (k, v) =>
          (v, fields.get(k)) match {
            case (BsonValue.BBinary(bin1), Some(BsonValue.BBinary(bin2))) => bin1.sameElements(bin2)
            case (bv1, Some(bv2))                                     => bv1 == bv2
            case _                                                    => false
          }
        }
      case _ => false
    }
}

object Document {
  val empty: Document = apply()

  def apply(): Document                                         = new ListMapDocument(ListMap.empty)
  def apply(keyValues: Iterable[(String, BsonValue)]): Document = new ListMapDocument(makeListMap(keyValues.toList))
  def apply(keyValues: (String, BsonValue)*): Document          = apply(keyValues.toList)
  def apply(fields: Map[String, BsonValue]): Document           = apply(fields.toList)

  def parse(json: String): Document = DocumentCodecProvider.DefaultCodec.decode(new JsonReader(json), DecoderContext.builder().build())

  def fromJava(document: JDocument): Document = parse(document.toJson)

  implicit val codecProvider: MongoCodecProvider[Document] = new MongoCodecProvider[Document] {
    override def get: CodecProvider = DocumentCodecProvider
  }

  private def makeListMap(keyValues: Iterable[(String, BsonValue)]): ListMap[String, BsonValue] =
    keyValues.foldLeft(ListMap.newBuilder[String, BsonValue]) { case (b, (k, v)) => b += (k -> v) }.result()
}
