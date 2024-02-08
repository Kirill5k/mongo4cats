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
import mongo4cats.Uuid
import mongo4cats.bson.json.{JsonMapper, Tag}
import mongo4cats.bson.{BsonValue, Document, ObjectId}
import mongo4cats.errors.MongoJsonParsingException

import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.{Base64, UUID}

private[circe] object CirceJsonMapper extends JsonMapper[Json] {

  def toBson(json: Json): BsonValue =
    json match {
      case j if j.isNull        => BsonValue.Null
      case j if j.isArray       => BsonValue.array(j.asArray.get.map(toBson))
      case j if j.isBoolean     => BsonValue.boolean(j.asBoolean.get)
      case j if j.isString      => BsonValue.string(j.asString.get)
      case j if j.isNumber      => j.asNumber.get.toBsonValue
      case j if j.isId          => BsonValue.objectId(ObjectId(jsonToObjectIdString(j).get))
      case j if j.isEpochMillis => BsonValue.instant(Instant.ofEpochMilli(j.asEpochMillis))
      case j if j.isLocalDate   => BsonValue.instant(LocalDate.parse(jsonToDateString(j).get).atStartOfDay().toInstant(ZoneOffset.UTC))
      case j if j.isDate        => BsonValue.instant(Instant.parse(jsonToDateString(j).get))
      case j if j.isBinaryArray => BsonValue.binary(Base64.getDecoder.decode(jsonToBinaryBase64(j).get))
      case j if j.isUuid        => BsonValue.uuid(jsonToUuid(json))
      case j                    => BsonValue.document(Document(j.asObject.get.toList.map { case (key, value) => key -> toBson(value) }))
    }

  implicit final private class JsonSyntax(private val json: Json) extends AnyVal {
    def isId: Boolean          = json.isObject && json.asObject.exists(_.contains(Tag.id))
    def isDate: Boolean        = json.isObject && json.asObject.exists(_.contains(Tag.date))
    def isEpochMillis: Boolean = isDate && json.asObject.exists(_(Tag.date).exists(_.isNumber))
    def isLocalDate: Boolean =
      isDate && json.asObject.exists(o => o(Tag.date).exists(_.isString) && o(Tag.date).exists(_.asString.get.length == 10))

    private def isBinary(subTypeMatch: String): Boolean = json.isObject && json.asObject.exists { o =>
      o(Tag.binary).exists(_.isObject) && o(Tag.binary).get.asObject.exists { b =>
        b("base64").exists(_.isString) && b("subType").exists(_.isString) && b("subType").get.asString.get.matches(subTypeMatch)
      }
    }

    def isBinaryArray: Boolean = isBinary("00")
    def isUuid: Boolean        = isBinary("0(3|4)")

    def asEpochMillis: Long = json.asObject.flatMap(_(Tag.date)).flatMap(_.asNumber).flatMap(_.toLong).get
  }

  implicit final private class JsonNumberSyntax(private val jNumber: JsonNumber) extends AnyVal {
    def isDecimal: Boolean = jNumber.toString.contains(".")
    def toBsonValue: BsonValue = {
      val fromIntOrLong =
        if (isDecimal) None
        else jNumber.toInt.map(BsonValue.int).orElse(jNumber.toLong.map(BsonValue.long))
      fromIntOrLong.getOrElse(
        jNumber.toBigDecimal.fold(BsonValue.double(jNumber.toDouble))(BsonValue.bigDecimal)
      )
    }
  }

  def fromBson(bson: BsonValue): Either[MongoJsonParsingException, Json] =
    bson match {
      case BsonValue.BNull            => Right(Json.Null)
      case BsonValue.BObjectId(value) => Right(objectIdToJson(value))
      case BsonValue.BDateTime(value) => Right(instantToJson(value))
      case BsonValue.BInt32(value)    => Right(Json.fromInt(value))
      case BsonValue.BInt64(value)    => Right(Json.fromLong(value))
      case BsonValue.BBoolean(value)  => Right(Json.fromBoolean(value))
      case BsonValue.BDecimal(value)  => Right(Json.fromBigDecimal(value))
      case BsonValue.BString(value)   => Right(Json.fromString(value))
      case BsonValue.BDouble(value)   => Json.fromDouble(value).toRight(MongoJsonParsingException(s"$value is not a valid double"))
      case BsonValue.BArray(value)    => value.toList.traverse(fromBson).map(Json.fromValues)
      case BsonValue.BBinary(value)   => Right(binaryArrayToJson(value))
      case BsonValue.BUuid(value)     => Right(uuidToJson(value))
      case BsonValue.BDocument(value) =>
        value.toList
          .filterNot { case (_, v) => v.isUndefined }
          .traverse { case (k, v) => fromBson(v).map(k -> _) }
          .map(Json.fromFields)
      case value => Left(MongoJsonParsingException(s"Cannot map $value bson value to json"))
    }

  def binaryBase64ToJson(base64: String, subType: String): Json =
    Json.obj(Tag.binary -> Json.obj("base64" -> Json.fromString(base64), "subType" -> Json.fromString(subType)))

  def binaryArrayToJson(binary: Array[Byte]): Json =
    binaryBase64ToJson(Base64.getEncoder.encodeToString(binary), "00")

  def uuidToJson(uuid: UUID): Json =
    binaryBase64ToJson(Uuid.toBase64(uuid), "04")

  def jsonToBinaryBase64(json: Json): Option[String] =
    json.asObject.get(Tag.binary).flatMap(_.asObject).get("base64").flatMap(_.asString)

  def jsonToUuid(json: Json): UUID =
    Uuid.fromBase64(jsonToBinaryBase64(json).get)

  def objectIdToJson(id: ObjectId): Json =
    Json.obj(Tag.id -> Json.fromString(id.toHexString))

  def jsonToObjectIdString(json: Json): Option[String] =
    json.asObject.get(Tag.id).flatMap(_.asString)

  def instantToJson(instant: Instant): Json =
    Json.obj(Tag.date -> Json.fromString(instant.toString))

  def localDateToJson(ld: LocalDate): Json =
    Json.obj(Tag.date -> Json.fromString(ld.toString))

  def jsonToDateString(json: Json): Option[String] =
    json.asObject.flatMap(_(Tag.date)).flatMap(_.asString)
}
