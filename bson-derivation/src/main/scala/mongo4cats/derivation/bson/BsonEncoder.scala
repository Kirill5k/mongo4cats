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
import cats.Contravariant
import mongo4cats.AsJava
import mongo4cats.derivation.bson.BsonEncoder.dummyRoot
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson._

/** A type class that provides a conversion from a value of type `A` to a [[BsonValue]] value. */
trait BsonEncoder[A] extends Serializable with AsJava { self =>

  def unsafeBsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit

  /** Convert a value to BsonValue. */
  def unsafeToBsonValue(a: A): BsonValue = {
    println(s"Default Slow unsafeToBsonValue(): ${a}")
    val bsonDocument = new BsonDocument(1)
    val writer       = new BsonDocumentWriter(bsonDocument)
    writer.writeStartDocument()
    writer.writeName(dummyRoot)
    unsafeBsonEncode(writer, a, bsonEncoderContextSingleton)
    // writer.writeEndDocument()
    bsonDocument.get(dummyRoot)
  }

  def safeBsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Either[Throwable, Unit] =
    Either.catchNonFatal(unsafeBsonEncode(writer, a, encoderContext))

  def safeToBsonValue(a: A): Either[Throwable, BsonValue] =
    Either.catchNonFatal(unsafeToBsonValue(a))

  /** Create a new [[BsonEncoder]] by applying a function to a value of type `B` before encoding as an `A`. */
  final def contramap[B](f: B => A): BsonEncoder[B] =
    new BsonEncoder[B] {
      override def unsafeToBsonValue(b: B): BsonValue =
        self.unsafeToBsonValue(f(b))

      override def unsafeBsonEncode(writer: BsonWriter, b: B, encoderContext: EncoderContext): Unit =
        self.unsafeBsonEncode(writer, f(b), encoderContext)
    }

  /** Create a new [[BsonEncoder]] by applying a function to the output of this one.
    */
  final def mapBsonValue(f: BsonValue => BsonValue): BsonEncoder[A] =
    new BsonEncoder[A] {
      override def unsafeToBsonValue(a: A): BsonValue = f(self.unsafeToBsonValue(a))

      override def unsafeBsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit = {
        val bsonValue = unsafeToBsonValue(a)
        bsonValueCodecSingleton.encode(writer, bsonValue, encoderContext)
      }
    }
}

object BsonEncoder {

  val dummyRoot = "d"

  def apply[A](implicit ev: BsonEncoder[A]): BsonEncoder[A] = ev

  def fastInstance[A](javaEncoder: org.bson.codecs.Encoder[A], toBsonValueOpt: A => BsonValue = null): BsonEncoder[A] =
    new BsonEncoder[A] {
      val cachedToBsonFunc: A => BsonValue =
        if (toBsonValueOpt == null) super.unsafeToBsonValue
        else toBsonValueOpt

      override def unsafeToBsonValue(a: A): BsonValue =
        cachedToBsonFunc(a)

      override def unsafeBsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit =
        javaEncoder.encode(writer, a, encoderContext)
    }

  def fastInstance[A](encodeFunc: (BsonWriter, A) => Unit): BsonEncoder[A] =
    new BsonEncoder[A] {
      override def unsafeBsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit =
        encodeFunc(writer, a)
    }

  def slowInstance[A](f: A => BsonValue): BsonEncoder[A] =
    new BsonEncoder[A] {
      override def unsafeToBsonValue(a: A): BsonValue = f(a)

      override def unsafeBsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit =
        bsonValueCodecSingleton.encode(writer, f(a), encoderContext)
    }

  def unsafeEncodeAsBsonDoc[A](a: A)(implicit encA: BsonDocumentEncoder[A]): BsonDocument = {
    val doc = new BsonDocument()
    encA.unsafeBsonEncode(new BsonDocumentWriter(doc), a, bsonEncoderContextSingleton)
    doc
  }

  implicit final val bsonEncoderContravariant: Contravariant[BsonEncoder] =
    new Contravariant[BsonEncoder] {
      final def contramap[A, B](e: BsonEncoder[A])(f: B => A): BsonEncoder[B] = e.contramap(f)
    }
}

trait BsonDocumentEncoder[A] extends BsonEncoder[A] {

  override def unsafeToBsonValue(a: A): BsonDocument = ???
  // super.unsafeToBsonValue(a).asInstanceOf[BsonDocument]

  override def unsafeBsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit = {
    writer.writeStartDocument()
    unsafeFieldsBsonEncode(writer, a, encoderContext)
    writer.writeEndDocument()
  }

  def unsafeFieldsBsonEncode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit
}

trait JavaEncoder[A] extends org.bson.codecs.Encoder[A] {

  def getEncoderClass: Class[A] = ???
}
