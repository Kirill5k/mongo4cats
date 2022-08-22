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
import org.bson.codecs.{ByteCodec, DecoderContext, IntegerCodec, LongCodec, ObjectIdCodec, ShortCodec, StringCodec}

import java.time.Instant
import java.util.UUID

trait AllBsonDecoders extends ScalaVersionDependentBsonDecoders {

  implicit val byteBsonDecoder: BsonDecoder[Byte]                       = instanceFromJavaDecoder(new ByteCodec()).map(_.toByte)
  implicit val shortBsonDecoder: BsonDecoder[Short]                     = instanceFromJavaDecoder(new ShortCodec()).map(_.toShort)
  implicit val intBsonDecoder: BsonDecoder[Int]                         = instanceFromJavaDecoder(new IntegerCodec()).map(_.toInt)
  implicit val longBsonDecoder: BsonDecoder[Long]                       = instanceFromJavaDecoder(new LongCodec()).map(_.toLong)
  implicit val stringBsonDecoder: BsonDecoder[String]                   = instanceFromJavaDecoder(new StringCodec())
  implicit val decodeBsonObjectId: BsonDecoder[org.bson.types.ObjectId] = instanceFromJavaDecoder(new ObjectIdCodec())
  implicit val instantBsonDecoder: BsonDecoder[Instant]                 = instanceFromJavaDecoder(new InstantCodec())

  implicit def optionBsonDecoder[A](implicit decA: BsonDecoder[A]): BsonDecoder[Option[A]] =
    instanceFromJavaDecoder(new JavaDecoder[Option[A]] {
      override def decode(reader: BsonReader, decoderContext: DecoderContext): Option[A] =
        if (reader.getCurrentBsonType() == BsonType.NULL) {
          reader.readNull()
          none
        } else decA.unsafeDecode(reader, decoderContext).some
    })

  implicit def tuple2BsonDecoder[A, B](implicit decA: BsonDecoder[A], decB: BsonDecoder[B]): BsonDecoder[(A, B)] =
    BsonDecoder.instanceFromBsonValue {
      case arr: BsonArray if arr.size() == 2 => (decA.unsafeFromBsonValue(arr.get(0)), decB.unsafeFromBsonValue(arr.get(1)))
      case arr: BsonArray                    => throw new Throwable(s"Not an array of size 2: ${arr}")
      case other                             => throw new Throwable(s"Not an array: ${other}")
    }

  implicit val uuidBsonDecoder: BsonDecoder[UUID] =
    stringBsonDecoder.emap(s => Either.catchNonFatal(UUID.fromString(s)))

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
