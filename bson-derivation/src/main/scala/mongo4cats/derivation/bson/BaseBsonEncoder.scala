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
import mongo4cats.derivation.bson.BsonEncoder.instanceFromJavaCodec
import org.bson.{types, BsonInt32, BsonInt64, BsonNull, BsonObjectId, BsonReader, BsonString, BsonValue, BsonWriter}
import org.bson.codecs._
import org.bson.codecs.jsr310.InstantCodec

import java.time.Instant
import java.util.UUID

trait BaseBsonEncoder {

  implicit val stringBsonEncoder: BsonEncoder[String] =
    instanceFromJavaCodec(new StringCodec(), new BsonString(_))

  implicit val byteBsonEncoder: BsonEncoder[Byte] =
    instanceFromJavaCodec(new ByteCodec(), (b: java.lang.Byte) => new BsonInt32(b.intValue())).asInstanceOf[BsonEncoder[Byte]]

  implicit val shortBsonEncoder: BsonEncoder[Short] =
    instanceFromJavaCodec(new ShortCodec(), (s: java.lang.Short) => new BsonInt32(s.intValue())).asInstanceOf[BsonEncoder[Short]]

  implicit val intBsonEncoder: BsonEncoder[Int] =
    instanceFromJavaCodec(new IntegerCodec(), new BsonInt32(_: Integer)).asInstanceOf[BsonEncoder[Int]]

  implicit val longBsonEncoder: BsonEncoder[Long] =
    instanceFromJavaCodec(new LongCodec(), new BsonInt64(_: java.lang.Long)).asInstanceOf[BsonEncoder[Long]]

  implicit val objectIdBsonEncoder: BsonEncoder[types.ObjectId] =
    instanceFromJavaCodec(new ObjectIdCodec(), new BsonObjectId(_))

  implicit val instantBsonEncoder: BsonEncoder[Instant] = instanceFromJavaCodec(new InstantCodec())

  implicit def encodeOption[A](implicit encA: BsonEncoder[A]): BsonEncoder[Option[A]] =
    new BsonEncoder[Option[A]] {
      override def unsafeToBsonValue(aOpt: Option[A]): BsonValue =
        aOpt.fold[BsonValue](BsonNull.VALUE)(encA.unsafeToBsonValue)

      override def unsafeBsonEncode(writer: BsonWriter, aOpt: Option[A], encoderContext: EncoderContext): Unit =
        aOpt.fold(writer.writeNull())(encA.unsafeBsonEncode(writer, _, encoderContext))
    }

  implicit def arrayBsonEncoder[A](implicit encA: BsonEncoder[A]): BsonEncoder[Array[A]] =
    instanceFromJavaCodec(new JavaEncoder[Array[A]] {
      override def encode(writer: BsonWriter, as: Array[A], encoderContext: EncoderContext): Unit = {
        writer.writeStartArray()
        val len = as.length
        var i   = 0
        while (i < len) {
          encA.unsafeBsonEncode(writer, as(i), encoderContext)
          i += 1
        }
        writer.writeEndArray()
      }
    })

  implicit def seqBsonEncoder[L[_] <: Seq[_], A](implicit encA: BsonEncoder[A]): BsonEncoder[L[A]] =
    instanceFromJavaCodec(new JavaEncoder[L[A]] {
      override def encode(writer: BsonWriter, value: L[A], encoderContext: EncoderContext): Unit = {
        writer.writeStartArray()
        value.toSeq.foreach[Unit] { a =>
          encA.unsafeBsonEncode(writer, a.asInstanceOf[A], encoderContext)
        }
        writer.writeEndArray()
      }
    })

  implicit val uuidBsonEncoder: BsonEncoder[UUID] =
    instanceFromJavaCodec(new StringCodec()).contramap(_.toString)

  implicit def mapBsonEncoder[K, V](implicit encK: KeyBsonEncoder[K], encV: BsonEncoder[V]): BsonEncoder[Map[K, V]] =
    instanceFromJavaCodec(new JavaEncoder[Map[K, V]] {
      override def encode(writer: BsonWriter, kvs: Map[K, V], encoderContext: EncoderContext): Unit = {
        writer.writeStartDocument()
        kvs.foreach { case (k, v) =>
          writer.writeName(encK(k))
          encV.unsafeBsonEncode(writer, v, encoderContext)
        }
        writer.writeEndDocument()
      }
    })
}
