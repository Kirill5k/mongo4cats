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

package mongo4cats.bson

import mongo4cats.bson.BsonPathSegment.{Field, Index}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

class BsonValueDecoderSpec extends AnyWordSpec with Matchers {
  private def failures[A](result: Either[BsonErrors, A]): Vector[BsonError] = result match {
    case Left(errors) => errors.errors
    case Right(value) => fail(s"Expected decoding failures but got $value")
  }

  "Diagnostic BSON decoders" should {
    "support SAM construction with structured results" in {
      val failure                           = BsonErrors(BsonError(BsonError.Kind.TypeMismatch, "Expected String"))
      val decoder: BsonValueDecoder[String] = value => value.asString.toRight(failure)
      decoder.decode(BsonValue.string("hello")) mustBe Right("hello")
      decoder.decode(BsonValue.int(1)) mustBe Left(failure)
    }

    "retain nonfatal decoder exceptions as causes" in {
      val cause   = new IllegalArgumentException("bad value")
      val decoder = BsonValueDecoder.fromEither[String](_ => throw cause)
      val error   = failures(decoder.decode(BsonValue.Null)).head
      error.kind mustBe BsonError.Kind.DecoderFailure
      error.cause mustBe Some(cause)
      error.message mustBe "bad value"
    }

    "describe expected and actual BSON types without converting their values" in {
      val error = failures(BsonValueDecoder.intDecoder.decode(BsonValue.string("42"))).head
      error.kind mustBe BsonError.Kind.TypeMismatch
      error.expected mustBe Some("Int32")
      error.actual mustBe Some("String")
      error.path mustBe Vector.empty
      error.render mustBe "Expected Int32 but found String at $"
    }

    "retain every built-in decoder's accepted values" in {
      val objectId = new ObjectId("507f1f77bcf86cd799439011")
      val instant  = Instant.parse("2022-01-01T00:00:00Z")
      val uuid     = UUID.fromString("cfbca728-4e39-4613-96bc-f920b5c37e16")
      BsonValueDecoder.objectIdDecoder.decode(BsonValue.objectId(objectId)) mustBe Right(objectId)
      BsonValueDecoder.intDecoder.decode(BsonValue.int(42)) mustBe Right(42)
      BsonValueDecoder.longDecoder.decode(BsonValue.long(42L)) mustBe Right(42L)
      BsonValueDecoder.longDecoder.decode(BsonValue.timestamp(42L)) mustBe Right(42L)
      BsonValueDecoder.stringDecoder.decode(BsonValue.string("hello")) mustBe Right("hello")
      BsonValueDecoder.dateTimeDecoder.decode(BsonValue.instant(instant)) mustBe Right(instant)
      BsonValueDecoder.dateTimeDecoder.decode(BsonValue.timestamp(42L)) mustBe Right(Instant.ofEpochSecond(42L))
      BsonValueDecoder.doubleDecoder.decode(BsonValue.double(1.25)) mustBe Right(1.25)
      BsonValueDecoder.booleanDecoder.decode(BsonValue.True) mustBe Right(true)
      BsonValueDecoder.documentDecoder.decode(BsonValue.document(Document.empty)) mustBe Right(Document.empty)
      BsonValueDecoder.bigDecimalDecoder.decode(BsonValue.bigDecimal(BigDecimal("1.25"))) mustBe Right(BigDecimal("1.25"))
      BsonValueDecoder.bigIntDecoder.decode(BsonValue.bigDecimal(BigDecimal("1.25"))) mustBe Right(BigInt(1))
      BsonValueDecoder.uuidDecoder.decode(BsonValue.uuid(uuid)) mustBe Right(uuid)
    }

    "accumulate list element errors in index order" in {
      val decoder = BsonValueDecoder.arrayListDecoder[Int]
      val value   = BsonValue.array(BsonValue.string("bad"), BsonValue.int(7), BsonValue.Null)
      val errors  = failures(decoder.decode(value))
      errors.map(_.path) mustBe Vector(Vector(Index(0)), Vector(Index(2)))
      errors.map(_.actual) mustBe Vector(Some("String"), Some("Null"))
      decoder.decode(BsonValue.array(BsonValue.int(1), BsonValue.int(2))) mustBe Right(List(1, 2))
      decoder.decode(BsonValue.array()) mustBe Right(Nil)
      failures(decoder.decode(BsonValue.int(1))).map(_.expected) mustBe Vector(Some("Array"))
    }

    "combine field decoders and accumulate missing and invalid siblings" in {
      val decoder = BsonValueDecoder.field[Int]("quantity").zip(BsonValueDecoder.field[String]("name"))
      val errors  = failures(decoder.decode(BsonValue.document("quantity" -> BsonValue.string("bad"))))
      errors.map(_.path) mustBe Vector(Vector(Field("quantity")), Vector(Field("name")))
      errors.map(_.kind) mustBe Vector(BsonError.Kind.TypeMismatch, BsonError.Kind.MissingField)
      decoder.decode(BsonValue.document("quantity" -> BsonValue.int(3), "name" -> BsonValue.string("book"))) mustBe Right((3, "book"))
    }

    "report one invalid parent container for composed fields" in {
      val decoder = BsonValueDecoder.field[Int]("a").zip(BsonValueDecoder.field[String]("b")).zip(BsonValueDecoder.field[Boolean]("c"))
      val errors  = failures(decoder.decode(BsonValue.array()))
      errors.size mustBe 1
      errors.head.path mustBe Vector.empty
      errors.head.expected mustBe Some("Document")
    }

    "report one invalid nested parent for independently composed field decoders" in {
      val decoder = BsonValueDecoder.intDecoder
        .field("a")
        .field("nested")
        .zip(BsonValueDecoder.stringDecoder.field("b").field("nested"))
      val errors = failures(decoder.decode(BsonValue.document("nested" -> BsonValue.Null)))

      errors.size mustBe 1
      errors.head.path mustBe Vector(Field("nested"))
      errors.head.kind mustBe BsonError.Kind.TypeMismatch
    }

    "retain nested field and index paths when decoders are composed" in {
      implicit val itemDecoder: BsonValueDecoder[(Int, String)] =
        BsonValueDecoder.field[Int]("quantity").zip(BsonValueDecoder.field[String]("name"))
      val value = BsonValue.document(
        "items" -> BsonValue.array(
          BsonValue.document("quantity" -> BsonValue.string("bad")),
          BsonValue.Null,
          BsonValue.document("quantity" -> BsonValue.int(3), "name" -> BsonValue.False)
        )
      )
      failures(BsonValueDecoder.field[List[(Int, String)]]("items").decode(value)).map(_.path) mustBe Vector(
        Vector(Field("items"), Index(0), Field("quantity")),
        Vector(Field("items"), Index(0), Field("name")),
        Vector(Field("items"), Index(1)),
        Vector(Field("items"), Index(2), Field("name"))
      )
    }

    "map decoded values and retain conversion failures" in {
      val decoder = BsonValueDecoder.stringDecoder.map(_.toInt).field("number")
      decoder.decode(BsonValue.document("number" -> BsonValue.string("42"))) mustBe Right(42)
      val error = failures(decoder.decode(BsonValue.document("number" -> BsonValue.string("wrong")))).head
      error.kind mustBe BsonError.Kind.DecoderFailure
      error.path mustBe Vector(Field("number"))
      error.cause.exists(_.isInstanceOf[NumberFormatException]) mustBe true
    }

    "retain custom decoder validation failures" in {
      val error   = BsonErrors(BsonError(BsonError.Kind.InvalidValue, "Must be positive"))
      val decoder = BsonValueDecoder.fromEither[Int] { value =>
        BsonValueDecoder.intDecoder.decode(value).flatMap(n => if (n > 0) Right(n) else Left(error))
      }
      decoder.decode(BsonValue.int(-1)) mustBe Left(error)
      decoder.decode(BsonValue.int(1)) mustBe Right(1)
    }

    "continue decoding independent branches after a custom decoder throws" in {
      val throwing = new BsonValueDecoder[Int] {
        override def decode(value: BsonValue): Either[BsonErrors, Int] = throw new IllegalStateException("failed")
      }
      val decoder = throwing.field("a").zip(BsonValueDecoder.field[String]("b"))
      val errors  = failures(decoder.decode(BsonValue.document("a" -> BsonValue.int(1), "b" -> BsonValue.False)))
      errors.map(_.path) mustBe Vector(Vector(Field("a")), Vector(Field("b")))
    }
  }

