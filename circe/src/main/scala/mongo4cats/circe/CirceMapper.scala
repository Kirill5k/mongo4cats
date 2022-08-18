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

import cats.syntax.traverse._
import io.circe.{Json, JsonNumber}
import mongo4cats.bson.{BsonValue, Document, ObjectId}

import java.time.{Instant, LocalDate, ZoneOffset}

private[circe] object CirceMapper {
  private val idMarker   = "$oid"
  private val dateMarker = "$date"

  def toBson(json: Json): BsonValue =
    json match {
      case j if j.isNull        => BsonValue.Null
      case j if j.isArray       => BsonValue.array(j.asArray.get.map(toBson))
      case j if j.isBoolean     => BsonValue.boolean(j.asBoolean.get)
      case j if j.isString      => BsonValue.string(j.asString.get)
      case j if j.isNumber      => j.asNumber.get.toBsonValue
      case j if j.isId          => BsonValue.objectId(ObjectId(j.asObject.get(idMarker).flatMap(_.asString).get))
      case j if j.isEpochMillis => BsonValue.instant(Instant.ofEpochMilli(j.asEpochMillis))
      case j if j.isLocalDate   => BsonValue.instant(LocalDate.parse(j.asIsoDateString).atStartOfDay().toInstant(ZoneOffset.UTC))
      case j if j.isDate        => BsonValue.instant(Instant.parse(j.asIsoDateString))
      case j                    => BsonValue.document(Document(j.asObject.get.toList.map { case (key, value) => key -> toBson(value) }))
    }

  implicit final private class JsonSyntax(private val json: Json) extends AnyVal {
    def isId: Boolean          = json.isObject && json.asObject.exists(_.contains(idMarker))
    def isDate: Boolean        = json.isObject && json.asObject.exists(_.contains(dateMarker))
    def isEpochMillis: Boolean = isDate && json.asObject.exists(_(dateMarker).exists(_.isNumber))
    def isLocalDate: Boolean =
      isDate && json.asObject.exists(o => o(dateMarker).exists(_.isString) && o(dateMarker).exists(_.asString.get.length == 10))

    def asEpochMillis: Long     = json.asObject.flatMap(_(dateMarker)).flatMap(_.asNumber).flatMap(_.toLong).get
    def asIsoDateString: String = json.asObject.flatMap(_(dateMarker)).flatMap(_.asString).get
  }

  implicit final private class JsonNumberSyntax(private val jNumber: JsonNumber) extends AnyVal {
    def isDecimal: Boolean = jNumber.toString.contains(".")
    def toBsonValue: BsonValue =
      if (isDecimal) jNumber.toBigDecimal.map(BsonValue.bigDecimal).getOrElse(BsonValue.double(jNumber.toDouble))
      else jNumber.toInt.map(BsonValue.int).orElse(jNumber.toLong.map(BsonValue.long)).get

  }

  def fromBson(bson: BsonValue): Either[MongoJsonParsingException, Json] =
    bson match {
      case BsonValue.BNull            => Right(Json.Null)
      case BsonValue.BObjectId(value) => Right(Json.obj(idMarker -> Json.fromString(value.toHexString)))
      case BsonValue.BDateTime(value) => Right(Json.obj(dateMarker -> Json.fromString(value.toString)))
      case BsonValue.BInt32(value)    => Right(Json.fromInt(value))
      case BsonValue.BInt64(value)    => Right(Json.fromLong(value))
      case BsonValue.BBoolean(value)  => Right(Json.fromBoolean(value))
      case BsonValue.BDecimal(value)  => Right(Json.fromBigDecimal(value))
      case BsonValue.BString(value)   => Right(Json.fromString(value))
      case BsonValue.BDouble(value)   => Json.fromDouble(value).toRight(MongoJsonParsingException(s"$value is not a valid double"))
      case BsonValue.BArray(value)    => value.toList.traverse(fromBson).map(Json.fromValues)
      case BsonValue.BDocument(value) => value.toList.traverse { case (k, v) => fromBson(v).map(k -> _) }.map(Json.fromFields)
      case value                      => Left(MongoJsonParsingException(s"Cannot map $value bson value to json"))
    }
}
