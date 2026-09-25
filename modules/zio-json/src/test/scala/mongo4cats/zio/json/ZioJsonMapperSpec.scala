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
import mongo4cats.errors.MongoJsonParsingException
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.json.ast.Json
import zio.json.JsonEncoder

import java.time.Instant
import java.util.{Base64, UUID}

class ZioJsonMapperSpec extends AnyWordSpec with Matchers {

  val ts = Instant.now()
  val id = ObjectId.gen

  val bsonDocument = BsonValue.document(
    Document(
      "_id"           -> BsonValue.objectId(id),
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
      "document"      -> BsonValue.document(Document("field1" -> BsonValue.string("1"), "field2" -> BsonValue.int(2)))
    )
  )

  def jsonString(s: String): Json          = JsonEncoder.string.toJsonAST(s).toOption.get
  def jsonBool(b: Boolean): Json           = JsonEncoder.boolean.toJsonAST(b).toOption.get
  def jsonInt(i: Int): Json                = JsonEncoder.int.toJsonAST(i).toOption.get
  def jsonLong(l: Long): Json              = JsonEncoder.long.toJsonAST(l).toOption.get
  def jsonBigDecimal(bd: BigDecimal): Json = JsonEncoder.scalaBigDecimal.toJsonAST(bd).toOption.get
  def jsonBigInt(bi: BigInt): Json         = JsonEncoder.scalaBigInt.toJsonAST(bi).toOption.get

