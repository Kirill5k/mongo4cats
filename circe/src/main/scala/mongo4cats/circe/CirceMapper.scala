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
    def toBsonValue: BsonValue = jNumber.toLong
      .map(BsonValue.long)
      .orElse(jNumber.toInt.map(BsonValue.int))
      .orElse(jNumber.toBigDecimal.map(BsonValue.bigDecimal))
      .getOrElse(BsonValue.double(jNumber.toDouble))
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
