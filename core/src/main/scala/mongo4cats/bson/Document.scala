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
  def filterKeys(predicate: String => Boolean): Document

  def isEmpty: Boolean
  def contains(key: String): Boolean
  def remove(key: String): Document

  def add(keyValuePair: (String, BsonValue)): Document
  def add(key: String, value: BsonValue): Document                                 = add(key -> value)
  def add[A](key: String, value: A)(implicit mapper: BsonValueMapper[A]): Document = add(key -> mapper.toBsonValue(value))

  def merge(other: Document): Document

  def get(key: String): Option[BsonValue]
  def getList(key: String): Option[List[BsonValue]] = get(key).collect { case BsonValue.BArray(value) => value.toList }
  def getObjectId(key: String): Option[ObjectId]    = get(key).collect { case BsonValue.BObjectId(value) => value }
  def getDocument(key: String): Option[Document]    = get(key).collect { case BsonValue.BDocument(value) => value }
  def getBoolean(key: String): Option[Boolean]      = get(key).collect { case BsonValue.BBoolean(value) => value }
  def getString(key: String): Option[String]        = get(key).collect { case BsonValue.BString(value) => value }
  def getDouble(key: String): Option[Double]        = get(key).collect { case BsonValue.BDouble(value) => value }
  def getLong(key: String): Option[Long]            = get(key).collect { case BsonValue.BInt64(value) => value }
  def getInt(key: String): Option[Int]              = get(key).collect { case BsonValue.BInt32(value) => value }

  def getNested(jsonPath: String): Option[BsonValue] = {
    @tailrec
    def go(currentPath: String, remainingPaths: Array[String], nestedDoc: Document): Option[BsonValue] =
      if (remainingPaths.isEmpty) nestedDoc.get(currentPath)
      else
        nestedDoc.getDocument(currentPath) match {
          case Some(anotherDoc: Document) => go(remainingPaths.head, remainingPaths.tail, anotherDoc)
          case _                          => None
        }

    val paths = jsonPath.split("\\.")
    go(paths.head, paths.tail, this)
  }

  def toList: List[(String, BsonValue)]
  def toMap: Map[String, BsonValue]

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
    private[mongo4cats] val fields: ListMap[String, BsonValue]
) extends Document {

  def keys: Set[String]                                = fields.keySet
  def filterKeys(predicate: String => Boolean)         = new ListMapDocument(fields.filter(kv => predicate(kv._1)))
  def isEmpty: Boolean                                 = fields.isEmpty
  def contains(key: String): Boolean                   = fields.contains(key)
  def remove(key: String): Document                    = new ListMapDocument(fields - key)
  def add(keyValuePair: (String, BsonValue)): Document = new ListMapDocument(fields + keyValuePair)
  def merge(other: Document): Document                 = new ListMapDocument(fields ++ other.toMap)
  def get(key: String): Option[BsonValue]              = fields.get(key)

  def toList: List[(String, BsonValue)] = fields.toList
  def toMap: Map[String, BsonValue]     = fields

  override def toString: String = toJson
  override def hashCode(): Int  = fields.hashCode()
  override def equals(other: Any): Boolean =
    Option(other) match {
      case Some(doc: Document) => doc.toMap.equals(fields)
      case _                   => false
    }
}

object Document {
  val empty: Document = apply()

  def apply(): Document                                         = new ListMapDocument(ListMap.empty)
  def apply(keyValues: Iterable[(String, BsonValue)]): Document = new ListMapDocument(makeListMap(keyValues.toList))
  def apply(keyValues: (String, BsonValue)*): Document          = apply(keyValues.toList)
  def apply(fields: Map[String, BsonValue]): Document           = apply(fields.toList)

  def parse(json: String): Document = DocumentCodecProvider.DefaultCodec.decode(new JsonReader(json), DecoderContext.builder().build())

  def fromNative(document: JDocument): Document = parse(document.toJson)

  implicit val codecProvider: MongoCodecProvider[Document] = new MongoCodecProvider[Document] {
    override def get: CodecProvider = DocumentCodecProvider
  }

  private def makeListMap(keyValues: Iterable[(String, BsonValue)]): ListMap[String, BsonValue] =
    keyValues.foldLeft(ListMap.newBuilder[String, BsonValue]) { case (b, (k, v)) => b += (k -> v) }.result()
}
