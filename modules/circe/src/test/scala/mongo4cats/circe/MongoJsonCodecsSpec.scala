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
import io.circe.syntax._
import io.circe.parser._
import mongo4cats.bson.json._
import mongo4cats.bson.{BsonValue, Document, ObjectId}
import mongo4cats.errors.MongoJsonParsingException
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.{Instant, LocalDate}
import java.util.UUID

class MongoJsonCodecsSpec extends AnyWordSpec with Matchers with MongoJsonCodecs {

  "ObjectId codec" should {
    "encode and decode ObjectId to json and back" in {
      val oid  = ObjectId.gen
      val json = s"""{"${Tag.id}":"${oid.toHexString}"}"""

      oid.asJson.noSpaces mustBe json
      decode[ObjectId](json) mustBe Right(oid)
    }

    "return decoding failures for non-object and malformed ObjectId values" in
      List(
        "42",
        "null",
        "true",
        "[]",
        "[{}]",
        "\"507f1f77bcf86cd799439011\"",
        "{}",
        """{"$oid":42}""",
        """{"$oid":null}""",
        """{"$oid":[]}""",
        """{"$oid":{}}""",
        """{"$oid":"invalid"}""",
        """{"$oid":"507f1f77bcf86cd799439011","extra":true}"""
      ).foreach { json =>
        withClue(json)(decode[ObjectId](json).isLeft mustBe true)
      }
  }

  "Instant codec" should {
    "encode and decode Instant to json and back" in {
      val inst = Instant.now()
      val json = s"""{"${Tag.date}":"$inst"}"""

      inst.asJson.noSpaces mustBe json
      decode[Instant](json) mustBe Right(inst)
    }

    "decode canonical, numeric and local-date representations" in {
      List(Long.MinValue, -1L, 0L, Long.MaxValue).foreach { millis =>
        val canonical = Json.obj("$date" -> Json.obj("$numberLong" -> Json.fromString(millis.toString)))
        val numeric   = Json.obj("$date" -> Json.fromLong(millis))

        canonical.as[Instant] mustBe Right(Instant.ofEpochMilli(millis))
        numeric.as[Instant] mustBe Right(Instant.ofEpochMilli(millis))
      }

      decode[Instant]("""{"$date":"2022-01-01"}""") mustBe Right(Instant.parse("2022-01-01T00:00:00Z"))
    }

    "return decoding failures for malformed dates" in
      List(
        """{"$date":1.5}""",
        """{"$date":9223372036854775808}""",
        """{"$date":{"$numberLong":"invalid"}}""",
        """{"$date":{"$numberLong":1}}""",
        """{"$date":0,"extra":true}""",
        """{"$date":"2022-99-99"}"""
      ).foreach(json => decode[Instant](json).isLeft mustBe true)
  }

  "LocalDate codec" should {
    "encode and decode LocalDate to json and back" in {
      val date = LocalDate.now()
      val json = s"""{"${Tag.date}":"${date}"}"""

      date.asJson.noSpaces mustBe json
      decode[LocalDate](json) mustBe Right(date)
    }
  }

  "UUID codec" should {
    "encode and decode UUID to json and back" in {
      val uuid = UUID.fromString("cfbca728-4e39-4613-96bc-f920b5c37e16")
      val json = s"""{"${Tag.binary}":{"base64":"z7ynKE45RhOWvPkgtcN+Fg==","subType":"04"}}""".stripMargin

      uuid.asJson.noSpaces mustBe json
      decode[UUID](json) mustBe Right(uuid)
    }
  }

  "Binary array codec" should {
    "encode and decode binary array to json and back" in {
      val binaryArray = Array[Byte](192.toByte, 168.toByte, 1.toByte, 9.toByte)
      val json        = s"""{"${Tag.binary}":{"base64":"wKgBCQ==","subType":"00"}}""".stripMargin

      binaryArray.asJson.noSpaces mustBe json
      decode[Array[Byte]](json).map(_.toList) mustBe Right(binaryArray.toList)
    }
  }

