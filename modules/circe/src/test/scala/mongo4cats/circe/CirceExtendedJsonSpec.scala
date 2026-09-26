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

import io.circe.{parser, Json}
import mongo4cats.bson._
import mongo4cats.bson.BsonPathSegment.{Field, Index}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

class CirceExtendedJsonSpec extends AnyWordSpec with Matchers {
  private val canonicalValues = Vector[BsonValue](
    BsonValue.Null,
    BsonValue.Undefined,
    BsonValue.MinKey,
    BsonValue.MaxKey,
    BsonValue.True,
    BsonValue.string("text"),
    BsonValue.int(Int.MinValue),
    BsonValue.int(Int.MaxValue),
    BsonValue.long(Long.MinValue),
    BsonValue.long(Long.MaxValue),
    BsonValue.long(1),
    BsonValue.double(1.5),
    BsonValue.double(-0.0),
    BsonValue.double(Double.NaN),
    BsonValue.double(Double.PositiveInfinity),
    BsonValue.double(Double.NegativeInfinity),
    BsonValue.bigDecimal(BigDecimal("123.4500")),
    BsonValue.bigDecimal(BigDecimal("1E-6176")),
    BsonValue.objectId(ObjectId("507f1f77bcf86cd799439011")),
    BsonValue.instant(Instant.ofEpochMilli(Long.MinValue)),
    BsonValue.instant(Instant.ofEpochMilli(Long.MaxValue)),
    BsonValue.instant(Instant.parse("2022-01-01T00:00:00.001Z")),
    BsonValue.timestamp(4294967295L, -1),
    BsonValue.regex("a.*".r, "im"),
    BsonValue.binary(Array[Byte](1, 2, 3), 0xff.toByte),
    BsonValue.uuid(UUID.fromString("cfbca728-4e39-4613-96bc-f920b5c37e16"))
  )

