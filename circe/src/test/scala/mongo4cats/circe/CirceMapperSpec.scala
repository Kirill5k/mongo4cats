package mongo4cats.circe

import io.circe.Json
import mongo4cats.bson.{BsonValue, Document, ObjectId}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

class CirceMapperSpec extends AnyWordSpec with Matchers {

  val ts = Instant.now()
  val id = ObjectId.get

  val bsonDocument = BsonValue.document(
    Document(
      "string"        -> BsonValue.string("string"),
      "null"          -> BsonValue.Null,
      "boolean"       -> BsonValue.True,
      "long"          -> BsonValue.long(ts.toEpochMilli),
      "int"           -> BsonValue.int(1),
      "bigDecimal"    -> BsonValue.bigDecimal(BigDecimal(100.0)),
      "array"         -> BsonValue.array(BsonValue.string("a"), BsonValue.string("b")),
      "dateInstant"   -> BsonValue.instant(ts),
      "dateEpoch"     -> BsonValue.instant(ts),
      "dateLocalDate" -> BsonValue.instant(Instant.parse("2022-01-01T00:00:00Z")),
      "id"            -> BsonValue.objectId(id),
      "document"      -> BsonValue.document(Document("field1" -> BsonValue.string("1"), "field2" -> BsonValue.int(2)))
    )
  )

  "A CirceMapper" when {
    "toBson" should {
      "accurately convert json to bson" in {
        val jsonObject = Json.obj(
          "string"        -> Json.fromString("string"),
          "null"          -> Json.Null,
          "boolean"       -> Json.fromBoolean(true),
          "long"          -> Json.fromLong(ts.toEpochMilli),
          "int"           -> Json.fromInt(1),
          "bigDecimal"    -> Json.fromBigDecimal(BigDecimal(100.0)),
          "array"         -> Json.arr(Json.fromString("a"), Json.fromString("b")),
          "dateInstant"   -> Json.obj("$date" -> Json.fromString(ts.toString)),
          "dateEpoch"     -> Json.obj("$date" -> Json.fromLong(ts.toEpochMilli)),
          "dateLocalDate" -> Json.obj("$date" -> Json.fromString("2022-01-01")),
          "id"            -> Json.obj("$oid" -> Json.fromString(id.toHexString)),
          "document"      -> Json.obj("field1" -> Json.fromString("1"), "field2" -> Json.fromInt(2))
        )

        CirceMapper.toBson(jsonObject) mustBe bsonDocument
      }

      "accurately convert bson to json" in {
        CirceMapper.fromBson(bsonDocument) mustBe Right(
          Json.obj(
            "string"        -> Json.fromString("string"),
            "null"          -> Json.Null,
            "boolean"       -> Json.fromBoolean(true),
            "long"          -> Json.fromLong(ts.toEpochMilli),
            "int"           -> Json.fromInt(1),
            "bigDecimal"    -> Json.fromBigDecimal(BigDecimal(100.0)),
            "array"         -> Json.arr(Json.fromString("a"), Json.fromString("b")),
            "dateInstant"   -> Json.obj("$date" -> Json.fromString(ts.toString)),
            "dateEpoch"     -> Json.obj("$date" -> Json.fromString(ts.toString)),
            "dateLocalDate" -> Json.obj("$date" -> Json.fromString("2022-01-01T00:00:00Z")),
            "id"            -> Json.obj("$oid" -> Json.fromString(id.toHexString)),
            "document"      -> Json.obj("field1" -> Json.fromString("1"), "field2" -> Json.fromInt(2))
          )
        )
      }
    }
  }
}