  "Document codec" should {
    "preserve valid nested ObjectId, UUID and binary wrappers" in {
      val json = """{
                   |  "values":[
                   |    {"$oid":"507f1f77bcf86cd799439011"},
                   |    {"$binary":{"base64":"AQID","subType":"00"}},
                   |    {"$binary":{"base64":"AQID","subType":"FF"}},
                   |    {"$binary":{"base64":"z7ynKE45RhOWvPkgtcN+Fg==","subType":"03"}},
                   |    {"$binary":{"base64":"z7ynKE45RhOWvPkgtcN+Fg==","subType":"04"}}
                   |  ],
                   |  "extra":true
                   |}""".stripMargin
      val uuid = UUID.fromString("cfbca728-4e39-4613-96bc-f920b5c37e16")
      val expected = Document(
        "values" -> BsonValue.array(
          BsonValue.objectId(ObjectId("507f1f77bcf86cd799439011")),
          BsonValue.binary(Array[Byte](1, 2, 3)),
          BsonValue.binary(Array[Byte](1, 2, 3), 255.toByte),
          BsonValue.binary(java.util.Base64.getDecoder.decode("z7ynKE45RhOWvPkgtcN+Fg=="), 3.toByte),
          BsonValue.uuid(uuid)
        ),
        "extra" -> BsonValue.True
      )

      decode[Document](json) mustBe Right(expected)
    }

    "reject malformed binary and ObjectId wrappers without discarding fields" in
      List(
        """{"$oid":"507f1f77bcf86cd799439011","extra":true}""",
        """{"$oid":null}""",
        """{"$oid":42}""",
        """{"$oid":[]}""",
        """{"$oid":{}}""",
        """{"$oid":"invalid"}""",
        """{"$binary":{"base64":"AQID","subType":"00"},"extra":true}""",
        """{"$binary":{"base64":"AQID","subType":"ff"},"extra":true}""",
        """{"$binary":{"base64":"z7ynKE45RhOWvPkgtcN+Fg==","subType":"04"},"extra":true}""",
        """{"$binary":{"base64":"AQID","subType":"ff","extra":true}}""",
        """{"$binary":{"base64":"z7ynKE45RhOWvPkgtcN+Fg==","subType":"04","extra":true}}""",
        """{"$binary":null}""",
        """{"$binary":[]}""",
        """{"$binary":"AQID"}""",
        """{"$binary":{}}""",
        """{"$binary":{"base64":"AQID"}}""",
        """{"$binary":{"subType":"00"}}""",
        """{"$binary":{"base64":1,"subType":"00"}}""",
        """{"$binary":{"base64":"AQID","subType":0}}""",
        """{"$binary":{"base64":"AQID","subType":"0"}}""",
        """{"$binary":{"base64":"AQID","subType":"100"}}""",
        """{"$binary":{"base64":"AQID","subType":"gg"}}""",
        """{"$binary":{"base64":"!","subType":"00"}}""",
        """{"$binary":{"base64":"AQID","subType":"04"}}""",
        """{"$binary":{"base64":"AAAAAAAAAAAAAAAAAAAAAAA=","subType":"04"}}"""
      ).foreach { wrapper =>
        List(s"""{"value":$wrapper}""", s"""{"values":[$wrapper]}""").foreach { json =>
          withClue(json)(decode[Document](json).isLeft mustBe true)
        }
      }

    "fail explicitly when encoding unsupported BSON values" in {
      val document = Document("timestamp" -> BsonValue.timestamp(4294967295L))

      intercept[MongoJsonParsingException](document.asJson)
    }

    "decode nested canonical dates and decimals" in {
      val json     = """{"values":[{"$date":{"$numberLong":"-1"}},{"amount":{"$numberDecimal":"12.50"}}]}"""
      val expected = Document(
        "values" -> BsonValue.array(
          BsonValue.instant(Instant.ofEpochMilli(-1L)),
          BsonValue.document("amount" -> BsonValue.bigDecimal(BigDecimal("12.50")))
        )
      )

      decode[Document](json) mustBe Right(expected)
    }

    "return decoding failures rather than throw for invalid nested wrappers" in
      List(
        """{"$date":1.5}""",
        """{"$date":9223372036854775808}""",
        """{"$date":{"$numberLong":"invalid"}}""",
        """{"$date":{"$numberLong":1}}""",
        """{"$date":0,"extra":true}""",
        """{"$numberDecimal":"NaN"}""",
        """{"$numberDecimal":"-0"}""",
        """{"$numberDecimal":"1E+6145"}""",
        """{"$numberDecimal":1}""",
        """{"$numberDecimal":"1","extra":true}"""
      ).foreach { wrapper =>
        List(s"""{"value":$wrapper}""", s"""{"values":[$wrapper]}""").foreach { json =>
          withClue(json)(decode[Document](json).isLeft mustBe true)
        }
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

      document.asJson.toString() mustBe json
      decode[Document](json).map(_.toString) mustBe Right(document.toString)
    }
  }
}
