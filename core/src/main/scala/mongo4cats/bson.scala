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
import mongo4cats.codecs.MongoCodecProvider
import org.bson.codecs.DocumentCodecProvider
import org.bson.codecs.configuration.CodecProvider

import java.time.Instant
import java.util.Date
import java.{util => ju}
import java.util.{Map => JMap, Set => JSet}
import java.util.Map.{Entry => JEntry}
import scala.collection.convert.AsJavaConverters

object bson {

  type Document = JDocument

  object Document extends AsJavaConverters {
    val empty: Document = new JDocument()

    def apply[A](entries: Map[String, A]): Document =
      new JDocument(
        MapOnlyForJDocumentCreation(
          entries.size,
          entries
            .map {
              case (k, v: Iterable[_]) =>
                // Create directly `JEntry` (no `Tuple2` then copied to `JEntry`).
                // No allocation for `scala.collection.convert.AsJavaExtensions.IterableHasAsJava` (which doesn't `extends AnyVal`).
                JEntryOnlyForJDocumentCreation(k, asJava(v))
              case (k, v) => JEntryOnlyForJDocumentCreation(k, v) // Create directly `JEntry`.
            }
        )
      )

    def apply[A](entries: (String, A)*): Document = apply[A](entries.toMap[String, A])
    def apply[A](key: String, value: A): Document = apply(key -> value)
    def parse(json: String): Document             = JDocument.parse(json)
    def from(json: String): Document              = parse(json)
    def from(doc: Document): Document             = parse(doc.toJson)

    implicit val codecProvider: MongoCodecProvider[Document] = new MongoCodecProvider[Document] {
      override def get: CodecProvider = new DocumentCodecProvider()
    }
  }

  type ObjectId = JObjectId
  object ObjectId {
    def apply(): ObjectId             = new JObjectId()
    def get: ObjectId                 = apply()
    def isValid(hex: String): Boolean = JObjectId.isValid(hex)

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
      isValid(hex)
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

final private case class JEntryOnlyForJDocumentCreation[A](
    override val getKey: String,
    override val getValue: A
) extends JEntry[String, A] {

  // Copied from [[scala.collection.convert.JavaCollectionWrappers.MapWrapper.entrySet]].
  // It's important that this implementation conform to the contract
  // specified in the javadocs of java.util.Map.Entry.hashCode
  // See https://github.com/scala/bug/issues/10663
  override def hashCode: Int = ??? // Unused for JDocument creation.
  // (if (getKey == null) 0 else getKey.hashCode()) ^ (if (getValue == null) 0 else getValue.hashCode())

  override def setValue(value: A): A = ??? // Unused for JDocument creation.
}

final private case class MapOnlyForJDocumentCreation[A](
    override val size: Int,
    entries: Iterable[JEntryOnlyForJDocumentCreation[A]]
) extends JMap[String, A] {
  self =>

  /** An Optimized version of [[scala.collection.convert.JavaCollectionWrappers.MapWrapper.entrySet]], implement only what is needed by
    * [[java.util.HashMap#putMapEntries(java.util.Map, boolean)]] (`size` & `entrySet`).
    *   - No allocations for `prev` var.
    *   - No allocations when copying `Tuple2` to `JEntry`.
    */
  override def entrySet: JSet[JEntry[String, A]] =
    new ju.AbstractSet[JEntry[String, A]] {
      override val size: Int = self.size

      override def iterator: ju.Iterator[JEntry[String, A]] = {
        val scalaIterator: Iterator[JEntryOnlyForJDocumentCreation[A]] = entries.iterator

        new ju.Iterator[JEntry[String, A]] {
          def hasNext: Boolean          = scalaIterator.hasNext
          def next(): JEntry[String, A] = scalaIterator.next()

          override def remove(): Unit = ??? // Unused for JDocument creation.
        }
      }
    }

  override def isEmpty: Boolean                           = ??? // Unused for JDocument creation.
  override def containsKey(key: Any): Boolean             = ??? // Unused for JDocument creation.
  override def containsValue(value: Any): Boolean         = ??? // Unused for JDocument creation.
  override def get(key: Any): A                           = ??? // Unused for JDocument creation.
  override def put(key: String, value: A): A              = ??? // Unused for JDocument creation.
  override def remove(key: Any): A                        = ??? // Unused for JDocument creation.
  override def putAll(m: JMap[_ <: String, _ <: A]): Unit = ??? // Unused for JDocument creation.
  override def clear(): Unit                              = ??? // Unused for JDocument creation.
  override def keySet(): JSet[String]                     = ??? // Unused for JDocument creation.
  override def values(): java.util.Collection[A]          = ??? // Unused for JDocument creation.
}
