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

import mongo4cats.codecs.{CodecRegistry, MongoCodecProvider, MyDocumentCodecProvider}
import org.bson.codecs.{DecoderContext, EncoderContext}
import org.bson.{BsonDocument => JBsonDocument, BsonDocumentWrapper, Document => JDocument}
import org.bson.codecs.configuration.CodecProvider
import org.bson.conversions.Bson
import org.bson.json.{JsonMode, JsonReader, JsonWriter, JsonWriterSettings}

import java.io.StringWriter
import scala.annotation.tailrec
import scala.collection.immutable.ListMap

final class Document private (
    private[mongo4cats] val fields: Map[String, Any]
) extends Bson {

  def merge(other: Document): Document = new Document(fields ++ other.fields)

  def isEmpty: Boolean                            = fields.isEmpty
  def contains(key: String): Boolean              = fields.contains(key)
  def keys: Set[String]                           = fields.keySet
  def remove(key: String): Document               = new Document(fields - key)
  def add[A](keyValuePair: (String, A)): Document = new Document(fields + keyValuePair)
  def add[A](key: String, value: A): Document     = add(key -> value)

  def get[A](key: String): Option[A] = fields.get(key).flatMap(Option(_)).map(_.asInstanceOf[A])

  def getString(key: String): Option[String]     = get[String](key)
  def getInt(key: String): Option[Int]           = get[Int](key)
  def getLong(key: String): Option[Long]         = get[Long](key)
  def getDouble(key: String): Option[Double]     = get[Double](key)
  def getBoolean(key: String): Option[Boolean]   = get[Boolean](key)
  def getObjectId(key: String): Option[ObjectId] = get[ObjectId](key)
  def getDocument(key: String): Option[Document] = get[Document](key)
  def getList[A](key: String): Option[List[A]]   = get[List[A]](key)

  def getNested[A](jsonPath: String): Option[A] = {
    @tailrec
    def go(currentPath: String, remainingPaths: Array[String], nestedDoc: Document): Option[A] =
      if (remainingPaths.isEmpty) nestedDoc.get[A](currentPath)
      else
        nestedDoc.get[Any](currentPath) match {
          case Some(anotherDoc: Document) => go(remainingPaths.head, remainingPaths.tail, anotherDoc)
          case _                          => None
        }

    val paths = jsonPath.split("\\.")
    go(paths.head, paths.tail, this)
  }

  def toJson: String = {
    val writerSettings = JsonWriterSettings.builder.outputMode(JsonMode.RELAXED).build
    val writer         = new JsonWriter(new StringWriter(), writerSettings)
    MyDocumentCodecProvider.DefaultCodec.encode(writer, this, EncoderContext.builder.build)
    writer.getWriter.toString
  }

  override def toBsonDocument: JBsonDocument = toBsonDocument(classOf[Document], CodecRegistry.Default)
  override def toBsonDocument[TDocument](documentClass: Class[TDocument], codecRegistry: CodecRegistry): JBsonDocument =
    new BsonDocumentWrapper[Document](this, codecRegistry.get(classOf[Document]))

  override def toString: String = fields.toString().replaceAll("ListMap", "Document")
  override def hashCode(): Int  = fields.hashCode()
  override def equals(other: Any): Boolean =
    Option(other) match {
      case Some(doc: Document) => doc.fields.equals(fields)
      case _                   => false
    }
}

object Document {
  val empty: Document = apply()

  def apply(): Document                                                  = new Document(ListMap.empty)
  def apply[A](keyValue: (String, A), keyValues: (String, A)*): Document = new Document(ListMap(keyValue) ++ keyValues.toList)
  def apply[A](fields: Map[String, A]): Document                         = new Document(ListMap(fields.toList: _*))

  def parse(json: String): Document = MyDocumentCodecProvider.DefaultCodec.decode(new JsonReader(json), DecoderContext.builder().build())

  def fromNative(document: JDocument): Document = parse(document.toJson)

  implicit val codecProvider: MongoCodecProvider[Document] = new MongoCodecProvider[Document] {
    override def get: CodecProvider = MyDocumentCodecProvider
  }
}
