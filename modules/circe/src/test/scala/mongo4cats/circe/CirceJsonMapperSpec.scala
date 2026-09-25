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

import io.circe.Json
import mongo4cats.bson.{BsonValue, Document, ObjectId}
import mongo4cats.errors.MongoJsonParsingException
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.{Base64, UUID}

class CirceJsonMapperSpec extends AnyWordSpec with Matchers {

  val ts = Instant.now()
  val id = ObjectId.gen

  val bsonDocument = BsonValue.document(
    Document(
      "_id"           -> BsonValue.objectId(id),
      "string"        -> BsonValue.string("string"),
      "null"          -> BsonValue.Null,
      "boolean"       -> BsonValue.True,
      "array"         -> BsonValue.array(BsonValue.string("a"), BsonValue.string("b")),
      "dateInstant"   -> BsonValue.instant(ts),
      "dateEpoch"     -> BsonValue.instant(ts),
      "dateLocalDate" -> BsonValue.instant(Instant.parse("2022-01-01T00:00:00Z")),
      "document"      -> BsonValue.document(Document("field1" -> BsonValue.string("1"), "field2" -> BsonValue.int(2)))
    )
  )

  "A CirceMapper" when {
    "toBson" should {
      "accurately convert json to bson" in {
        val jsonObject = Json.obj(
          "_id"           -> Json.obj("$oid" -> Json.fromString(id.toHexString)),
          "string"        -> Json.fromString("string"),
          "null"          -> Json.Null,
          "boolean"       -> Json.fromBoolean(true),
          "array"         -> Json.arr(Json.fromString("a"), Json.fromString("b")),
          "dateInstant"   -> Json.obj("$date" -> Json.fromString(ts.toString)),
          "dateEpoch"     -> Json.obj("$date" -> Json.fromLong(ts.toEpochMilli)),
          "dateLocalDate" -> Json.obj("$date" -> Json.fromString("2022-01-01")),
          "document"      -> Json.obj("field1" -> Json.fromString("1"), "field2" -> Json.fromInt(2))
        )

        CirceJsonMapper.toBson(jsonObject).asDocument.map(_.toJson) mustBe bsonDocument.asDocument.map(_.toJson)
      }

      "accurately convert bson to json" in {
        CirceJsonMapper.fromBson(bsonDocument) mustBe Right(
          Json.obj(
            "_id"           -> Json.obj("$oid" -> Json.fromString(id.toHexString)),
            "string"        -> Json.fromString("string"),
            "null"          -> Json.Null,
            "boolean"       -> Json.fromBoolean(true),
            "array"         -> Json.arr(Json.fromString("a"), Json.fromString("b")),
            "dateInstant"   -> Json.obj("$date" -> Json.fromString(ts.toString)),
            "dateEpoch"     -> Json.obj("$date" -> Json.fromString(ts.toString)),
            "dateLocalDate" -> Json.obj("$date" -> Json.fromString("2022-01-01T00:00:00Z")),
            "document"      -> Json.obj("field1" -> Json.fromString("1"), "field2" -> Json.fromInt(2))
          )
        )
      }

      "handle binary conversions" in {
        val bson = BsonValue.document(
          "uuid"   -> BsonValue.uuid(UUID.fromString("cfbca728-4e39-4613-96bc-f920b5c37e16")),
          "binary" -> BsonValue.binary(Array[Byte](192.toByte, 168.toByte, 1, 9))
        )
        val json = Json.obj(
          "uuid" -> Json.obj(
            "$binary" -> Json.obj("base64" -> Json.fromString("z7ynKE45RhOWvPkgtcN+Fg=="), "subType" -> Json.fromString("04"))
          ),
          "binary" -> Json.obj("$binary" -> Json.obj("base64" -> Json.fromString("wKgBCQ=="), "subType" -> Json.fromString("00")))
        )

        CirceJsonMapper.fromBson(bson) mustBe Right(json)
        CirceJsonMapper.toBson(json) mustBe bson
      }

      List("03" -> 3, "05" -> 5, "80" -> 128, "ff" -> 255).foreach { case (hex, subtype) =>
        s"preserve binary subtype $hex through json conversion" in {
          val base64     = "z7ynKE45RhOWvPkgtcN+Fg=="
          val bsonBinary = BsonValue.binary(Base64.getDecoder.decode(base64), subtype.toByte)
          val json       = Json.obj(
            "$binary" -> Json.obj("base64" -> Json.fromString(base64), "subType" -> Json.fromString(hex))
          )

          CirceJsonMapper.fromBson(bsonBinary) mustBe Right(json)
          CirceJsonMapper.toBson(json) mustBe bsonBinary

          val nestedBson = BsonValue.document(
            "values" -> BsonValue.array(bsonBinary, BsonValue.document("binary" -> bsonBinary))
          )
          val nestedJson = Json.obj("values" -> Json.arr(json, Json.obj("binary" -> json)))

          CirceJsonMapper.fromBson(nestedBson) mustBe Right(nestedJson)
          CirceJsonMapper.toBson(nestedJson) mustBe nestedBson
        }
      }

      "accept uppercase binary subtype hex digits" in {
        val json = Json.obj(
          "$binary" -> Json.obj("base64" -> Json.fromString("AQID"), "subType" -> Json.fromString("FF"))
        )

        CirceJsonMapper.toBson(json) mustBe BsonValue.binary(Array[Byte](1, 2, 3), 255.toByte)
      }

      "handle numeric conversions" in {
        val bson = BsonValue.document(
          "double"     -> BsonValue.double(0.2),
          "long"       -> BsonValue.long(ts.toEpochMilli),
          "int"        -> BsonValue.int(1),
          "bigDecimal" -> BsonValue.bigDecimal(BigDecimal(100.0)),
          "bigInt"     -> BsonValue.bigDecimal(BigDecimal(BigInt(Long.MaxValue) + 3))
        )
        val json = Json.obj(
          "double"     -> Json.fromDouble(0.2).get,
          "long"       -> Json.fromLong(ts.toEpochMilli),
          "int"        -> Json.fromInt(1),
          "bigDecimal" -> Json.fromBigDecimal(BigDecimal(100.0)),
          "bigInt"     -> Json.fromBigInt(BigInt(Long.MaxValue) + 3)
        )

        CirceJsonMapper.fromBson(bson) mustBe Right(json)
        CirceJsonMapper.toBson(json) mustBe bson
      }

      "decode canonical dates at the signed millisecond boundaries" in {
        List(Long.MinValue, -1L, 0L, 1L, Long.MaxValue).foreach { millis =>
          val expected  = BsonValue.instant(Instant.ofEpochMilli(millis))
          val canonical = Json.obj("$date" -> Json.obj("$numberLong" -> Json.fromString(millis.toString)))

          CirceJsonMapper.toBson(canonical) mustBe expected
          CirceJsonMapper.toBson(Json.obj("$date" -> Json.fromLong(millis))) mustBe expected
          CirceJsonMapper.fromBson(expected) mustBe Right(Json.obj("$date" -> Json.fromString(Instant.ofEpochMilli(millis).toString)))
        }

        CirceJsonMapper.toBson(Json.obj("$date" -> Json.fromBigDecimal(BigDecimal("1.0")))) mustBe
          BsonValue.instant(Instant.ofEpochMilli(1L))
      }

      "decode finite Decimal128 wrappers without changing decimal output" in
        List("0", "-1", "123.4500", "1234567890123456789012345678901234", "1E-6176", "9.999999999999999999999999999999999E+6144")
          .foreach { decimal =>
            val value    = BigDecimal(decimal)
            val expected = BsonValue.bigDecimal(value)

            CirceJsonMapper.toBson(Json.obj("$numberDecimal" -> Json.fromString(decimal))) mustBe expected
            CirceJsonMapper.fromBson(expected) mustBe Right(Json.fromBigDecimal(value))
          }

      "decode date and decimal wrappers recursively in arrays and documents" in {
        val json = Json.obj(
          "values" -> Json.arr(
            Json.obj("$date"  -> Json.obj("$numberLong" -> Json.fromString("-1"))),
            Json.obj("amount" -> Json.obj("$numberDecimal" -> Json.fromString("12.50")))
          )
        )
        val expected = BsonValue.document(
          "values" -> BsonValue.array(
            BsonValue.instant(Instant.ofEpochMilli(-1L)),
            BsonValue.document("amount" -> BsonValue.bigDecimal(BigDecimal("12.50")))
          )
        )

        CirceJsonMapper.toBson(json) mustBe expected
      }

      "reject malformed date wrappers with a typed parsing error" in {
        val invalidValues = List(
          Json.Null,
          Json.True,
          Json.arr(),
          Json.fromString("not-a-date"),
          Json.fromString("2022-99-99"),
          Json.fromString("+1000000000-12-31T23:59:59.999999999Z"),
          Json.fromBigDecimal(BigDecimal("1.5")),
          Json.fromBigInt(BigInt(Long.MaxValue) + 1),
          Json.fromBigInt(BigInt(Long.MinValue) - 1),
          Json.obj(),
          Json.obj("$numberLong" -> Json.fromLong(1L)),
          Json.obj("$numberLong" -> Json.fromString("1"), "extra" -> Json.Null)
        ) ++ List("9223372036854775808", "-9223372036854775809", "1.5", "1e3", "", "invalid").map { millis =>
          Json.obj("$numberLong" -> Json.fromString(millis))
        }
        val invalid = invalidValues.map(value => Json.obj("$date" -> value)) ++ List(
          Json.obj("$date" -> Json.fromLong(0L), "extra"               -> Json.Null),
          Json.obj("$oid"  -> Json.fromString(id.toHexString), "$date" -> Json.fromLong(0L))
        )

        invalid.foreach { json =>
          withClue(json.noSpaces) {
            intercept[MongoJsonParsingException](CirceJsonMapper.toBson(json))
            CirceJsonMapper.toBsonEither(json).isLeft mustBe true
          }
        }
      }

      "reject malformed or unrepresentable decimal wrappers with a typed parsing error" in {
        val invalidValues = List(Json.Null, Json.True, Json.fromInt(1), Json.arr(), Json.obj()) ++
          List("", "invalid", "NaN", "Infinity", "-Infinity", "-0", "-0.000", "1E-6177", "1E+6145", "12345678901234567890123456789012345")
            .map(Json.fromString)
        val invalid = invalidValues.map(value => Json.obj("$numberDecimal" -> value)) ++ List(
          Json.obj("$numberDecimal" -> Json.fromString("1"), "extra"                     -> Json.Null),
          Json.obj("$oid"           -> Json.fromString(id.toHexString), "$numberDecimal" -> Json.fromString("1"))
        )

        invalid.foreach { json =>
          withClue(json.noSpaces) {
            intercept[MongoJsonParsingException](CirceJsonMapper.toBson(json))
            CirceJsonMapper.toBsonEither(json).isLeft mustBe true
          }
        }
      }

      "continue treating unsupported Extended JSON wrappers as ordinary documents" in {
        val json = Json.obj(
          "integer"   -> Json.obj("$numberLong" -> Json.fromString("42")),
          "timestamp" -> Json.obj("$timestamp" -> Json.obj("t" -> Json.fromInt(1), "i" -> Json.fromInt(2)))
        )

        CirceJsonMapper.toBson(json) mustBe BsonValue.document(
          "integer"   -> BsonValue.document("$numberLong" -> BsonValue.string("42")),
          "timestamp" -> BsonValue.document("$timestamp" -> BsonValue.document("t" -> BsonValue.int(1), "i" -> BsonValue.int(2)))
        )
      }
    }
  }
}
