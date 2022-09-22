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

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.syntax.all._
import mongo4cats.derivation.bson.BsonDecoder.{fastInstance, slowInstance, JavaDecoder}
import org.bson._
import org.bson.codecs.jsr310.InstantCodec
import org.bson.codecs.{
  ByteCodec,
  CharacterCodec,
  DecoderContext,
  IntegerCodec,
  LongCodec,
  ObjectIdCodec,
  ShortCodec,
  StringCodec,
  UuidCodec
}

import java.time.Instant
import java.util.UUID

trait AllBsonDecoders extends ScalaVersionDependentBsonDecoders with TupleBsonDecoders {

  implicit val charBsonDecoder: BsonDecoder[Char]     = fastInstance(new CharacterCodec()).asInstanceOf[BsonDecoder[Char]]
  implicit val byteBsonDecoder: BsonDecoder[Byte]     = fastInstance(new ByteCodec()).asInstanceOf[BsonDecoder[Byte]]
  implicit val shortBsonDecoder: BsonDecoder[Short]   = fastInstance(new ShortCodec()).asInstanceOf[BsonDecoder[Short]]
  implicit val intBsonDecoder: BsonDecoder[Int]       = fastInstance(new IntegerCodec()).asInstanceOf[BsonDecoder[Int]]
  implicit val longBsonDecoder: BsonDecoder[Long]     = fastInstance(new LongCodec()).asInstanceOf[BsonDecoder[Long]]
  implicit val stringBsonDecoder: BsonDecoder[String] = fastInstance(new StringCodec())
  implicit val objectIdBsonDecoder: BsonDecoder[org.bson.types.ObjectId] = fastInstance(new ObjectIdCodec())
  implicit val instantBsonDecoder: BsonDecoder[Instant]                  = fastInstance(new InstantCodec())

  implicit def eitherBsonDecoder[A, B](implicit
      decA: BsonDecoder[A],
      decB: BsonDecoder[B]
  ): BsonDecoder[Either[A, B]] =
    new BsonDecoder[Either[A, B]] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): Either[A, B] = {
        val mark = reader.getMark
        reader.readStartDocument()
        val key = reader.readName()

        if (key == "Right") {
          val r = Right(decB.unsafeDecode(reader, decoderContext))
          reader.readEndDocument()
          r
        } else if (key == "Left") {
          val l = Left(decA.unsafeDecode(reader, decoderContext))
          reader.readEndDocument()
          l
        } else {
          mark.reset()
          throw new Throwable(s"Can't decode Either with key: '$key'")
        }
      }

      override def unsafeFromBsonValue(bson: BsonValue): Either[A, B] =
        if (bson.isInstanceOf[BsonDocument]) {
          val doc        = bson.asInstanceOf[BsonDocument]
          val rightValue = doc.get("Right")

          if (rightValue eq null) {
            val leftValue = doc.get("Left")
            if (leftValue eq null) throw new Throwable(s"Can't decode Either, because there is no 'Left' nor 'Right'")
            else Left(decA.unsafeFromBsonValue(leftValue))
          } else Right(decB.unsafeFromBsonValue(rightValue))
        } else throw new Throwable("Can't decode Either, because it's not a BsonDocument")
    }

  implicit def validatedBsonDecoder[A, B](implicit
      decA: BsonDecoder[A],
      decB: BsonDecoder[B]
  ): BsonDecoder[Validated[A, B]] =
    new BsonDecoder[Validated[A, B]] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): Validated[A, B] = {
        val mark = reader.getMark
        reader.readStartDocument()
        val key = reader.readName()

        if (key == "Valid") {
          val r = Valid(decB.unsafeDecode(reader, decoderContext))
          reader.readEndDocument()
          r
        } else if (key == "Invalid") {
          val l = Invalid(decA.unsafeDecode(reader, decoderContext))
          reader.readEndDocument()
          l
        } else {
          mark.reset()
          throw new Throwable(s"Can't decode Validated with key: '$key'")
        }
      }

      override def unsafeFromBsonValue(bson: BsonValue): Validated[A, B] =
        if (bson.isInstanceOf[BsonDocument]) {
          val doc        = bson.asInstanceOf[BsonDocument]
          val rightValue = doc.get("Valid")

          if (rightValue eq null) {
            val leftValue = doc.get("Invalid")
            if (leftValue eq null) throw new Throwable(s"Can't decode Validated, because there is no 'Valid' nor 'Invalid'")
            else Invalid(decA.unsafeFromBsonValue(leftValue))
          } else Valid(decB.unsafeFromBsonValue(rightValue))
        } else throw new Throwable("Can't decode Validated, because it's not a BsonDocument")
    }

  implicit def optionBsonDecoder[A](implicit decA: BsonDecoder[A]): BsonDecoder[Option[A]] =
    new BsonDecoder[Option[A]] {

      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): Option[A] =
        // if (reader.getState eq State.TYPE) reader.readBsonType()
        if (reader.getCurrentBsonType eq BsonType.NULL) {
          reader.readNull()
          none
        } else Option(decA.unsafeDecode(reader, decoderContext))

      override def unsafeFromBsonValue(bson: BsonValue): Option[A] =
        if ((bson eq null) || bson.isNull) none
        else Option(decA.unsafeFromBsonValue(bson))
    }

  implicit val uuidBsonDecoder: BsonDecoder[UUID] =
    stringBsonDecoder.map(UUID.fromString)

  implicit def mapBsonDecoder[K, V](implicit decK: KeyBsonDecoder[K], decV: BsonDecoder[V]): BsonDecoder[Map[K, V]] =
    slowInstance {
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
