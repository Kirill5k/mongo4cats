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
import mongo4cats.derivation.bson.BsonEncoder.{instanceFromJavaCodec, instanceFromJavaEncoder}
import org.bson.BsonWriter
import org.bson.codecs._

import java.time.Instant
import java.util.UUID

trait MidBsonEncoder {

  implicit val stringBsonEncoder: BsonEncoder[String] = instanceFromJavaCodec(new StringCodec())
  implicit val byteBsonEncoder: BsonEncoder[Byte]     = instanceFromJavaCodec(new ByteCodec()).contramap(_.toByte)
  implicit val shortBsonEncoder: BsonEncoder[Short]   = instanceFromJavaCodec(new ShortCodec()).contramap(_.toShort)
  implicit val intBsonEncoder: BsonEncoder[Int]       = instanceFromJavaCodec(new IntegerCodec()).contramap(_.toInt)
  implicit val longBsonEncoder: BsonEncoder[Long]     = instanceFromJavaCodec(new LongCodec()).contramap(_.toLong)

  implicit val instantBsonEncoder: BsonEncoder[Instant] =
    instanceFromJavaEncoder(new JavaEncoder[Instant] {
      override def encode(writer: BsonWriter, value: Instant, encoderContext: EncoderContext): Unit = {
        writer.writeStartDocument()
        writer.writeString("$date", value.toString)
        writer.writeEndDocument()
      }
    })
  // stringBsonEncoder.contramap[Instant](_.toString).mapBsonValue(v => new BsonDocument("$date", v))

  implicit val encodeBsonObjectId: BsonEncoder[org.bson.types.ObjectId] =
    instanceFromJavaCodec(new ObjectIdCodec())

  implicit def encodeOption[A](implicit encA: BsonEncoder[A]): BsonEncoder[Option[A]] =
    instanceFromJavaEncoder(new JavaEncoder[Option[A]] {
      override def encode(writer: BsonWriter, value: Option[A], encoderContext: EncoderContext): Unit =
        value match {
          case Some(a) => encA.useJavaEncoderFirst(writer, a, encoderContext)
          case None    => writer.writeNull()
        }
    })

  implicit def encodeSeq[L[_] <: Seq[_], A](implicit encA: BsonEncoder[A]): BsonEncoder[L[A]] =
    instanceFromJavaEncoder(new JavaEncoder[L[A]] {
      override def encode(writer: BsonWriter, value: L[A], encoderContext: EncoderContext): Unit = {
        writer.writeStartArray()
        value.toSeq.foreach[Unit] { a =>
          encA.useJavaEncoderFirst(writer, a.asInstanceOf[A], encoderContext)
        }
        writer.writeEndArray()
      }
    })

  implicit def tuple2BsonEncoder[A, B](implicit encA: BsonEncoder[A], encB: BsonEncoder[B]): BsonEncoder[(A, B)] =
    instanceFromJavaEncoder(new JavaEncoder[(A, B)] {
      override def encode(writer: BsonWriter, value: (A, B), encoderContext: EncoderContext): Unit = {
        writer.writeStartArray()
        encA.useJavaEncoderFirst(writer, value._1, encoderContext)
        encB.useJavaEncoderFirst(writer, value._2, encoderContext)
        writer.writeEndArray()
      }
    })

  implicit val uuidBsonEncoder: BsonEncoder[UUID] =
    instanceFromJavaCodec(new StringCodec()).contramap(_.toString)

  implicit def mapBsonEncoder[K, V](implicit encK: KeyBsonEncoder[K], encV: BsonEncoder[V]): BsonEncoder[Map[K, V]] =
    instanceFromJavaEncoder(new JavaEncoder[Map[K, V]] {
      override def encode(writer: BsonWriter, kvs: Map[K, V], encoderContext: EncoderContext): Unit = {
        writer.writeStartDocument()
        kvs.foreach { case (k, v) =>
          writer.writeName(encK(k))
          encV.useJavaEncoderFirst(writer, v, encoderContext)
        }
        writer.writeEndDocument()
      }
    })
}
