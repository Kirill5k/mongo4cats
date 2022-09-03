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
import org.bson.{AbstractBsonReader, BsonDocument, BsonDocumentReader, BsonReader, BsonValue}

import scala.util.Try

/** A type class that provides a way to produce a value of type `A` from a [[org.bson.BsonValue]] value. */
trait BsonDecoder[A] { self =>

  def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A

  /** Decode the given [[org.bson.BsonValue]]. */
  def unsafeFromBsonValue(bson: BsonValue): A = {
    val docReader = new BsonDocumentReader(new BsonDocument("d", bson))
    docReader.readStartDocument()
    docReader.readName()
    unsafeDecode(docReader, bsonDecoderContextSingleton)
  }

  final def map[B](f: A => B): BsonDecoder[B] =
    new BsonDecoder[B] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): B =
        f(self.unsafeDecode(reader, decoderContext))

      override def unsafeFromBsonValue(bson: BsonValue): B =
        f(self.unsafeFromBsonValue(bson))
    }

  final def emap[B](f: A => Result[B]): BsonDecoder[B] =
    new BsonDecoder[B] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): B = {
        val mark = reader.getMark
        f(self.unsafeDecode(reader, decoderContext)).getOrElse {
          mark.reset()
          val bson = bsonValueCodecSingleton.decode(reader, decoderContext)
          mark.reset()
          throw new Throwable(s"Can't decode via `emap()`: ${bson}")
        }
      }

      override def unsafeFromBsonValue(bson: BsonValue): B =
        f(self.unsafeFromBsonValue(bson)).getOrElse(throw new Throwable(s"Can't decode via `emap()`: ${bson}"))
    }

  final def emapTry[B](f: A => Try[B]): BsonDecoder[B] =
    new BsonDecoder[B] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): B = {
        val mark = reader.getMark
        f(self.unsafeDecode(reader, decoderContext)).getOrElse {
          mark.reset()
          val bson = bsonValueCodecSingleton.decode(reader, decoderContext)
          mark.reset()
          throw new Throwable(s"Can't decode via `emapTry()`: ${bson}")
        }
      }

      override def unsafeFromBsonValue(bson: BsonValue): B =
        f(self.unsafeFromBsonValue(bson)).getOrElse(throw new Throwable(s"Can't decode via `emap()`: ${bson}"))
    }

  final def flatMap[B](f: A => BsonDecoder[B]): BsonDecoder[B] =
    new BsonDecoder[B] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): B = {
        val a = self.unsafeDecode(reader, decoderContext)
        f(a).unsafeDecode(reader, decoderContext)
      }
    }

  final def handleErrorWith(f: Throwable => BsonDecoder[A]): BsonDecoder[A] =
    new BsonDecoder[A] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A = {
        val mark = reader.getMark
        try self.unsafeDecode(reader, decoderContext)
        catch {
          case ex: Throwable =>
            mark.reset()
            f(ex).unsafeDecode(reader, decoderContext)
        }
      }
    }

  final def withErrorMessage(message: String): BsonDecoder[A] =
    new BsonDecoder[A] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A =
        try self.unsafeDecode(reader, decoderContext)
        catch { case ex: Throwable => throw WithErrorMessage(message, ex) }
    }

  final def ensure(pred: A => Boolean, message: => String): BsonDecoder[A] =
    new BsonDecoder[A] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A = {
        val a = self.unsafeDecode(reader, decoderContext)
        if (pred(a)) a else throw new Throwable(message)
      }
    }

  final def product[B](fb: BsonDecoder[B]): BsonDecoder[(A, B)] =
    new BsonDecoder[(A, B)] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): (A, B) = {
        val mark = reader.getMark
        val a    = self.unsafeDecode(reader, decoderContext)
        mark.reset()
        val b = fb.unsafeDecode(reader, decoderContext)
        (a, b)
      }
    }

  final def or[AA >: A](d: => BsonDecoder[AA]): BsonDecoder[AA] =
    new BsonDecoder[AA] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): AA = {
        val mark = reader.getMark
        try self.unsafeDecode(reader, decoderContext)
        catch {
          case ex: Throwable =>
            mark.reset()
            d.unsafeDecode(reader, decoderContext)
        }
      }
    }

  final def either[B](decodeB: BsonDecoder[B]): BsonDecoder[Either[A, B]] =
    new BsonDecoder[Either[A, B]] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): Either[A, B] = {
        val mark = reader.getMark
        try Left(self.unsafeDecode(reader, decoderContext))
        catch {
          case ex: Throwable =>
            mark.reset()
            Right(decodeB.unsafeDecode(reader, decoderContext))
        }
      }
    }

  final def prepare(f: BsonReader => BsonReader): BsonDecoder[A] =
    new BsonDecoder[A] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A = ???
    }

  final def at(field: String): BsonDecoder[A] =
    new BsonDecoder[A] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A = {
        var name: String = null
        while ({
          name = reader.readName()
          name =!= field
        }) reader.skipValue()
        self.unsafeDecode(reader, decoderContext)
      }
    }

}

object BsonDecoder {

  type Result[A]      = Either[Throwable, A]
  type JavaDecoder[A] = org.bson.codecs.Decoder[A]

  def apply[A](implicit ev: BsonDecoder[A]): BsonDecoder[A] = ev

  final def const[A](a: A): BsonDecoder[A] =
    new BsonDecoder[A] {
      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A = {
        reader.skipValue()
        a
      }
    }

  def safeDecode[A](bsonValue: BsonValue)(implicit decA: BsonDecoder[A]): Result[A] =
    Either.catchNonFatal(decA.unsafeFromBsonValue(bsonValue))

  def safeDecode[A](bsonReader: BsonReader)(implicit decA: BsonDecoder[A]): Result[A] =
    Either.catchNonFatal(unsafeDecode(bsonReader))

  def unsafeDecode[A](bsonReader: BsonReader)(implicit decA: BsonDecoder[A]): A =
    decA.unsafeDecode(bsonReader.asInstanceOf[AbstractBsonReader], bsonDecoderContextSingleton)

  def slowInstance[A](f: BsonValue => A): BsonDecoder[A] = new BsonDecoder[A] {
    override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A =
      unsafeFromBsonValue(bsonValueCodecSingleton.decode(reader, decoderContext))

    override def unsafeFromBsonValue(bson: BsonValue): A =
      f(bson)
  }

  def fastInstance[A](javaDecoder: JavaDecoder[A]): BsonDecoder[A] =
    new BsonDecoder[A] {

      override def unsafeDecode(reader: AbstractBsonReader, decoderContext: DecoderContext): A = {
        val mark = reader.getMark
        try javaDecoder.decode(reader, decoderContext)
        catch {
          case ex: Throwable =>
            mark.reset()
            throw ex
        }
      }
    }

  def fastInstance[A](f: BsonReader => A): BsonDecoder[A] =
    fastInstance(new JavaDecoder[A] {
      override def decode(reader: BsonReader, decoderContext: DecoderContext): A = f(reader)
    })

  implicit val bsonDecoderInstances: Functor[BsonDecoder] = new Functor[BsonDecoder] {
    override def map[A, B](fa: BsonDecoder[A])(f: A => B): BsonDecoder[B] = fa.map(f)
  }

  def debug(reader: BsonReader, prefix: String = "Debug BsonValue"): Unit = {
    val mark      = reader.getMark
    val bsonValue = bsonValueCodecSingleton.decode(reader, bsonDecoderContextSingleton)
    println(s"$prefix: ${bsonValue}")
    mark.reset()
  }
}

final case class WithErrorMessage(message: String, cause: Throwable) extends Throwable(message, cause)
