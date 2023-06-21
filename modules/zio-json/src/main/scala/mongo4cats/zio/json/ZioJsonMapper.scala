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

import mongo4cats.Uuid
import mongo4cats.bson.json.{JsonMapper, Tag}
import mongo4cats.bson.{BsonValue, Document, ObjectId}
import mongo4cats.errors.MongoJsonParsingException
import zio.json.ast.Json

import java.time.{Instant, LocalDate, ZoneOffset}
import java.util.{Base64, UUID}
import scala.math.BigDecimal._

private[json] object ZioJsonMapper extends JsonMapper[Json] {

  def toBson(json: Json): BsonValue =
    json match {
      case j if j.isNull        => BsonValue.Null
      case j if j.isArray       => BsonValue.array(j.asArray.get.map(toBson))
      case j if j.isBoolean     => BsonValue.boolean(j.asBoolean.get)
      case j if j.isString      => BsonValue.string(j.asString.get)
      case j if j.isNumber      => j.asNumber.get.toBsonValue
      case j if j.isId          => BsonValue.objectId(ObjectId(jsonToObjectIdString(json).get))
      case j if j.isEpochMillis => BsonValue.instant(Instant.ofEpochMilli(j.asEpochMillis))
      case j if j.isLocalDate   => BsonValue.instant(LocalDate.parse(jsonToDateString(j).get).atStartOfDay().toInstant(ZoneOffset.UTC))
      case j if j.isDate        => BsonValue.instant(Instant.parse(jsonToDateString(j).get))
      case j if j.isBinaryArray => BsonValue.binary(Base64.getDecoder.decode(jsonToBinaryBase64(j).get))
      case j if j.isUuid        => BsonValue.uuid(jsonToUuid(j))
      case j => BsonValue.document(Document(j.asObject.get.fields.toList.map { case (key, value) => key -> toBson(value) }))
    }

  implicit final private class JsonSyntax(private val json: Json) extends AnyVal {
    def isNull: Boolean        = json.asNull.nonEmpty
    def isArray: Boolean       = json.asArray.nonEmpty
    def isBoolean: Boolean     = json.asBoolean.nonEmpty
    def isString: Boolean      = json.asString.nonEmpty
    def isNumber: Boolean      = json.asNumber.nonEmpty
    def isId: Boolean          = json.asObject.nonEmpty && json.asObject.exists(_.contains(Tag.id))
    def isDate: Boolean        = json.asObject.nonEmpty && json.asObject.exists(_.contains(Tag.date))
    def isEpochMillis: Boolean = isDate && json.asObject.exists(_.get(Tag.date).exists(_.isNumber))
    def isLocalDate: Boolean =
      isDate && json.asObject.exists(o => o.get(Tag.date).exists(_.isString) && o.get(Tag.date).exists(_.asString.get.length == 10))

    private def isBinary(subTypeMatch: String): Boolean = json.asObject.nonEmpty && json.asObject.exists { o =>
      o.asObject.exists(_.contains(Tag.binary)) && o.asObject.get.get(Tag.binary).get.asObject.exists { b =>
        b.contains("base64") && b.contains("subType") && b.get("subType").exists(st => st.isString && st.asString.get.matches(subTypeMatch))
      }
    }

    def isBinaryArray: Boolean = isBinary("00")
    def isUuid: Boolean        = isBinary("0(3|4)")

    def asEpochMillis: Long =
      (for {
        obj  <- json.asObject
        date <- obj.get(Tag.date)
        num  <- date.asNumber
        ts = num.value.toLong
      } yield ts).get
  }

  implicit final private class JsonNumSyntax(private val jNumber: Json.Num) extends AnyVal {
    def isDecimal: Boolean = jNumber.toString.contains(".")

    def toBsonValue: BsonValue =
      (isDecimal, jNumber.value) match {
        case (true, n)                   => BsonValue.bigDecimal(n)
        case (false, n) if n.isValidInt  => BsonValue.int(n.toInt)
        case (false, n) if n.isValidLong => BsonValue.long(n.toLong)
        case (_, n)                      => BsonValue.bigDecimal(n)
      }
  }

  def fromBson(bson: BsonValue): Either[MongoJsonParsingException, Json] = {
    def rightEmptyList[A]: Either[MongoJsonParsingException, List[A]] = Right(List.empty[A])

    bson match {
      case BsonValue.BNull            => Right(Json.Null)
      case BsonValue.BObjectId(value) => Right(objectIdToJson(value))
      case BsonValue.BDateTime(value) => Right(instantToJson(value))
      case BsonValue.BInt32(value)    => Right(Json.Num(value))
      case BsonValue.BInt64(value)    => Right(Json.Num(value))
      case BsonValue.BBoolean(value)  => Right(Json.Bool(value))
      case BsonValue.BDecimal(value)  => Right(Json.Num(value))
      case BsonValue.BString(value)   => Right(Json.Str(value))
      case BsonValue.BDouble(value)   => Right(Json.Num(value))
      case BsonValue.BUuid(value)     => Right(uuidToJson(value))
      case BsonValue.BBinary(value)   => Right(binaryArrayToJson(value))
      case BsonValue.BArray(value) =>
        value.toList
          .foldRight(rightEmptyList[Json]) { case (a, acc) => (fromBson(a), acc).mapN(_ :: _) }
          .map(xs => Json.Arr(xs: _*))
      case BsonValue.BDocument(value) =>
        value.toList
          .filterNot { case (_, v) => v.isUndefined }
          .foldRight(rightEmptyList[(String, Json)]) { case (a, acc) => (fromBson(a._2), acc).mapN((x, xs) => (a._1, x) :: xs) }
          .map(xs => Json.Obj(xs: _*))
      case value => Left(MongoJsonParsingException(s"Cannot map $value bson value to json"))
    }
  }

  implicit final private class EitherSyntax[A, B](
      private val eitherTuple: (Either[MongoJsonParsingException, A], Either[MongoJsonParsingException, B])
  ) extends AnyVal {
    def mapN[C](f: (A, B) => C): Either[MongoJsonParsingException, C] =
      for {
        v1 <- eitherTuple._1
        v2 <- eitherTuple._2
      } yield f(v1, v2)
  }

  def binaryBase64ToJson(base64: String, subType: String): Json =
    Json.Obj(Tag.binary -> Json.Obj("base64" -> Json.Str(base64), "subType" -> Json.Str(subType)))

  def uuidToJson(uuid: UUID): Json =
    binaryBase64ToJson(Uuid.toBase64(uuid), "04")

  def binaryArrayToJson(binary: Array[Byte]): Json =
    binaryBase64ToJson(Base64.getEncoder.encodeToString(binary), "00")

  def jsonToBinaryBase64(json: Json): Option[String] =
    json.asObject.flatMap(_.get(Tag.binary)).flatMap(_.asObject).flatMap(_.get("base64")).flatMap(_.asString)

  def jsonToUuid(json: Json): UUID =
    Uuid.fromBase64(jsonToBinaryBase64(json).get)

  def objectIdToJson(id: ObjectId): Json =
    Json.Obj(Tag.id -> Json.Str(id.toHexString))

  def jsonToObjectIdString(json: Json): Option[String] =
    for {
      obj <- json.asObject
      id  <- obj.get(Tag.id)
      hex <- id.asString
    } yield hex

  def instantToJson(instant: Instant): Json =
    Json.Obj(Tag.date -> Json.Str(instant.toString))

  def localDateToJson(ld: LocalDate): Json =
    Json.Obj(Tag.date -> Json.Str(ld.toString))

  def jsonToDateString(json: Json): Option[String] =
    for {
      obj  <- json.asObject
      date <- obj.get(Tag.date)
      str  <- date.asString
    } yield str

}
