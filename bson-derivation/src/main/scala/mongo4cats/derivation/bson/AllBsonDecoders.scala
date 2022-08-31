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

package mongo4cats.derivation.bson

import cats.syntax.all._
import mongo4cats.derivation.bson.BsonDecoder.{instanceFromBsonValue, instanceFromJavaDecoder, JavaDecoder}
import org.bson._
import org.bson.codecs.jsr310.InstantCodec
import org.bson.codecs.{ByteCodec, DecoderContext, IntegerCodec, LongCodec, ObjectIdCodec, ShortCodec, StringCodec, UuidCodec}

import java.time.Instant
import java.util.UUID

trait AllBsonDecoders extends ScalaVersionDependentBsonDecoders with TupleBsonDecoders {

  implicit val byteBsonDecoder: BsonDecoder[Byte]     = instanceFromJavaDecoder(new ByteCodec()).asInstanceOf[BsonDecoder[Byte]]
  implicit val shortBsonDecoder: BsonDecoder[Short]   = instanceFromJavaDecoder(new ShortCodec()).asInstanceOf[BsonDecoder[Short]]
  implicit val intBsonDecoder: BsonDecoder[Int]       = instanceFromJavaDecoder(new IntegerCodec()).asInstanceOf[BsonDecoder[Int]]
  implicit val longBsonDecoder: BsonDecoder[Long]     = instanceFromJavaDecoder(new LongCodec()).asInstanceOf[BsonDecoder[Long]]
  implicit val stringBsonDecoder: BsonDecoder[String] = instanceFromJavaDecoder(new StringCodec())
  implicit val objectIdBsonDecoder: BsonDecoder[org.bson.types.ObjectId] = instanceFromJavaDecoder(new ObjectIdCodec())
  implicit val instantBsonDecoder: BsonDecoder[Instant]                  = instanceFromJavaDecoder(new InstantCodec())

  implicit def optionBsonDecoder[A](implicit decA: BsonDecoder[A]): BsonDecoder[Option[A]] =
    new BsonDecoder[Option[A]] {

      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): Option[A] =
        // if (reader.getState eq State.TYPE) reader.readBsonType()
        if (reader.getCurrentBsonType eq BsonType.NULL) {
          reader.readNull()
          none
        } else Option(decA.unsafeDecode(reader, decoderContext))

      override def unsafeFromBsonValue(bson: BsonValue): Option[A] =
        if (bson == null || bson.isNull) none
        else Option(decA.unsafeFromBsonValue(bson))
    }

  implicit val uuidBsonDecoder: BsonDecoder[UUID] =
    stringBsonDecoder.map(UUID.fromString)

  implicit def mapBsonDecoder[K, V](implicit decK: KeyBsonDecoder[K], decV: BsonDecoder[V]): BsonDecoder[Map[K, V]] =
    instanceFromBsonValue {
      case doc: BsonDocument =>
        val mapBuilder = scala.collection.mutable.LinkedHashMap[K, V]()

        doc
          .entrySet()
          .forEach { entry =>
            mapBuilder += (decK(entry.getKey).get -> decV.unsafeFromBsonValue(entry.getValue))
            ()
          }
        mapBuilder.toMap

      case other => throw new Throwable(s"Not a Document: ${other}")
    }
}

object AllBsonDecoders extends AllBsonDecoders