  "Document diagnostic getters" should {
    "derive convenient Option getters from the same decoder result" in {
      val document = Document("count" -> BsonValue.int(42), "nested" -> BsonValue.document("count" -> BsonValue.int(7)))
      document.getAs[Int]("count") mustBe document.getAsEither[Int]("count").toOption
      document.getAs[Int]("count") mustBe Some(42)
      document.getAs[String]("count") mustBe document.getAsEither[String]("count").toOption
      document.getAs[String]("count") mustBe None
      document.getAs[Int]("absent") mustBe None
      document.getNestedAs[Int]("nested.count") mustBe document.getNestedAsEither[Int]("nested.count").toOption
      document.getNestedAs[Int]("nested.count") mustBe Some(7)
      document.getNestedAs[String]("nested.count") mustBe None
      document.getNestedAs[Int]("nested.absent") mustBe None
    }

    "distinguish absent fields, null values, and type errors" in {
      val document = Document("null" -> BsonValue.Null, "number" -> BsonValue.string("42"))
      failures(document.getAsEither[Int]("absent")).head.kind mustBe BsonError.Kind.MissingField
      failures(document.getAsEither[Int]("null")).head.actual mustBe Some("Null")
      failures(document.getAsEither[Int]("number")).head.actual mustBe Some("String")
    }

    "preserve punctuation in exact field names and rendered paths" in {
      val name  = "a.b[0]\"\\\n"
      val error = failures(Document.empty.getAsEither[Int](name)).head
      error.path mustBe Vector(Field(name))
      error.renderPath mustBe "$[\"a.b[0]\\\"\\\\\\u000a\"]"
      failures(Document.empty.getAsEither[Int]("")).head.renderPath mustBe "$[\"\"]"
    }

    "locate nested parent failures and decode valid leaf values" in {
      val document = Document("a" -> BsonValue.document("b" -> BsonValue.document("value" -> BsonValue.int(42)), "wrong" -> BsonValue.Null))
      document.getNestedAsEither[Int]("a.b.value") mustBe Right(42)
      failures(document.getNestedAsEither[String]("a.b.value")).head.path mustBe Vector(Field("a"), Field("b"), Field("value"))
      failures(document.getNestedAsEither[Int]("a.wrong.value")).head.path mustBe Vector(Field("a"), Field("wrong"))
      failures(document.getNestedAsEither[Int]("a.missing.value")).head.path mustBe Vector(Field("a"), Field("missing"))
      failures(document.getNestedAsEither[Int]("missing.value")).head.path mustBe Vector(Field("missing"))
    }
  }

  "BsonErrors" should {
    "remain nonempty when prefixing and concatenating failures" in {
      val first  = BsonError(BsonError.Kind.MissingField, "Missing value", Vector(Field("a")))
      val second = BsonError(BsonError.Kind.InvalidValue, "Invalid value", Vector(Index(1)))
      val errors = (BsonErrors(first) ++ BsonErrors(second)).prepend(Field("parent"))
      errors.errors.map(_.renderPath) mustBe Vector("$.parent.a", "$.parent[1]")
      errors.getMessage mustBe "Missing value at $.parent.a; Invalid value at $.parent[1]"
    }
  }
}