  "Circe Extended JSON" should {
    "accept the shared BSON corpus fixtures and preserve their canonical values" in {
      ExtendedJsonFixtures.canonical.foreach { case (input, expected) =>
        withClue(input) {
          CirceExtendedJson.parse(input).map(_.asJava) mustBe Right(expected.asJava)
          val json = CirceExtendedJson.fromBson(expected, BsonJsonMode.Canonical).toOption.get
          CirceExtendedJson.toBson(json).map(_.asJava) mustBe Right(expected.asJava)
        }
      }
      ExtendedJsonFixtures.invalid.foreach { input =>
        withClue(input)(CirceExtendedJson.parse(input).isLeft mustBe true)
      }
    }

    "normalize decoded regex options in Scala values" in
      List("mi" -> "im", "xusmli" -> "ilmsux", "im" -> "im", "" -> "").foreach { case (options, expected) =>
        val json = Json.obj("$regularExpression" -> Json.obj("pattern" -> Json.fromString("a"), "options" -> Json.fromString(options)))
        List(CirceExtendedJson.toBson(json), CirceExtendedJson.parse(json.noSpaces)).foreach { decoded =>
          decoded.map {
            case BsonValue.BRegex(pattern, flags) => pattern.regex -> flags
            case value                            => fail(s"Expected BRegex, got $value")
          } mustBe Right("a" -> expected)
        }
      }

    "preserve all supported BSON types and values through canonical text" in {
      canonicalValues.foreach { bson =>
        withClue(bson.toString) {
          val json = CirceExtendedJson.fromBson(bson, BsonJsonMode.Canonical).toOption.get
          CirceExtendedJson.parse(json.noSpaces).map(_.asJava) mustBe Right(bson.asJava)
        }
      }
      val nested = BsonValue.document("values" -> BsonValue.array(canonicalValues), "undefined" -> BsonValue.Undefined)
      val json   = CirceExtendedJson.fromBson(nested, BsonJsonMode.Canonical).toOption.get
      CirceExtendedJson.toBson(json).map(_.asJava) mustBe Right(nested.asJava)
      json.hcursor.downField("undefined").focus.nonEmpty mustBe true
    }

    "require an explicit output mode and keep decimal wrappers in relaxed JSON" in {
      val bson      = BsonValue.document("long" -> BsonValue.long(1), "decimal" -> BsonValue.bigDecimal(BigDecimal("1.5")))
      val canonical = CirceExtendedJson.fromBson(bson, BsonJsonMode.Canonical).toOption.get
      val relaxed   = CirceExtendedJson.fromBson(bson, BsonJsonMode.Relaxed).toOption.get

      canonical.hcursor.downField("long").focus mustBe Some(Json.obj("$numberLong" -> Json.fromString("1")))
      relaxed.hcursor.downField("long").focus mustBe Some(Json.fromInt(1))
      relaxed.hcursor.downField("decimal").focus mustBe Some(Json.obj("$numberDecimal" -> Json.fromString("1.5")))
      CirceExtendedJson.toBson(relaxed).toOption.get.asDocument.get.get("long") mustBe Some(BsonValue.int(1))
    }

    "use canonical dates outside the relaxed range" in {
      List(Long.MinValue, -1L, 253402300800000L, Long.MaxValue).foreach { millis =>
        CirceExtendedJson.fromBson(BsonValue.instant(Instant.ofEpochMilli(millis)), BsonJsonMode.Relaxed) mustBe Right(
          Json.obj("$date" -> Json.obj("$numberLong" -> Json.fromString(millis.toString)))
        )
      }
      CirceExtendedJson
        .fromBson(BsonValue.instant(Instant.EPOCH), BsonJsonMode.Relaxed)
        .toOption
        .get
        .hcursor
        .downField("$date")
        .as[String]
        .isRight mustBe true
    }

    "collect malformed siblings with field and index paths" in {
      val json =
        parser.parse("""{"a.b[0]":{"$numberInt":"bad"},"items":[{"$oid":"bad"},{"$numberLong":"9223372036854775808"}]}""").toOption.get
      val errors = CirceExtendedJson.toBson(json).swap.toOption.get.errors

      errors.size mustBe 3
      errors.map(_.path) mustBe Vector(
        Vector(Field("a.b[0]"), Field("$numberInt")),
        Vector(Field("items"), Index(0), Field("$oid")),
        Vector(Field("items"), Index(1), Field("$numberLong"))
      )
    }

    "preserve double numeric tokens and signed zero in the Circe AST" in {
      CirceExtendedJson.parse("1") mustBe Right(BsonValue.int(1))
      List("1.0", "1e0", "1E+0").foreach { input =>
        CirceExtendedJson.parse(input) mustBe Right(BsonValue.double(1.0))
      }
      val zero = BsonValue.double(-0.0)
      val json = CirceExtendedJson.fromBson(zero, BsonJsonMode.Relaxed).toOption.get
      json.noSpaces mustBe "-0.0"
      CirceExtendedJson.toBson(json).map(_.asJava) mustBe Right(zero.asJava)
      CirceExtendedJson.toBson(Json.fromBigDecimal(BigDecimal("1.0"))) mustBe Right(BsonValue.double(1.0))
    }

    "accumulate independent wrapper payload failures" in {
      val errors = CirceExtendedJson.parse("""{"$binary":{"base64":"!","subType":"wrong"}}""").swap.toOption.get.errors
      errors.map(_.path) mustBe Vector(
        Vector(Field("$binary"), Field("base64")),
        Vector(Field("$binary"), Field("subType"))
      )
    }

    "reject unrepresentable values and ambiguous documents" in {
      List("NaN", "Infinity", "-Infinity", "-0", "1E-6177").foreach { value =>
        CirceExtendedJson.toBson(Json.obj("$numberDecimal" -> Json.fromString(value))).isLeft mustBe true
      }
      CirceExtendedJson
        .fromBson(BsonValue.instant(Instant.parse("2022-01-01T00:00:00.000001Z")), BsonJsonMode.Canonical)
        .isLeft mustBe true
      CirceExtendedJson.fromBson(BsonValue.document("$numberInt" -> BsonValue.string("1")), BsonJsonMode.Canonical).isLeft mustBe true
      CirceExtendedJson.parse("""{"$date":"2022-01-01T00:00:00.000001Z"}""").isLeft mustBe true
    }

    "reject duplicate keys before the Circe AST would discard them" in {
      List(
        """{"value":1,"value":2}""",
        """{"nested":[{"$numberInt":"1","$numberInt":"2"}]}""",
        """{"$binary":{"base64":"AA==","base64":"AQ==","subType":"00"}}"""
      ).foreach { input =>
        val error = CirceExtendedJson.parse(input).swap.toOption.get.head
        error.kind mustBe BsonError.Kind.SyntaxError
        error.message.nonEmpty mustBe true
      }
      // The opt-in parser does not change Circe's ordinary parsing behavior.
      parser.parse("""{"value":1,"value":2}""").isRight mustBe true
    }

    "retain the syntax parser's message, cause and available location" in {
      val error = CirceExtendedJson.parse("{\n \"broken\": ]}").swap.toOption.get.head
      error.kind mustBe BsonError.Kind.SyntaxError
      error.path mustBe Vector.empty
      error.message.nonEmpty mustBe true
      error.cause.nonEmpty mustBe true
    }
  }
}
