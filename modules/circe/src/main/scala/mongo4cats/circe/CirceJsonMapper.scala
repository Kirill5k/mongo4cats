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
import mongo4cats.bson.json.{ExtendedJson, JsonMapper, Tag}
import mongo4cats.bson.{BsonError, BsonErrors, BsonPathSegment, BsonValue, Document, ObjectId}
import mongo4cats.errors.MongoJsonParsingException

import java.time.{Instant, LocalDate}
import java.util.{Base64, UUID}
import scala.util.control.NonFatal

private[circe] object CirceJsonMapper extends JsonMapper[Json] {

  def toBson(json: Json): BsonValue =
    json match {
      case j if j.isNull           => BsonValue.Null
      case j if j.isArray          => BsonValue.array(j.asArray.get.map(toBson))
      case j if j.isBoolean        => BsonValue.boolean(j.asBoolean.get)
      case j if j.isString         => BsonValue.string(j.asString.get)
      case j if j.isNumber         => j.asNumber.get.toBsonValue
      case j if j.hasTag(Tag.date) =>
        parseExtendedJson(Tag.date, j) {
          val value   = wrapperValue(j, Tag.date)
          val instant = value.asString match {
            case Some(date)             => ExtendedJson.parseDateString(date)
            case None if value.isNumber =>
              val millis = value.asNumber
                .flatMap(_.toBigDecimal)
                .filter(n => n.isWhole && n.isValidLong)
                .getOrElse(throw MongoJsonParsingException("$date must contain exact signed 64-bit epoch milliseconds"))
              Instant.ofEpochMilli(millis.toLong)
            case None =>
              val millis = wrapperValue(value, Tag.numberLong).asString
                .getOrElse(throw MongoJsonParsingException("Canonical $date must contain a $numberLong string"))
              ExtendedJson.parseDateMillis(millis)
          }
          BsonValue.instant(instant)
        }
      case j if j.hasTag(Tag.numberDecimal) =>
        parseExtendedJson(Tag.numberDecimal, j) {
          val decimal = wrapperValue(j, Tag.numberDecimal).asString
            .getOrElse(throw MongoJsonParsingException("$numberDecimal must contain a string"))
          BsonValue.bigDecimal(ExtendedJson.parseDecimal(decimal))
        }
      case j if j.isId          => BsonValue.objectId(ObjectId(jsonToObjectIdString(j).get))
      case j if j.isUuid        => BsonValue.uuid(jsonToUuid(j))
      case j if j.isBinaryArray => BsonValue.binary(Base64.getDecoder.decode(jsonToBinaryBase64(j).get), jsonToBinarySubtype(j).get)
      case j                    => BsonValue.document(Document(j.asObject.get.toList.map { case (key, value) => key -> toBson(value) }))
    }

  implicit final private class JsonSyntax(private val json: Json) extends AnyVal {
    def hasTag(tag: String): Boolean = json.asObject.exists(_.contains(tag))
    def isId: Boolean                = hasTag(Tag.id)

    private def isBinary(subTypeMatch: String): Boolean = json.isObject && json.asObject.exists { o =>
      o(Tag.binary).exists(_.isObject) && o(Tag.binary).get.asObject.exists { b =>
        b("base64").exists(_.isString) && b("subType").exists(_.isString) && b("subType").get.asString.get.matches(subTypeMatch)
      }
    }

    def isBinaryArray: Boolean = isBinary("[0-9a-fA-F]{2}")
    def isUuid: Boolean        = isBinary("04")
  }

  private def wrapperValue(json: Json, tag: String): Json =
    json.asObject
      .filter(_.size == 1)
      .flatMap(_(tag))
      .getOrElse(throw MongoJsonParsingException(s"Extended JSON $tag must be an object containing only $tag"))

  private def parseExtendedJson(tag: String, json: Json)(parse: => BsonValue): BsonValue =
    try parse
    catch {
      case error: MongoJsonParsingException => throw error
      case NonFatal(error) => throw MongoJsonParsingException(s"Invalid $tag value: ${error.getMessage}", Some(json.noSpaces))
    }

  implicit final private class JsonNumberSyntax(private val jNumber: JsonNumber) extends AnyVal {
    def toBsonValue: BsonValue =
      jNumber.getClass.getName match {
        case "io.circe.JsonDouble" | "io.circe.JsonFloat" => BsonValue.double(jNumber.toDouble)
        case "io.circe.JsonLong"                          => jNumber.toInt.map(BsonValue.int).orElse(jNumber.toLong.map(BsonValue.long)).get
        case _                                            => BsonValue.bigDecimal(jNumber.toBigDecimal.get)
      }
  }

  def fromBson(bson: BsonValue): Either[MongoJsonParsingException, Json] =
    bson match {
      case BsonValue.BNull                   => Right(Json.Null)
      case BsonValue.BObjectId(value)        => Right(objectIdToJson(value))
      case BsonValue.BDateTime(value)        => Right(instantToJson(value))
      case BsonValue.BInt32(value)           => Right(Json.fromInt(value))
      case BsonValue.BInt64(value)           => Right(Json.fromLong(value))
      case BsonValue.BBoolean(value)         => Right(Json.fromBoolean(value))
      case BsonValue.BDecimal(value)         => Right(Json.fromBigDecimal(value))
      case BsonValue.BString(value)          => Right(Json.fromString(value))
      case BsonValue.BDouble(value)          => Json.fromDouble(value).toRight(MongoJsonParsingException(s"$value is not a valid double"))
      case BsonValue.BArray(value)           => value.toList.traverse(fromBson).map(Json.fromValues)
      case BsonValue.BBinary(value, subtype) => Right(binaryArrayToJson(value, subtype))
      case BsonValue.BUuid(value)            => Right(uuidToJson(value))
      case BsonValue.BDocument(value)        =>
        value.toList
          .filterNot { case (_, v) => v.isUndefined }
          .traverse { case (k, v) => fromBson(v).map(k -> _) }
          .map(Json.fromFields)
      case value => Left(MongoJsonParsingException(s"Cannot map $value bson value to json"))
    }

  // Retain the historical JSON representation while collecting mapping failures before domain decoding.
  def fromBsonDiagnostic(bson: BsonValue): Either[BsonErrors, Json] = bson match {
    case BsonValue.BArray(values) =>
      accumulate(values.toVector.zipWithIndex) { case (value, index) =>
        fromBsonDiagnostic(value).left.map(_.prepend(BsonPathSegment.Index(index)))
      }.map(Json.fromValues)
    case BsonValue.BDocument(document) =>
      accumulate(document.toList.toVector.filterNot(_._2.isUndefined)) { case (key, value) =>
        fromBsonDiagnostic(value).left.map(_.prepend(BsonPathSegment.Field(key))).map(key -> _)
      }.map(Json.fromFields)
    case value =>
      fromBson(value).left.map { failure =>
        val kind = value match {
          case BsonValue.BDouble(_) => BsonError.Kind.InvalidValue
          case _                    => BsonError.Kind.UnsupportedType
        }
        BsonErrors(BsonError(kind, failure.getMessage, cause = Some(failure)))
      }
  }

  private def accumulate[A, B](values: Vector[A])(f: A => Either[BsonErrors, B]): Either[BsonErrors, Vector[B]] = {
    val results  = Vector.newBuilder[B]
    val failures = Vector.newBuilder[BsonError]
    values.foreach { value =>
      f(value) match {
        case Right(result) => results += result
        case Left(errors)  => failures ++= errors.errors
      }
    }
    val errors = failures.result()
    errors.headOption match {
      case Some(head) => Left(BsonErrors(head, errors.tail))
      case None       => Right(results.result())
    }
  }

  def binaryBase64ToJson(base64: String, subType: String): Json =
    Json.obj(Tag.binary -> Json.obj("base64" -> Json.fromString(base64), "subType" -> Json.fromString(subType)))

  def binaryArrayToJson(binary: Array[Byte]): Json =
    binaryArrayToJson(binary, 0)

  def binaryArrayToJson(binary: Array[Byte], subtype: Byte): Json =
    binaryBase64ToJson(Base64.getEncoder.encodeToString(binary), f"${subtype & 0xff}%02x")

  def uuidToJson(uuid: UUID): Json =
    binaryBase64ToJson(Uuid.toBase64(uuid), "04")

  def jsonToBinaryBase64(json: Json): Option[String] =
    for {
      obj       <- json.asObject
      bin       <- obj(Tag.binary)
      binObj    <- bin.asObject
      base64    <- binObj("base64")
      base64Str <- base64.asString
    } yield base64Str

  private def jsonToBinarySubtype(json: Json): Option[Byte] =
    for {
      obj     <- json.asObject
      bin     <- obj(Tag.binary)
      binObj  <- bin.asObject
      subtype <- binObj("subType")
      hex     <- subtype.asString
    } yield Integer.parseInt(hex, 16).toByte

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
