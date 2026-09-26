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

package mongo4cats.bson

import java.time.Instant
import java.util.UUID
import scala.util.control.NonFatal

trait BsonValueDecoder[A] {
  self =>

  def decode(bsonValue: BsonValue): Either[BsonErrors, A]

  def map[B](f: A => B): BsonValueDecoder[B] = BsonValueDecoder.fromEither(value => self.decode(value).map(f))

  /** Decode both branches against the same input and collect their independent failures. */
  def zip[B](other: BsonValueDecoder[B]): BsonValueDecoder[(A, B)] = BsonValueDecoder.fromEither { value =>
    val left  = BsonValueDecoder.attempt(self.decode(value))
    val right = BsonValueDecoder.attempt(other.decode(value))
    (left, right) match {
      case (Right(a), Right(b)) => Right((a, b))
      case (Left(a), Left(b))   =>
        // Field decoders share the same parent container. Report its type failure once.
        val additional = b.errors.filterNot(error =>
          error.kind == BsonError.Kind.TypeMismatch && error.expected.contains("Document") && a.errors.contains(error)
        )
        Left(BsonErrors(a.head, a.tail ++ additional))
      case (Left(errors), _) => Left(errors)
      case (_, Left(errors)) => Left(errors)
    }
  }

  def field(name: String): BsonValueDecoder[A] = BsonValueDecoder.field(name)(self)
}

object BsonValueDecoder {
  private[bson] def attempt[A](result: => Either[BsonErrors, A]): Either[BsonErrors, A] =
    try result
    catch {
      case errors: BsonErrors => Left(errors)
      case NonFatal(error)    =>
        Left(
          BsonErrors(
            BsonError(BsonError.Kind.DecoderFailure, Option(error.getMessage).getOrElse(error.getClass.getName), cause = Some(error))
          )
        )
    }

  private def builtin[A](expected: String)(f: BsonValue => Option[A]): BsonValueDecoder[A] =
    fromEither(value => f(value).toRight(BsonError.typeMismatch(expected, value)))

  /** Construct a decoder that captures nonfatal exceptions as structured failures. */
  def fromEither[A](f: BsonValue => Either[BsonErrors, A]): BsonValueDecoder[A] = value => attempt(f(value))

  def field[A](name: String)(implicit decoder: BsonValueDecoder[A]): BsonValueDecoder[A] = fromEither { value =>
    value.asDocument match {
      case Some(document) => document.getAsEither[A](name)
      case None           => Left(BsonError.typeMismatch("Document", value))
    }
  }

  implicit val objectIdDecoder: BsonValueDecoder[ObjectId]     = builtin("ObjectId")(_.asObjectId)
  implicit val intDecoder: BsonValueDecoder[Int]               = builtin("Int32")(_.asInt)
  implicit val longDecoder: BsonValueDecoder[Long]             = builtin("Int64 or Timestamp")(_.asLong)
  implicit val stringDecoder: BsonValueDecoder[String]         = builtin("String")(_.asString)
  implicit val dateTimeDecoder: BsonValueDecoder[Instant]      = builtin("DateTime or Timestamp")(_.asInstant)
  implicit val doubleDecoder: BsonValueDecoder[Double]         = builtin("Double")(_.asDouble)
  implicit val booleanDecoder: BsonValueDecoder[Boolean]       = builtin("Boolean")(_.asBoolean)
  implicit val documentDecoder: BsonValueDecoder[Document]     = builtin("Document")(_.asDocument)
  implicit val bigDecimalDecoder: BsonValueDecoder[BigDecimal] = builtin("Decimal128")(_.asBigDecimal)
  implicit val bigIntDecoder: BsonValueDecoder[BigInt]         = builtin("Decimal128")(_.asBigDecimal.map(_.toBigInt))
  implicit val uuidDecoder: BsonValueDecoder[UUID]             = builtin("UUID")(_.asUuid)

  implicit def arrayListDecoder[A](implicit d: BsonValueDecoder[A]): BsonValueDecoder[List[A]] = fromEither { value =>
    value.asList match {
      case None         => Left(BsonError.typeMismatch("Array", value))
      case Some(values) =>
        val results = List.newBuilder[A]
        val errors  = Vector.newBuilder[BsonError]
        values.zipWithIndex.foreach { case (element, index) =>
          attempt(d.decode(element)) match {
            case Right(result)  => results += result
            case Left(failures) => errors ++= failures.prepend(BsonPathSegment.Index(index)).errors
          }
        }
        val failures = errors.result()
        failures.headOption match {
          case Some(head) => Left(BsonErrors(head, failures.tail))
          case None       => Right(results.result())
        }
    }
  }
}
