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

sealed trait BsonPathSegment extends Product with Serializable

object BsonPathSegment {
  final case class Field(name: String) extends BsonPathSegment
  final case class Index(index: Int)   extends BsonPathSegment
}

/** A decoding or conversion failure whose path is relative to the input value. */
final case class BsonError(
    kind: BsonError.Kind,
    message: String,
    path: Vector[BsonPathSegment] = Vector.empty,
    expected: Option[String] = None,
    actual: Option[String] = None,
    cause: Option[Throwable] = None
) {
  def prepend(segment: BsonPathSegment): BsonError = copy(path = segment +: path)

  def renderPath: String = path.foldLeft("$") {
    case (result, BsonPathSegment.Field(name)) if name.matches("[A-Za-z_][A-Za-z0-9_]*") => result + "." + name
    case (result, BsonPathSegment.Field(name))                                           => result + "[\"" + BsonError.escape(name) + "\"]"
    case (result, BsonPathSegment.Index(index))                                          => result + "[" + index.toString + "]"
  }

  def render: String = message + " at " + renderPath
}

object BsonError {
  sealed trait Kind extends Product with Serializable
  object Kind {
    case object MissingField    extends Kind
    case object TypeMismatch    extends Kind
    case object InvalidValue    extends Kind
    case object UnsupportedType extends Kind
    case object DecoderFailure  extends Kind
    case object SyntaxError     extends Kind
  }

  private[bson] def escape(value: String): String = value.flatMap {
    case '"'                => "\\\""
    case '\\'               => "\\\\"
    case char if char < ' ' => f"\\u${char.toInt}%04x"
    case char               => char.toString
  }

  private[mongo4cats] def typeName(value: BsonValue): String = value match {
    case BsonValue.BNull         => "Null"
    case BsonValue.BUndefined    => "Undefined"
    case BsonValue.BMaxKey       => "MaxKey"
    case BsonValue.BMinKey       => "MinKey"
    case _: BsonValue.BInt32     => "Int32"
    case _: BsonValue.BInt64     => "Int64"
    case _: BsonValue.BDouble    => "Double"
    case _: BsonValue.BTimestamp => "Timestamp"
    case _: BsonValue.BDateTime  => "DateTime"
    case _: BsonValue.BBinary    => "Binary"
    case _: BsonValue.BBoolean   => "Boolean"
    case _: BsonValue.BDecimal   => "Decimal128"
    case _: BsonValue.BString    => "String"
    case _: BsonValue.BObjectId  => "ObjectId"
    case _: BsonValue.BDocument  => "Document"
    case _: BsonValue.BArray     => "Array"
    case _: BsonValue.BRegex     => "RegularExpression"
    case _: BsonValue.BUuid      => "UUID"
  }

  private[mongo4cats] def typeMismatch(expected: String, value: BsonValue): BsonErrors = {
    val actual = typeName(value)
    BsonErrors(BsonError(Kind.TypeMismatch, s"Expected $expected but found $actual", expected = Some(expected), actual = Some(actual)))
  }
}

/** Nonempty, ordered decoding failures. Also throwable for APIs whose return type is not Either. */
final case class BsonErrors(head: BsonError, tail: Vector[BsonError] = Vector.empty)
    extends RuntimeException((head +: tail).map(_.render).mkString("; "), head.cause.orNull) {
  def errors: Vector[BsonError] = head +: tail

  def prepend(segment: BsonPathSegment): BsonErrors = BsonErrors(head.prepend(segment), tail.map(_.prepend(segment)))

  def ++(other: BsonErrors): BsonErrors = BsonErrors(head, tail ++ other.errors)
}
