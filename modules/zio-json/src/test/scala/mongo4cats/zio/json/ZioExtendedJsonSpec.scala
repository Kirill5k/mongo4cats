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

import mongo4cats.bson._
import mongo4cats.bson.BsonPathSegment.{Field, Index}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.json._
import zio.json.ast.Json

class ZioExtendedJsonSpec extends AnyWordSpec with Matchers with MongoJsonCodecs {
  "ZioExtendedJson" should {
    "decode and round trip the shared canonical fixtures" in
      ExtendedJsonFixtures.canonical.foreach { case (json, expected) =>
        withClue(json) {
          ZioExtendedJson.parse(json).map(_.asJava) mustBe Right(expected.asJava)
          ZioExtendedJson.fromBson(expected, BsonJsonMode.Canonical).flatMap(ZioExtendedJson.toBson).map(_.asJava) mustBe
            Right(expected.asJava)
        }
      }

    "normalize decoded regex options in Scala values" in
      List("mi" -> "im", "xusmli" -> "ilmsux", "im" -> "im", "" -> "").foreach { case (options, expected) =>
        val json = Json.Obj("$regularExpression" -> Json.Obj("pattern" -> Json.Str("a"), "options" -> Json.Str(options)))
        List(ZioExtendedJson.toBson(json), ZioExtendedJson.parse(json.toJson)).foreach { decoded =>
          decoded.map {
            case BsonValue.BRegex(pattern, flags) => pattern.regex -> flags
            case value                            => fail(s"Expected BRegex, got $value")
          } mustBe Right("a" -> expected)
        }
      }

    "reject the shared malformed wrappers" in
      ExtendedJsonFixtures.invalid.foreach { json =>
        withClue(json)(ZioExtendedJson.parse(json).isLeft mustBe true)
      }

    "keep canonical and relaxed output explicit without changing the default codecs" in {
      val number = BsonValue.long(1L)

      ZioExtendedJson.fromBson(number, BsonJsonMode.Canonical) mustBe Right(Json.Obj("$numberLong" -> Json.Str("1")))
      ZioExtendedJson.fromBson(number, BsonJsonMode.Relaxed) mustBe Right(Json.Num(1))
      ZioJsonMapper.fromBson(number) mustBe Right(Json.Num(1))

      val decimal = BsonValue.bigDecimal(BigDecimal("12.50"))
      ZioExtendedJson.fromBson(decimal, BsonJsonMode.Relaxed) mustBe
        Right(Json.Obj("$numberDecimal" -> Json.Str("12.50")))
      ZioJsonMapper.fromBson(decimal) mustBe Right(Json.Num(BigDecimal("12.50")))
    }

    "preserve nested binary subtypes and regular expression options" in {
      val document = BsonValue.document(
        "values" -> BsonValue.array(
          BsonValue.binary(Array[Byte](1, 2, 3), 255.toByte),
          BsonValue.regex("a.*".r, "im")
        )
      )

      List(BsonJsonMode.Canonical, BsonJsonMode.Relaxed).foreach { mode =>
        ZioExtendedJson.fromBson(document, mode).flatMap(ZioExtendedJson.toBson).map(_.asJava) mustBe Right(document.asJava)
      }
    }

    "accumulate malformed sibling wrappers with field and index paths" in {
      val json   = """{"records":[{"$numberInt":"bad"},{"$numberLong":"bad"}],"a.b[0]":{"$oid":"bad"}}"""
      val errors = ZioExtendedJson.parse(json).left.toOption.get.errors

      errors.size mustBe 3
      errors(0).path.take(2) mustBe Vector(Field("records"), Index(0))
      errors(1).path.take(2) mustBe Vector(Field("records"), Index(1))
      errors(2).path.head mustBe Field("a.b[0]")
    }

    "report invalid syntax and reject trailing input" in {
      List("", "{", "{} garbage", "{} {}", "1]", "true false", "[1,]").foreach { json =>
        withClue(json) {
          val errors = ZioExtendedJson.parse(json).left.toOption.get.errors
          errors.size mustBe 1
          errors.head.kind mustBe BsonError.Kind.SyntaxError
        }
      }

      ZioExtendedJson.parse(" \n{}\t\r ") mustBe Right(BsonValue.document(Document.empty))
    }

    "preserve negative zero and exponent number tokens when parsing strings" in {
      ZioExtendedJson.parse("-0.0").map(_.asDouble.map(java.lang.Double.doubleToRawLongBits)) mustBe Right(Some(Long.MinValue))
      ZioExtendedJson.parse("1e0") mustBe Right(BsonValue.double(1.0))
      ZioExtendedJson.parse("1") mustBe Right(BsonValue.int(1))

      val errors = ZioExtendedJson.parse("""{"$timestamp":{"t":1e0,"i":0}}""").left.toOption.get.errors
      errors.map(_.path) mustBe Vector(Vector(Field("$timestamp"), Field("t")))
    }

    "accept bare numeric values with and without trailing whitespace" in {
      val numbers = List(
        "0"          -> BsonValue.int(0),
        "1"          -> BsonValue.int(1),
        "-2"         -> BsonValue.int(-2),
        "2147483648" -> BsonValue.long(2147483648L),
        "0.0"        -> BsonValue.double(0.0),
        "-0.0"       -> BsonValue.double(-0.0),
        "1.5"        -> BsonValue.double(1.5),
        "-2.5"       -> BsonValue.double(-2.5),
        "1e0"        -> BsonValue.double(1.0),
        "-1e0"       -> BsonValue.double(-1.0),
        "1E+2"       -> BsonValue.double(100.0),
        "-2E-2"      -> BsonValue.double(-0.02)
      )

      numbers.foreach { case (number, expected) =>
        List("", " ", "\n\t\r").foreach { suffix =>
          withClue(number + suffix)(ZioExtendedJson.parse(number + suffix).map(_.asJava) mustBe Right(expected.asJava))
        }
      }
    }

    "ignore numeric text in escaped strings when attaching number tokens" in {
      val json     = """{"text":"quoted \"123\" and slash \\ and 45","numbers":[-0.0,1e0,2],"nested":{"7":"text 8","value":-2.5E+2}}"""
      val expected = BsonValue.document(
        "text"    -> BsonValue.string("quoted \"123\" and slash \\ and 45"),
        "numbers" -> BsonValue.array(BsonValue.double(-0.0), BsonValue.double(1.0), BsonValue.int(2)),
        "nested"  -> BsonValue.document("7" -> BsonValue.string("text 8"), "value" -> BsonValue.double(-250.0))
      )

      ZioExtendedJson.parse(json).map(_.asJava) mustBe Right(expected.asJava)
    }

    "reject relaxed negative zero where the ZIO AST would lose its sign" in {
      val value  = BsonValue.document("numbers" -> BsonValue.array(BsonValue.double(0.0), BsonValue.double(-0.0)))
      val errors = ZioExtendedJson.fromBson(value, BsonJsonMode.Relaxed).left.toOption.get.errors

      errors.map(_.path) mustBe Vector(Vector(Field("numbers"), Index(1)))
      ZioExtendedJson.fromBson(value, BsonJsonMode.Canonical).flatMap(ZioExtendedJson.toBson).map(_.asJava) mustBe Right(value.asJava)
    }
  }

