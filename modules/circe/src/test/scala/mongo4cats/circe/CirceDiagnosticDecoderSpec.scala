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

import cats.syntax.apply._
import io.circe.{CursorOp, Decoder, DecodingFailure, Json}
import mongo4cats.bson._
import mongo4cats.bson.BsonPathSegment.{Field, Index}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CirceDiagnosticDecoderSpec extends AnyWordSpec with Matchers with MongoJsonCodecs {
  "Circe diagnostic BSON decoders" should {
    "collect field and array failures in native decoder order" in {
      val native  = (Decoder[Int].at("a.b[0]"), Decoder[List[Int]].at("items"), Decoder[Boolean].at("missing")).tupled
      val decoder = deriveJsonBsonValueDecoder(native)
      val bson    = BsonValue.document(
        "a.b[0]" -> BsonValue.string("wrong"),
        "items"  -> BsonValue.array(BsonValue.string("wrong"), BsonValue.int(1), BsonValue.False)
      )
      val errors = decoder.decode(bson).swap.toOption.get.errors

      errors.map(_.path) mustBe Vector(
        Vector(Field("a.b[0]")),
        Vector(Field("items"), Index(0)),
        Vector(Field("items"), Index(2)),
        Vector(Field("missing"))
      )
      errors.last.kind mustBe BsonError.Kind.MissingField
      errors.forall(_.cause.nonEmpty) mustBe true
      decoder.decode(bson).toOption mustBe None
    }

    "report native type expectations without reconstructing custom error paths" in {
      val decoder = deriveJsonBsonValueDecoder(Decoder[String].at("value"))
      val errors  = decoder.decode(BsonValue.document("value" -> BsonValue.int(1))).swap.toOption.get

      errors.head.kind mustBe BsonError.Kind.TypeMismatch
      errors.head.expected.nonEmpty mustBe true
      errors.head.actual mustBe Some("Number")
      errors.head.path mustBe Vector(Field("value"))

      val custom = deriveJsonBsonValueDecoder(Decoder.instance[String](_ => Left(DecodingFailure("custom", Nil))))
      custom.decode(BsonValue.Null).swap.toOption.get.head.path mustBe Vector.empty
    }

    "return a structured failure when a custom native decoder throws" in {
      val failure = new IllegalArgumentException("custom decoder failed")
      val native  = Decoder.instance[String](_ => throw failure).at("inner")
      val decoder = deriveJsonBsonValueDecoder(native)
      val input   = BsonValue.document("inner" -> BsonValue.string("value"))
      val error   = decoder.decode(input).swap.toOption.get.head

      error.kind mustBe BsonError.Kind.DecoderFailure
      error.message mustBe "custom decoder failed"
      error.cause mustBe Some(failure)
      error.path mustBe Vector.empty
      Document("payload" -> input).getAsEither[String]("payload")(decoder).swap.toOption.get.head.path mustBe
        Vector(Field("payload"))
    }

    "collect unsupported BSON mapping failures with their complete paths" in {
      val decoder = deriveJsonBsonValueDecoder(Decoder.decodeJson)
      val bson    = BsonValue.document(
        "ignored" -> BsonValue.Undefined,
        "a.b[0]"  -> BsonValue.timestamp(1),
        "items"   -> BsonValue.array(BsonValue.double(Double.NaN), BsonValue.document("regex" -> BsonValue.regex("x".r)))
      )
      val errors = decoder.decode(bson).swap.toOption.get.errors

      errors.map(_.path) mustBe Vector(
        Vector(Field("a.b[0]")),
        Vector(Field("items"), Index(0)),
        Vector(Field("items"), Index(1), Field("regex"))
      )
      errors.map(_.kind) mustBe Vector(BsonError.Kind.UnsupportedType, BsonError.Kind.InvalidValue, BsonError.Kind.UnsupportedType)
    }

    "retain domain JSON representations and omit undefined document fields" in {
      val decoder = deriveJsonBsonValueDecoder(Decoder.decodeJson)
      val bson    = BsonValue.document(
        "long"    -> BsonValue.long(1),
        "decimal" -> BsonValue.bigDecimal(BigDecimal("1.5")),
        "ignored" -> BsonValue.Undefined
      )
      val expected = Json.obj("long" -> Json.fromLong(1), "decimal" -> Json.fromBigDecimal(BigDecimal("1.5")))

      decoder.decode(bson) mustBe Right(expected)
      Document("value" -> bson).getAs[Json]("value") mustBe Some(expected)
    }

    "reduce native cursor navigation to typed path segments" in {
      val operations = List(
        CursorOp.DownField("old"),
        CursorOp.Field("array"),
        CursorOp.DownN(4),
        CursorOp.MoveLeft,
        CursorOp.MoveRight,
        CursorOp.DownField("remove"),
        CursorOp.DeleteGoParent,
        CursorOp.MoveUp,
        CursorOp.Field("a.b"),
        CursorOp.DownArray,
        CursorOp.MoveRight
      )
      CirceDiagnosticDecoder.path(operations.reverse) mustBe Vector(Field("a.b"), Index(1))
      CirceDiagnosticDecoder.path(List(CursorOp.MoveUp)) mustBe Vector.empty
    }
  }
}
