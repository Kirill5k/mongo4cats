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

import mongo4cats.bson.json._
import mongo4cats.bson.{BsonValue, Document, ObjectId}
import mongo4cats.errors.MongoJsonParsingException
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.json._
import zio.json.ast.Json

import java.time.{Instant, LocalDate}
import java.util.UUID

class MongoJsonCodecsSpec extends AnyWordSpec with Matchers with MongoJsonCodecs {

  "ObjectId codec" should {
    "encode and decode ObjectId to json and back" in {
      val oid  = ObjectId.gen
      val json = s"""{"${Tag.id}":"${oid.toHexString}"}"""

      objectIdEncoder.encodeJson(oid).toString mustBe json
      objectIdDecoder.decodeJson(json) mustBe Right(oid)
    }
  }

  "Instant codec" should {
    "encode and decode Instant to json and back" in {
      val inst = Instant.now()
      val json = s"""{"${Tag.date}":"$inst"}"""

      instantEncoder.encodeJson(inst).toString mustBe json
      instantDecoder.decodeJson(json) mustBe Right(inst)
    }

    "decode canonical dates, numeric milliseconds and local dates" in {
      List(Long.MinValue, -1L, 0L, Long.MaxValue).foreach { millis =>
        val canonical = Json.Obj("$date" -> Json.Obj("$numberLong" -> Json.Str(millis.toString)))
        val numeric   = Json.Obj("$date" -> Json.Num(millis))

        instantDecoder.fromJsonAST(canonical) mustBe Right(Instant.ofEpochMilli(millis))
        instantDecoder.fromJsonAST(numeric) mustBe Right(Instant.ofEpochMilli(millis))
      }

      instantDecoder.decodeJson("""{"$date":"2022-01-01"}""") mustBe Right(Instant.parse("2022-01-01T00:00:00Z"))
    }

    "return decoding failures for invalid date wrappers" in
      List(
        """{"$date":{"$numberLong":"9223372036854775808"}}""",
        """{"$date":{"$numberLong":1}}""",
        """{"$date":1.5}""",
        """{"$date":"invalid"}""",
        """{"$date":0,"extra":true}"""
      ).foreach { json =>
        withClue(json)(instantDecoder.decodeJson(json).isLeft mustBe true)
      }
  }

  "LocalDate codec" should {
    "encode and decode LocalDate to json and back" in {
      val date = LocalDate.now()
      val json = s"""{"${Tag.date}":"${date}"}"""

      localDateEncoder.encodeJson(date).toString mustBe json
      localDateDecoder.decodeJson(json) mustBe Right(date)
    }
  }

  "UUID codec" should {
    "encode and decode UUID to json and back" in {
      val uuid = UUID.fromString("cfbca728-4e39-4613-96bc-f920b5c37e16")
      val json = s"""{"${Tag.binary}":{"base64":"z7ynKE45RhOWvPkgtcN+Fg==","subType":"04"}}"""

      uuidEncoder.encodeJson(uuid).toString mustBe json
      uuidDecoder.decodeJson(json) mustBe Right(uuid)
    }
  }

  "Binary array codec" should {
    "encode and decode binary array to json and back" in {
      val binaryArray = Array[Byte](192.toByte, 168.toByte, 1.toByte, 9.toByte)
      val json        = s"""{"${Tag.binary}":{"base64":"wKgBCQ==","subType":"00"}}""".stripMargin

      binaryEncoder.encodeJson(binaryArray).toString mustBe json
      binaryDecoder.decodeJson(json).map(_.toList) mustBe Right(binaryArray.toList)
    }
  }

  "Document codec" should {
    "decode nested canonical dates and Decimal128 wrappers" in {
      val json     = """{"values":[{"$date":{"$numberLong":"-1"}},{"amount":{"$numberDecimal":"12.50"}}]}"""
      val document = Document(
        "values" -> BsonValue.array(
          BsonValue.instant(Instant.ofEpochMilli(-1L)),
          BsonValue.document("amount" -> BsonValue.bigDecimal(BigDecimal("12.50")))
        )
      )

      documentDecoder.decodeJson(json) mustBe Right(document)
    }

    "return decoding failures for malformed recognized wrappers instead of throwing" in
      List(
        """{"$date":null}""",
        """{"$date":{"$numberLong":1}}""",
        """{"$date":{"$numberLong":"9223372036854775808"}}""",
        """{"$date":9223372036854775808}""",
        """{"$date":-9223372036854775809}""",
        """{"$date":0.1}""",
        """{"$date":"invalid"}""",
        """{"$date":0,"extra":true}""",
        """{"$numberDecimal":1}""",
        """{"$numberDecimal":"invalid"}""",
        """{"$numberDecimal":"NaN"}""",
        """{"$numberDecimal":"Infinity"}""",
        """{"$numberDecimal":"-0"}""",
        """{"$numberDecimal":"1E+6145"}""",
        """{"$numberDecimal":"1","extra":true}"""
      ).foreach { wrapper =>
        val json = s"""{"nested":[$wrapper]}"""
        withClue(json)(documentDecoder.decodeJson(json).isLeft mustBe true)
      }

    "fail explicitly when a document contains an unsupported BSON timestamp" in {
      val document = Document("timestamp" -> BsonValue.timestamp(1L))

      intercept[MongoJsonParsingException](documentEncoder.encodeJson(document))
    }

    "encode and decode Document to json and back" in {
      val id       = ObjectId.gen
      val ts       = Instant.now
      val document = Document(
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
        "document"      -> BsonValue.document(Document("field1" -> BsonValue.string("1"), "field2" -> BsonValue.int(2))),
        "uuid"          -> BsonValue.uuid(UUID.fromString("cfbca728-4e39-4613-96bc-f920b5c37e16"))
      )

      val json =
        s"""{
          |  "_id" : {
          |    "${Tag.id}" : "${id.toHexString}"
          |  },
          |  "string" : "string",
          |  "null" : null,
          |  "boolean" : true,
          |  "long" : ${ts.toEpochMilli},
          |  "int" : 1,
          |  "bigDecimal" : 100.0,
          |  "array" : [
          |    "a",
          |    "b"
          |  ],
          |  "dateInstant" : {
          |    "${Tag.date}" : "$ts"
          |  },
          |  "dateEpoch" : {
          |    "${Tag.date}" : "$ts"
          |  },
          |  "dateLocalDate" : {
          |    "${Tag.date}" : "2022-01-01T00:00:00Z"
          |  },
          |  "document" : {
          |    "field1" : "1",
          |    "field2" : 2
          |  },
          |  "uuid" : {
          |    "${Tag.binary}" : {
          |      "base64" : "z7ynKE45RhOWvPkgtcN+Fg==",
          |      "subType" : "04"
          |    }
          |  }
          |}""".stripMargin

      Json.decoder.decodeJson(documentEncoder.encodeJson(document)).toOption.get.toJsonPretty mustBe json
      documentDecoder.decodeJson(json) mustBe Right(document)
    }
  }
}