  "ZIO diagnostic BSON decoders" should {
    "retain native field and index paths without splitting punctuation in names" in {
      val decoder = deriveJsonBsonValueDecoder[Map[String, List[Int]]]
      val input   = BsonValue.document(
        "a.b[0]" -> BsonValue.array(BsonValue.int(1), BsonValue.string("bad")),
        "other"  -> BsonValue.array(BsonValue.string("also bad"))
      )
      val errors = decoder.decode(input).left.toOption.get.errors

      // Native ZIO JsonDecoder reports its first failure, even for products and collections.
      errors.size mustBe 1
      errors.head.kind mustBe BsonError.Kind.DecoderFailure
      errors.head.path mustBe Vector(Field("a.b[0]"), Index(1))
      errors.head.cause.isDefined mustBe true
      decoder.decode(input).toOption mustBe None
    }

    "retain the location of custom validation errors" in {
      val decoder = deriveJsonBsonValueDecoder[List[String]](
        JsonDecoder.list[String](JsonDecoder.string.mapOrFail(_ => Left("rejected by custom validation")))
      )
      val errors = decoder.decode(BsonValue.array(BsonValue.string("value"))).left.toOption.get.errors

      errors.head.path mustBe Vector(Index(0))
      errors.head.message must include("rejected by custom validation")
    }

    "report a custom decoder exception as a structured failure" in {
      val failure = new IllegalArgumentException("custom decoder failed")
      val decoder = deriveJsonBsonValueDecoder[String](JsonDecoder.string.map(_ => throw failure))
      val errors  = decoder.decode(BsonValue.string("value")).left.toOption.get.errors

      errors.head.kind mustBe BsonError.Kind.DecoderFailure
      errors.head.cause mustBe Some(failure)
      errors.head.path mustBe Vector.empty
    }

    "accumulate legacy mapper failures before invoking the native decoder" in {
      val input = BsonValue.document(
        "nested" -> BsonValue.array(BsonValue.timestamp(1L), BsonValue.MinKey),
        "a.b[0]" -> BsonValue.regex("a".r, "i")
      )
      val errors = deriveJsonBsonValueDecoder[Json].decode(input).left.toOption.get.errors

      errors.map(_.path) mustBe Vector(Vector(Field("nested"), Index(0)), Vector(Field("nested"), Index(1)), Vector(Field("a.b[0]")))
      errors.map(_.kind).distinct mustBe Vector(BsonError.Kind.UnsupportedType)
    }

    "keep convenient Option access and undefined field omission" in {
      val decoder = deriveJsonBsonValueDecoder[Map[String, Int]]
      val input   = BsonValue.document("value" -> BsonValue.int(1), "omitted" -> BsonValue.Undefined)

      Document("nested" -> input).getAs[Map[String, Int]]("nested")(decoder) mustBe Some(Map("value" -> 1))
      decoder.decode(input) mustBe Right(Map("value" -> 1))
    }
  }
}
