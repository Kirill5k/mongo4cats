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

final class Document private (
    private[mongo4cats] val fields: Map[String, BsonValue]
) extends Bson {

  def merge(other: Document): Document         = new Document(fields ++ other.fields)
  def filterKeys(predicate: String => Boolean) = new Document(fields.filter(kv => predicate(kv._1)))

  def isEmpty: Boolean               = fields.isEmpty
  def contains(key: String): Boolean = fields.contains(key)
  def keys: Set[String]              = fields.keySet
  def remove(key: String): Document  = new Document(fields - key)

  def add(keyValuePair: (String, BsonValue)): Document                             = new Document(fields + keyValuePair)
  def add(key: String, value: BsonValue): Document                                 = add(key -> value)
  def add[A](key: String, value: A)(implicit mapper: BsonValueMapper[A]): Document = add(key -> mapper.toBsonValue(value))

  def get(key: String): Option[BsonValue]           = fields.get(key)
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

  def toJson: String = {
    val writerSettings = JsonWriterSettings.builder.outputMode(JsonMode.RELAXED).build
    val writer         = new JsonWriter(new StringWriter(), writerSettings)
    DocumentCodecProvider.DefaultCodec.encode(writer, this, EncoderContext.builder.build)
    writer.getWriter.toString
  }

  override def toBsonDocument: JBsonDocument = toBsonDocument(classOf[Document], CodecRegistry.Default)
  override def toBsonDocument[TDocument](documentClass: Class[TDocument], codecRegistry: CodecRegistry): JBsonDocument =
    new BsonDocumentWrapper[Document](this, codecRegistry.get(classOf[Document]))

  override def toString: String = toJson
  override def hashCode(): Int  = fields.hashCode()
  override def equals(other: Any): Boolean =
    Option(other) match {
      case Some(doc: Document) => doc.fields.equals(fields)
      case _                   => false
    }
}

object Document {
  val empty: Document = apply()

  def apply(): Document                                                               = new Document(ListMap.empty)
  def apply(keyValue: (String, BsonValue), keyValues: (String, BsonValue)*): Document = new Document(ListMap(keyValue) ++ keyValues.toMap)
  def apply(fields: Map[String, BsonValue]): Document                                 = new Document(fields)

  def parse(json: String): Document = DocumentCodecProvider.DefaultCodec.decode(new JsonReader(json), DecoderContext.builder().build())

  def fromNative(document: JDocument): Document = parse(document.toJson)

  implicit val codecProvider: MongoCodecProvider[Document] = new MongoCodecProvider[Document] {
    override def get: CodecProvider = DocumentCodecProvider
  }
}
