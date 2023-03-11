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

import mongo4cats.bson.{BsonValue, Document, ObjectId}
import zio.json.ast.Json

import java.time.{Instant, LocalDate, ZoneOffset}
import scala.math.BigDecimal._

private[json] object JsonMapper {
  val idTag   = "$oid"
  val dateTag = "$date"

  def toBson(json: Json): BsonValue =
    json match {
      case j if j.isNull        => BsonValue.Null
      case j if j.isArray       => BsonValue.array(j.asArray.get.map(toBson))
      case j if j.isBoolean     => BsonValue.boolean(j.asBoolean.get)
      case j if j.isString      => BsonValue.string(j.asString.get)
      case j if j.isNumber      => j.asNumber.get.toBsonValue
      case j if j.isId          => BsonValue.objectId(j.asObjectId)
      case j if j.isEpochMillis => BsonValue.instant(Instant.ofEpochMilli(j.asEpochMillis))
      case j if j.isLocalDate   => BsonValue.instant(LocalDate.parse(j.asIsoDateString).atStartOfDay().toInstant(ZoneOffset.UTC))
      case j if j.isDate        => BsonValue.instant(Instant.parse(j.asIsoDateString))
      case j => BsonValue.document(Document(j.asObject.get.fields.toList.map { case (key, value) => key -> toBson(value) }))
    }

  implicit final private class JsonSyntax(private val json: Json) extends AnyVal {
    def isNull: Boolean        = json.asNull.nonEmpty
    def isArray: Boolean       = json.asArray.nonEmpty
    def isBoolean: Boolean     = json.asBoolean.nonEmpty
    def isString: Boolean      = json.asString.nonEmpty
    def isNumber: Boolean      = json.asNumber.nonEmpty
    def isId: Boolean          = json.asObject.nonEmpty && json.asObject.exists(_.contains(idTag))
    def isDate: Boolean        = json.asObject.nonEmpty && json.asObject.exists(_.contains(dateTag))
    def isEpochMillis: Boolean = isDate && json.asObject.exists(_.fields.toMap.get(dateTag).exists(_.isNumber))
    def isLocalDate: Boolean =
      isDate && json.asObject.exists(o =>
        o.fields.toMap.get(dateTag).exists(_.isString) && o.fields.toMap.get(dateTag).exists(_.asString.get.length == 10)
      )

    def asEpochMillis: Long =
      (for {
        obj  <- json.asObject
        date <- obj.get(dateTag)
        num  <- date.asNumber
        ts = num.value.toLong
      } yield ts).get

    def asIsoDateString: String =
      (for {
        obj  <- json.asObject
        date <- obj.get(dateTag)
        str  <- date.asString
      } yield str).get

    def asObjectId: ObjectId =
      (for {
        obj   <- json.asObject
        id    <- obj.get(idTag)
        hex   <- id.asString
        objId <- ObjectId.from(hex).toOption
      } yield objId).get
  }

  implicit final private class JsonNumSyntax(private val jNumber: Json.Num) extends AnyVal {
    def isDecimal: Boolean = jNumber.toString.contains(".")
    def toBsonValue: BsonValue =
      jNumber.value match {
        case n if n.isExactDouble => BsonValue.double(n.toDouble)
        case n if n.isValidInt    => BsonValue.int(n.toInt)
        case n if n.isValidLong   => BsonValue.long(n.toLong)
        case n                    => BsonValue.bigDecimal(n)
      }
  }

  def fromBson(bson: BsonValue): Either[MongoJsonParsingException, Json] = {
    def right[A](a: A): Either[MongoJsonParsingException, A] = Right(a)

    bson match {
      case BsonValue.BNull            => Right(Json.Null)
      case BsonValue.BObjectId(value) => Right(Json.Obj(idTag -> Json.Str(value.toHexString)))
      case BsonValue.BDateTime(value) => Right(Json.Obj(dateTag -> Json.Str(value.toString)))
      case BsonValue.BInt32(value)    => Right(Json.Num(value))
      case BsonValue.BInt64(value)    => Right(Json.Num(value))
      case BsonValue.BBoolean(value)  => Right(Json.Bool(value))
      case BsonValue.BDecimal(value)  => Right(Json.Num(value))
      case BsonValue.BString(value)   => Right(Json.Str(value))
      case BsonValue.BDouble(value)   => Right(Json.Num(value))
      case BsonValue.BArray(value) =>
        value.toList
          .foldRight(right(List.empty[Json])) { case (a: BsonValue, acc: Either[MongoJsonParsingException, List[Json]]) =>
            for {
              x  <- fromBson(a)
              xs <- acc
            } yield x :: xs
          }
          .map(xs => Json.Arr(xs: _*))
      case BsonValue.BDocument(value) =>
        value.toList
          .filterNot { case (_, v) => v.isUndefined }
          .foldRight(right(List.empty[(String, Json)])) {
            case (a: (String, BsonValue), acc: Either[MongoJsonParsingException, List[(String, Json)]]) =>
              for {
                x  <- fromBson(a._2)
                xs <- acc
              } yield (a._1, x) :: xs
          }
          .map(xs => Json.Obj(xs: _*))
      case value => Left(MongoJsonParsingException(s"Cannot map $value bson value to json"))
    }
  }

  def fromBsonOpt(bson: BsonValue): Option[Json] =
    bson match {
      case BsonValue.BNull            => Some(Json.Null)
      case BsonValue.BObjectId(value) => Some(Json.Obj(idTag -> Json.Str(value.toHexString)))
      case BsonValue.BDateTime(value) => Some(Json.Obj(dateTag -> Json.Str(value.toString)))
      case BsonValue.BInt32(value)    => Some(Json.Num(value))
      case BsonValue.BInt64(value)    => Some(Json.Num(value))
      case BsonValue.BBoolean(value)  => Some(Json.Bool(value))
      case BsonValue.BDecimal(value)  => Some(Json.Num(value))
      case BsonValue.BString(value)   => Some(Json.Str(value))
      case BsonValue.BDouble(value)   => Some(Json.Num(value))
      case BsonValue.BArray(value)    => Some(Json.Arr(value.toList.flatMap(fromBsonOpt): _*))
      case BsonValue.BDocument(value) => Some(Json.Obj(value.toList.flatMap { case (k, v) => fromBsonOpt(v).map(k -> _) }: _*))
      case _                          => None
    }
}
