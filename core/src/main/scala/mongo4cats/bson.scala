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

package mongo4cats

import org.bson.types.{ObjectId => JObjectId}
import org.bson.{Document => JDocument}
import cats.syntax.alternative._
import cats.syntax.functor._
import mongo4cats.collection.MongoCodecProvider
import org.bson.codecs.DocumentCodecProvider
import org.bson.codecs.configuration.CodecProvider

import java.time.Instant
import java.util.Date
import scala.jdk.CollectionConverters._

object bson {

  type Document = JDocument
  object Document {
    val empty: Document                             = new JDocument()
    def apply[A](entries: Map[String, A]): Document = new JDocument(entries.asInstanceOf[Map[String, AnyRef]].asJava)
    def apply[A](entries: (String, A)*): Document   = apply[A](entries.toMap[String, A])
    def apply[A](key: String, value: A): Document   = apply(key -> value)
    def parse(json: String): Document               = JDocument.parse(json)
    def from(json: String): Document                = parse(json)
    def from(doc: Document): Document               = parse(doc.toJson)

    implicit val codecProvider: MongoCodecProvider[Document] = new MongoCodecProvider[Document] {
      override def get: CodecProvider = new DocumentCodecProvider()
    }
  }

  type ObjectId = JObjectId
  object ObjectId {
    def apply(): ObjectId = new JObjectId()
    def get: ObjectId     = apply()

    /** Constructs a new instance from a 24-byte hexadecimal string representation.
      *
      * @param hex
      *   the string to convert
      * @throws IllegalArgumentException
      *   if the string is not a valid hex string representation of an ObjectId
      */
    def apply(hex: String): ObjectId = new JObjectId(hex)

    /** Constructs a new instance from a 24-byte hexadecimal string representation.
      *
      * @param hex
      *   the string to convert
      */
    def from(hex: String): Either[String, ObjectId] =
      JObjectId
        .isValid(hex)
        .guard[Option]
        .as(apply(hex))
        .toRight(s"Invalid hexadecimal representation of an ObjectId $hex")

    /** Constructs a new instance from the given byte array
      *
      * @param bytes
      *   the byte array
      * @throws IllegalArgumentException
      *   if array is null or not of length 12
      */
    def apply(bytes: Array[Byte]): ObjectId = new JObjectId(bytes)

    /** Constructs a new instance using the given instant.
      *
      * @param instant
      *   the instant
      */
    def apply(instant: Instant) = new JObjectId(Date.from(instant))
  }
}
