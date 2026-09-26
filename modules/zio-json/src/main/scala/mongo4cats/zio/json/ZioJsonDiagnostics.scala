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

package mongo4cats.zio.json

import mongo4cats.bson._
import zio.json.{JsonDecoder, JsonError}
import zio.json.ast.Json
import zio.json.internal.RetractReader

import scala.util.control.NonFatal

private[json] object ZioJsonDiagnostics {
  // ZIO's decodeJson permits a valid prefix. This isolated low-level adapter
  // requires the remaining input to contain only JSON whitespace.
  private val completeJsonDecoder: JsonDecoder[Json] = new JsonDecoder[Json] {
    def unsafeDecode(trace: List[JsonError], in: RetractReader): Json = {
      val value = Json.decoder.unsafeDecode(trace, in)
      var next  = in.read()
      while (next == ' ' || next == '\n' || next == '\r' || next == '\t') next = in.read()
      if (next != -1) throw JsonDecoder.UnsafeJson(JsonError.Message("Unexpected trailing input") :: trace)
      value
    }
  }

  def parse(json: String): Either[BsonErrors, Json] =
    // ZIO's numeric lexer retracts its lookahead even at EOF. A whitespace
    // sentinel gives it a real delimiter, so the final digit is not replayed.
    completeJsonDecoder.decodeJson(json + " ").left.map(message => BsonErrors(BsonError(BsonError.Kind.SyntaxError, message)))

  def decode[A](bson: BsonValue, decoder: JsonDecoder[A]): Either[BsonErrors, A] =
    fromBson(bson).flatMap(json => decodeJson(json, decoder))

  // The safe ZIO entry point renders this trace into a String. Keep the typed trace
  // here; a native ZIO decoder reports one failure, even for a derived product.
  private def decodeJson[A](json: Json, decoder: JsonDecoder[A]): Either[BsonErrors, A] =
    try Right(decoder.unsafeFromJsonAST(Nil, json))
    catch {
      case error: JsonDecoder.UnsafeJson =>
        val path = error.trace.reverse.collect {
          case JsonError.ObjectAccess(name) => BsonPathSegment.Field(name)
          case JsonError.ArrayAccess(index) => BsonPathSegment.Index(index)
        }.toVector
        Left(BsonErrors(BsonError(BsonError.Kind.DecoderFailure, JsonError.render(error.trace), path, cause = Some(error))))
      case NonFatal(error) => Left(BsonErrors(failure(BsonError.Kind.DecoderFailure, error)))
    }

  private def fromBson(bson: BsonValue): Either[BsonErrors, Json] = bson match {
    case BsonValue.BArray(values) =>
      collect(values.toVector.zipWithIndex.map { case (value, index) =>
        fromBson(value).left.map(_.prepend(BsonPathSegment.Index(index)))
      }).map(values => Json.Arr(values: _*))
    case BsonValue.BDocument(document) =>
      collect(document.toList.toVector.filterNot(_._2.isUndefined).map { case (name, value) =>
        fromBson(value).left.map(_.prepend(BsonPathSegment.Field(name))).map(name -> _)
      }).map(fields => Json.Obj(fields: _*))
    case _ =>
      try ZioJsonMapper.fromBson(bson).left.map(error => BsonErrors(failure(BsonError.Kind.UnsupportedType, error)))
      catch {
        case NonFatal(error) => Left(BsonErrors(failure(BsonError.Kind.InvalidValue, error)))
      }
  }

  private def failure(kind: BsonError.Kind, error: Throwable): BsonError =
    BsonError(kind, Option(error.getMessage).getOrElse(error.getClass.getSimpleName), cause = Some(error))

  private def collect[A](values: Vector[Either[BsonErrors, A]]): Either[BsonErrors, Vector[A]] =
    values.foldLeft[Either[BsonErrors, Vector[A]]](Right(Vector.empty)) {
      case (Right(result), Right(value)) => Right(result :+ value)
      case (Left(errors), Left(more))    => Left(errors ++ more)
      case (Left(errors), _)             => Left(errors)
      case (_, Left(errors))             => Left(errors)
    }
}
