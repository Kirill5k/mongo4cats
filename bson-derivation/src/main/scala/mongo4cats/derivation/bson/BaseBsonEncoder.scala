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
import org.bson.{types, BsonNull, BsonReader, BsonValue, BsonWriter}
import org.bson.codecs._
import org.bson.codecs.jsr310.InstantCodec

import java.time.Instant
import java.util.UUID

trait BaseBsonEncoder {

  implicit val stringBsonEncoder: BsonEncoder[String]           = instanceFromJavaCodec(new StringCodec())
  implicit val byteBsonEncoder: BsonEncoder[Byte]               = instanceFromJavaCodec(new ByteCodec()).asInstanceOf[BsonEncoder[Byte]]
  implicit val shortBsonEncoder: BsonEncoder[Short]             = instanceFromJavaCodec(new ShortCodec()).asInstanceOf[BsonEncoder[Short]]
  implicit val intBsonEncoder: BsonEncoder[Int]                 = instanceFromJavaCodec(new IntegerCodec()).asInstanceOf[BsonEncoder[Int]]
  implicit val longBsonEncoder: BsonEncoder[Long]               = instanceFromJavaCodec(new LongCodec()).asInstanceOf[BsonEncoder[Long]]
  implicit val objectIdBsonEncoder: BsonEncoder[types.ObjectId] = instanceFromJavaCodec(new ObjectIdCodec())
  implicit val instantBsonEncoder: BsonEncoder[Instant]         = instanceFromJavaCodec(new InstantCodec())

  implicit def encodeOption[A](implicit encA: BsonEncoder[A]): BsonEncoder[Option[A]] =
    new BsonEncoder[Option[A]] {
      override def toBsonValue(aOpt: Option[A]): BsonValue =
        aOpt match {
          case Some(a) => encA.toBsonValue(a)
          case None    => BsonNull.VALUE
        }

      override def bsonEncode(writer: BsonWriter, aOpt: Option[A], encoderContext: EncoderContext): Unit =
        aOpt match {
          case Some(a) => encA.bsonEncode(writer, a, encoderContext)
          case None    => writer.writeNull()
        }
    }

  implicit def encodeSeq[L[_] <: Seq[_], A](implicit encA: BsonEncoder[A]): BsonEncoder[L[A]] =
    instanceFromJavaCodec(new JavaEncoder[L[A]] {
      override def encode(writer: BsonWriter, value: L[A], encoderContext: EncoderContext): Unit = {
        writer.writeStartArray()
        value.toSeq.foreach[Unit] { a =>
          encA.bsonEncode(writer, a.asInstanceOf[A], encoderContext)
        }
        writer.writeEndArray()
      }
    })

  implicit def tuple2BsonEncoder[A, B](implicit encA: BsonEncoder[A], encB: BsonEncoder[B]): BsonEncoder[(A, B)] =
    instanceFromJavaCodec(new JavaEncoder[(A, B)] {
      override def encode(writer: BsonWriter, value: (A, B), encoderContext: EncoderContext): Unit = {
        writer.writeStartArray()
        encA.bsonEncode(writer, value._1, encoderContext)
        encB.bsonEncode(writer, value._2, encoderContext)
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
          encV.bsonEncode(writer, v, encoderContext)
        }
        writer.writeEndDocument()
      }
    })
}
