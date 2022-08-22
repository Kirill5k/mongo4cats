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

import cats.Contravariant
import mongo4cats.AsJava
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson._

/** A type class that provides a conversion from a value of type `A` to a [[BsonValue]] value. */
trait BsonEncoder[A] extends Serializable with AsJava { self =>

  /** Convert a value to BsonValue. */
  def toBsonValue(a: A): BsonValue

  def bsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit

  /** Create a new [[BsonEncoder]] by applying a function to a value of type `B` before encoding as an `A`. */
  final def contramap[B](f: B => A): BsonEncoder[B] =
    new BsonEncoder[B] {
      override def toBsonValue(b: B): BsonValue =
        self.toBsonValue(f(b))

      override def bsonEncode(writer: BsonWriter, b: B, encoderContext: EncoderContext): Unit =
        self.bsonEncode(writer, f(b), encoderContext)
    }

  /** Create a new [[BsonEncoder]] by applying a function to the output of this one.
    */
  final def mapBsonValue(f: BsonValue => BsonValue): BsonEncoder[A] =
    new BsonEncoder[A] {
      override def toBsonValue(a: A): BsonValue = f(self.toBsonValue(a))

      override def bsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit =
        bsonValueCodecSingleton.encode(writer, toBsonValue(a), encoderContext)
    }
}

object BsonEncoder {

  def apply[A](implicit ev: BsonEncoder[A]): BsonEncoder[A] = ev

  def instanceWithBsonValue[A](f: A => BsonValue): BsonEncoder[A] =
    new BsonEncoder[A] {
      override def toBsonValue(a: A): BsonValue = f(a)

      override def bsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit =
        bsonValueCodecSingleton.encode(writer, toBsonValue(a), encoderContext)
    }

  def instanceFromJavaCodec[A](javaEncoder: org.bson.codecs.Encoder[A]): BsonEncoder[A] =
    new BsonEncoder[A] {
      val dummyRoot = "d"

      override def toBsonValue(a: A): BsonValue = {
        val bsonDocument = new BsonDocument(1)
        val writer       = new BsonDocumentWriter(bsonDocument)
        writer.writeStartDocument()
        writer.writeName(dummyRoot)
        javaEncoder.encode(writer, a, bsonEncoderContextSingleton)
        // writer.writeEndDocument()
        bsonDocument.get(dummyRoot)
      }

      override def bsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit =
        javaEncoder.encode(writer, a, encoderContext)
    }

  implicit final val bsonEncoderContravariant: Contravariant[BsonEncoder] =
    new Contravariant[BsonEncoder] {
      final def contramap[A, B](e: BsonEncoder[A])(f: B => A): BsonEncoder[B] = e.contramap(f)
    }
}

trait JavaEncoder[A] extends org.bson.codecs.Encoder[A] {

  def getEncoderClass: Class[A] = ???
}
