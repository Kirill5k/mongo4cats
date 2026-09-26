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

import io.circe.{Json, JsonNumber}
import io.circe.jawn.JawnParser
import mongo4cats.bson._

/** Explicit BSON Extended JSON conversion, independent of the default domain codecs. */
object CirceExtendedJson {
  private val parser                            = JawnParser(allowDuplicateKeys = false)
  implicit private val jsonTree: JsonTree[Json] = new JsonTree[Json] {
    override def fields(value: Json): Option[Vector[(String, Json)]] = value.asObject.map(_.toVector)
    override def elements(value: Json): Option[Vector[Json]]         = value.asArray
    override def string(value: Json): Option[String]                 = value.asString
    override def number(value: Json): Option[String]                 = value.asNumber.map(_.toString)
    override def boolean(value: Json): Option[Boolean]               = value.asBoolean
    override def isNull(value: Json): Boolean                        = value.isNull
    override def obj(fields: Vector[(String, Json)]): Json           = Json.fromFields(fields)
    override def arr(values: Vector[Json]): Json                     = Json.fromValues(values)
    override def str(value: String): Json                            = Json.fromString(value)
    override def num(value: String): Json                            = Json.fromJsonNumber(JsonNumber.fromDecimalStringUnsafe(value))
    override def bool(value: Boolean): Json                          = Json.fromBoolean(value)
    override def nul: Json                                           = Json.Null
  }

  /** Read either canonical or relaxed Extended JSON. */
  def toBson(json: Json): Either[BsonErrors, BsonValue] = ExtendedJsonCodec.toBson(json)

  /** Canonical output preserves supported BSON types; relaxed output can lose numeric type information. */
  def fromBson(bson: BsonValue, mode: BsonJsonMode): Either[BsonErrors, Json] = ExtendedJsonCodec.fromBson[Json](bson, mode)

  /** Parse Extended JSON, retaining the JSON parser's syntax error and available source location. */
  def parse(input: String): Either[BsonErrors, BsonValue] =
    parser
      .parse(input)
      .left
      .map { failure =>
        BsonErrors(BsonError(BsonError.Kind.SyntaxError, failure.message, cause = Some(failure)))
      }
      .flatMap(toBson)
}
