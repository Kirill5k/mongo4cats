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
import zio.json.ast.Json

/** Explicit Extended JSON conversion, independent of the default domain codecs. */
object ZioExtendedJson {
  implicit private val tree: JsonTree[Json] = new JsonTree[Json] {
    override def preservesNegativeZero: Boolean = false

    def fields(value: Json): Option[Vector[(String, Json)]] = value.asObject.map(_.fields.toVector)
    def elements(value: Json): Option[Vector[Json]]         = value.asArray.map(_.toVector)
    def string(value: Json): Option[String]                 = value.asString
    def number(value: Json): Option[String]                 = value.asNumber.map(_.value.toString)
    def boolean(value: Json): Option[Boolean]               = value.asBoolean
    def isNull(value: Json): Boolean                        = value.asNull.nonEmpty
    def obj(fields: Vector[(String, Json)]): Json           = Json.Obj(fields: _*)
    def arr(elements: Vector[Json]): Json                   = Json.Arr(elements: _*)
    def str(value: String): Json                            = Json.Str(value)
    def num(value: String): Json                            = Json.Num(new java.math.BigDecimal(value))
    def bool(value: Boolean): Json                          = Json.Bool(value)
    def nul: Json                                           = Json.Null
  }

  def toBson(json: Json): Either[BsonErrors, BsonValue] = ExtendedJsonCodec.toBson(json)

  def fromBson(bson: BsonValue, mode: BsonJsonMode): Either[BsonErrors, Json] =
    ExtendedJsonCodec.fromBson[Json](bson, mode)

  def parse(json: String): Either[BsonErrors, BsonValue] =
    ZioJsonDiagnostics.parse(json).flatMap(value => ZioJsonLexical.toBson(json, value))
}