  "A ZioJsonMapper" when {
    "toBson" should {
      "accurately convert json to bson" in {
        val jsonObject = Json.Obj(
          "_id"           -> Json.Obj("$oid" -> Json.Str(id.toHexString)),
          "string"        -> jsonString("string"),
          "null"          -> Json.Null,
          "boolean"       -> jsonBool(true),
          "long"          -> jsonLong(ts.toEpochMilli),
          "int"           -> jsonInt(1),
          "bigDecimal"    -> jsonBigDecimal(BigDecimal(100.0)),
          "array"         -> Json.Arr(jsonString("a"), jsonString("b")),
          "dateInstant"   -> Json.Obj("$date" -> jsonString(ts.toString)),
          "dateEpoch"     -> Json.Obj("$date" -> jsonLong(ts.toEpochMilli)),
          "dateLocalDate" -> Json.Obj("$date" -> jsonString("2022-01-01")),
          "document"      -> Json.Obj("field1" -> jsonString("1"), "field2" -> jsonInt(2))
        )

        ZioJsonMapper.toBson(jsonObject).asDocument.map(_.toJson) mustBe bsonDocument.asDocument.map(_.toJson)
      }

      "accurately convert bson to json" in {
        ZioJsonMapper.fromBson(bsonDocument) mustBe Right(
          Json.Obj(
            "_id"           -> Json.Obj("$oid" -> jsonString(id.toHexString)),
            "string"        -> jsonString("string"),
            "null"          -> Json.Null,
            "boolean"       -> jsonBool(true),
            "long"          -> jsonLong(ts.toEpochMilli),
            "int"           -> jsonInt(1),
            "bigDecimal"    -> jsonBigDecimal(BigDecimal(100.0)),
            "array"         -> Json.Arr(jsonString("a"), jsonString("b")),
            "dateInstant"   -> Json.Obj("$date" -> jsonString(ts.toString)),
            "dateEpoch"     -> Json.Obj("$date" -> jsonString(ts.toString)),
            "dateLocalDate" -> Json.Obj("$date" -> jsonString("2022-01-01T00:00:00Z")),
            "document"      -> Json.Obj("field1" -> jsonString("1"), "field2" -> jsonInt(2))
          )
        )
      }

      "handle binary conversions" in {
        val bson = BsonValue.document(
          "uuid"   -> BsonValue.uuid(UUID.fromString("cfbca728-4e39-4613-96bc-f920b5c37e16")),
          "binary" -> BsonValue.binary(Array[Byte](192.toByte, 168.toByte, 1, 9))
        )
        val json = Json.Obj(
          "uuid"   -> Json.Obj("$binary" -> Json.Obj("base64" -> jsonString("z7ynKE45RhOWvPkgtcN+Fg=="), "subType" -> jsonString("04"))),
          "binary" -> Json.Obj("$binary" -> Json.Obj("base64" -> jsonString("wKgBCQ=="), "subType" -> jsonString("00")))
        )

        ZioJsonMapper.fromBson(bson) mustBe Right(json)
        ZioJsonMapper.toBson(json) mustBe bson
      }

      List("03" -> 3, "05" -> 5, "80" -> 128, "ff" -> 255).foreach { case (hex, subtype) =>
        s"preserve binary subtype $hex through json conversion" in {
          val base64     = "z7ynKE45RhOWvPkgtcN+Fg=="
          val bsonBinary = BsonValue.binary(Base64.getDecoder.decode(base64), subtype.toByte)
          val json       = Json.Obj(
            "$binary" -> Json.Obj("base64" -> Json.Str(base64), "subType" -> Json.Str(hex))
          )

          ZioJsonMapper.fromBson(bsonBinary) mustBe Right(json)
          ZioJsonMapper.toBson(json) mustBe bsonBinary

          val nestedBson = BsonValue.document(
            "values" -> BsonValue.array(bsonBinary, BsonValue.document("binary" -> bsonBinary))
          )
          val nestedJson = Json.Obj("values" -> Json.Arr(json, Json.Obj("binary" -> json)))

          ZioJsonMapper.fromBson(nestedBson) mustBe Right(nestedJson)
          ZioJsonMapper.toBson(nestedJson) mustBe nestedBson
        }
      }

      "accept uppercase binary subtype hex digits" in {
        val json = Json.Obj(
          "$binary" -> Json.Obj("base64" -> Json.Str("AQID"), "subType" -> Json.Str("FF"))
        )

        ZioJsonMapper.toBson(json) mustBe BsonValue.binary(Array[Byte](1, 2, 3), 255.toByte)
      }

      "handle numeric conversions" in {
        val bson = BsonValue.document(
          "long"       -> BsonValue.long(ts.toEpochMilli),
          "int"        -> BsonValue.int(1),
          "bigDecimal" -> BsonValue.bigDecimal(BigDecimal(100.0)),
          "bigInt"     -> BsonValue.bigDecimal(BigDecimal(BigInt(Long.MaxValue) + 3))
        )
        val json = Json.Obj(
          "long"       -> jsonLong(ts.toEpochMilli),
          "int"        -> jsonInt(1),
          "bigDecimal" -> jsonBigDecimal(BigDecimal(100.0)),
          "bigInt"     -> jsonBigInt(BigInt(Long.MaxValue) + 3)
        )

        ZioJsonMapper.fromBson(bson) mustBe Right(json)
        ZioJsonMapper.toBson(json) mustBe bson
      }

      "decode canonical dates and numeric milliseconds across the signed 64-bit range" in {
        List(Long.MinValue, -1L, 0L, 1L, Long.MaxValue).foreach { millis =>
          val canonical = Json.Obj("$date" -> Json.Obj("$numberLong" -> Json.Str(millis.toString)))
          val numeric   = Json.Obj("$date" -> Json.Num(millis))
          val expected  = BsonValue.instant(Instant.ofEpochMilli(millis))

          ZioJsonMapper.toBson(canonical) mustBe expected
          ZioJsonMapper.toBson(numeric) mustBe expected
          ZioJsonMapper.toBson(canonical).asJava.asDateTime().getValue mustBe millis
        }
        ZioJsonMapper.toBson(Json.Obj("$date" -> Json.Num(BigDecimal("1.0")))) mustBe BsonValue.instant(Instant.ofEpochMilli(1L))
      }

      "decode canonical dates and decimals nested in documents and arrays" in {
        val json = Json.Obj(
          "values" -> Json.Arr(
            Json.Obj("$date"  -> Json.Obj("$numberLong" -> Json.Str("-1"))),
            Json.Obj("amount" -> Json.Obj("$numberDecimal" -> Json.Str("12.50")))
          )
        )
        val bson = BsonValue.document(
          "values" -> BsonValue.array(
            BsonValue.instant(Instant.ofEpochMilli(-1L)),
            BsonValue.document("amount" -> BsonValue.bigDecimal(BigDecimal("12.50")))
          )
        )

        ZioJsonMapper.toBson(json) mustBe bson
      }

      "reject malformed date wrappers with typed parsing errors" in {
        val invalidDates = List(
          Json.Obj("$date" -> Json.Null),
          Json.Obj("$date" -> Json.Bool(true)),
          Json.Obj("$date" -> Json.Arr()),
          Json.Obj("$date" -> Json.Str("not-a-date")),
          Json.Obj("$date" -> Json.Str("+999999999-12-31T23:59:59Z")),
          Json.Obj("$date" -> Json.Num(BigDecimal("1.5"))),
          Json.Obj("$date" -> Json.Num(BigDecimal("9223372036854775808"))),
          Json.Obj("$date" -> Json.Num(BigDecimal("-9223372036854775809"))),
          Json.Obj("$date" -> Json.Obj()),
          Json.Obj("$date" -> Json.Obj("$numberLong" -> Json.Num(1))),
          Json.Obj("$date" -> Json.Obj("$numberLong" -> Json.Str("1.5"))),
          Json.Obj("$date" -> Json.Obj("$numberLong" -> Json.Str("9223372036854775808"))),
          Json.Obj("$date" -> Json.Obj("$numberLong" -> Json.Str("-9223372036854775809"))),
          Json.Obj("$date" -> Json.Obj("$numberLong" -> Json.Str("0"), "extra" -> Json.Null)),
          Json.Obj("$date" -> Json.Num(0), "extra"              -> Json.Null),
          Json.Obj("$oid"  -> Json.Str(id.toHexString), "$date" -> Json.Num(0))
        )

        invalidDates.foreach { json =>
          withClue(json.toString) {
            intercept[MongoJsonParsingException](ZioJsonMapper.toBson(json))
            intercept[MongoJsonParsingException](ZioJsonMapper.toBson(Json.Obj("nested" -> Json.Arr(json))))
          }
        }
      }

      "decode finite Decimal128 wrappers within precision and exponent limits" in
        List("0", "-12.50", "1234567890123456789012345678901234", "1E-6176", "1E+6144", "9.999999999999999999999999999999999E+6144")
          .foreach { value =>
            val json = Json.Obj("$numberDecimal" -> Json.Str(value))
            val bson = ZioJsonMapper.toBson(json)

            bson mustBe BsonValue.bigDecimal(BigDecimal(value))
            BigDecimal(bson.asJava.asDecimal128().getValue.bigDecimalValue()) mustBe BigDecimal(value)
          }

      "reject malformed or unrepresentable Decimal128 wrappers with typed parsing errors" in {
        val invalidValues = List(
          "",
          "not-a-decimal",
          "12345678901234567890123456789012345",
          "1E-6177",
          "1E+6145",
          "NaN",
          "Infinity",
          "-Infinity",
          "-0",
          "-0.00"
        ).map(value => Json.Obj("$numberDecimal" -> Json.Str(value)))
        val invalidShapes = List(
          Json.Obj("$numberDecimal" -> Json.Null),
          Json.Obj("$numberDecimal" -> Json.Num(1)),
          Json.Obj("$numberDecimal" -> Json.Obj()),
          Json.Obj("$numberDecimal" -> Json.Str("1"), "extra"                     -> Json.Null),
          Json.Obj("$oid"           -> Json.Str(id.toHexString), "$numberDecimal" -> Json.Str("1"))
        )

        (invalidValues ++ invalidShapes).foreach { json =>
          withClue(json.toString) {
            intercept[MongoJsonParsingException](ZioJsonMapper.toBson(json))
            intercept[MongoJsonParsingException](ZioJsonMapper.toBson(Json.Obj("nested" -> Json.Arr(json))))
          }
        }
      }

      "retain the existing date and decimal JSON output" in {
        val canonicalDate = Json.Obj("$date" -> Json.Obj("$numberLong" -> Json.Str("0")))
        val decimal       = Json.Obj("$numberDecimal" -> Json.Str("12.50"))

        ZioJsonMapper.fromBson(ZioJsonMapper.toBson(canonicalDate)) mustBe Right(Json.Obj("$date" -> Json.Str("1970-01-01T00:00:00Z")))
        ZioJsonMapper.fromBson(ZioJsonMapper.toBson(decimal)) mustBe Right(Json.Num(BigDecimal("12.50")))
      }

      "leave unsupported Extended JSON wrappers as ordinary documents" in {
        val json = Json.Obj("$numberLong" -> Json.Str("1"))
        val bson = BsonValue.document("$numberLong" -> BsonValue.string("1"))

        ZioJsonMapper.toBson(json) mustBe bson
        ZioJsonMapper.fromBson(bson) mustBe Right(json)
      }
    }
  }
}
