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

package mongo4cats.circe

import io.circe.{CursorOp, DecodingFailure, Json}
import mongo4cats.bson.{BsonError, BsonPathSegment}
import mongo4cats.bson.BsonPathSegment.{Field, Index}

private[circe] object CirceDiagnosticDecoder {
  def error(failure: DecodingFailure): BsonError = {
    val (kind, expected, actual) = failure.reason match {
      case DecodingFailure.Reason.MissingField =>
        (BsonError.Kind.MissingField, None, None)
      case DecodingFailure.Reason.WrongTypeExpectation(expectedType, value) =>
        (BsonError.Kind.TypeMismatch, Some(expectedType), Some(jsonType(value)))
      case _ =>
        (BsonError.Kind.DecoderFailure, None, None)
    }
    BsonError(kind, failure.message, path(failure.history), expected, actual, Some(failure))
  }

  private def jsonType(value: Json): String =
    value.fold("Null", _ => "Boolean", _ => "Number", _ => "String", _ => "Array", _ => "Object")

  // Cursor operations are newest first. Never parse their rendered form: field names may contain punctuation.
  private[circe] def path(history: List[CursorOp]): Vector[BsonPathSegment] =
    history.reverse
      .foldLeft(Option(Vector.empty[BsonPathSegment])) { (result, operation) =>
        result.flatMap { current =>
          operation match {
            case CursorOp.DownField(name)                                      => Some(current :+ Field(name))
            case CursorOp.DownArray                                            => Some(current :+ Index(0))
            case CursorOp.DownN(index) if index >= 0                           => Some(current :+ Index(index))
            case CursorOp.MoveUp | CursorOp.DeleteGoParent if current.nonEmpty => Some(current.init)
            case CursorOp.Field(name) => current.lastOption.collect { case Field(_) => current.init :+ Field(name) }
            case CursorOp.MoveRight   =>
              current.lastOption.collect {
                case Index(index) if index < Int.MaxValue => current.init :+ Index(index + 1)
              }
            case CursorOp.MoveLeft =>
              current.lastOption.collect {
                case Index(index) if index > 0 => current.init :+ Index(index - 1)
              }
            case _ => None
          }
        }
      }
      .getOrElse(Vector.empty)
}
