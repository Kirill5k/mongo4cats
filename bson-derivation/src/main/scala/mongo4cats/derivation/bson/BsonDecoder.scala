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

import cats.Functor
import cats.syntax.all._
import mongo4cats.derivation.bson.BsonDecoder.Result
import org.bson.codecs.DecoderContext
import org.bson.{BsonDocument, BsonDocumentReader, BsonReader, BsonValue}

/** A type class that provides a way to produce a value of type `A` from a [[org.bson.BsonValue]] value. */
trait BsonDecoder[A] { self =>

  /** Decode the given [[org.bson.BsonValue]]. */
  def fromBsonValue(bson: BsonValue): Result[A]

  def decode(reader: BsonReader, decoderContext: DecoderContext): Result[A]

  final def map[B](f: A => B): BsonDecoder[B] =
    new BsonDecoder[B] {
      override def fromBsonValue(bson: BsonValue): Result[B] =
        self.fromBsonValue(bson).map(f)

      override def decode(reader: BsonReader, decoderContext: DecoderContext): Result[B] =
        self.decode(reader, decoderContext).map(f)
    }

  final def emap[B](f: A => Result[B]): BsonDecoder[B] = new BsonDecoder[B] {
    override def fromBsonValue(bson: BsonValue): Result[B] =
      self.fromBsonValue(bson) >>= f

    override def decode(reader: BsonReader, decoderContext: DecoderContext): Result[B] =
      self.decode(reader, decoderContext) >>= f
  }
}

object BsonDecoder {

  type Result[A]      = Either[Throwable, A]
  type JavaDecoder[A] = org.bson.codecs.Decoder[A]

  def apply[A](implicit ev: BsonDecoder[A]): BsonDecoder[A] = ev

  def instanceFromBsonValue[A](f: BsonValue => Either[Throwable, A]): BsonDecoder[A] = new BsonDecoder[A] {
    override def fromBsonValue(bson: BsonValue): Result[A] = f(bson)

    override def decode(reader: BsonReader, decoderContext: DecoderContext): Result[A] =
      fromBsonValue(bsonValueCodecSingleton.decode(reader, decoderContext))
  }

  def instanceFromJavaDecoder[A](javaDecoder: JavaDecoder[A]): BsonDecoder[A] =
    new BsonDecoder[A] {
      override def fromBsonValue(bson: BsonValue): Result[A] = {
        val docReader = new BsonDocumentReader(new BsonDocument("d", bson))
        docReader.readStartDocument()
        docReader.readName()
        decode(docReader, bsonDecoderContextSingleton)
      }

      override def decode(reader: BsonReader, decoderContext: DecoderContext): Result[A] = {
        val mark = reader.getMark
        Either.catchNonFatal(javaDecoder.decode(reader, decoderContext)).leftMap { ex => mark.reset(); ex }
      }
    }

  implicit val bsonDecoderInstances: Functor[BsonDecoder] = new Functor[BsonDecoder] {
    override def map[A, B](fa: BsonDecoder[A])(f: A => B): BsonDecoder[B] = fa.map(f)
  }

}
