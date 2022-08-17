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

package mongo4cats.derivation.bson.configured

import cats.syntax.all._
import io.circe.{Decoder, Json, ParsingFailure}
import io.circe.generic.auto._
import io.circe.syntax._
import mongo4cats.circe._
import mongo4cats.derivation.bson.AllBsonEncoders._
import mongo4cats.derivation.bson.AllBsonDecoders._
import mongo4cats.derivation.bson.derivation.decoder.auto._
import mongo4cats.derivation.bson.derivation.decoder.auto.autoDerived
import mongo4cats.derivation.bson.derivation.encoder.auto._
import mongo4cats.derivation.bson.derivation.encoder.auto.autoDerived
import mongo4cats.derivation.bson.{BsonDecoder, BsonEncoder, BsonValueOps}
import mongo4cats.derivation.bson.configured.TestSealedTrait.CObj1
import mongo4cats.derivation.bson.configured.TestSealedTrait.CObj2
import mongo4cats.derivation.bson.configured.TestSealedTrait.CC1
import mongo4cats.derivation.bson.configured.TestSealedTrait.CC2
import mongo4cats.derivation.bson.configured.TestSealedTrait.CC3
import org.bson.BsonDocument
import _root_.org.scalacheck._
import _root_.org.scalacheck.Gen._
import _root_.org.scalacheck.Arbitrary._
import _root_.org.scalacheck.Arbitrary.arbitrary
import _root_.org.scalacheck.cats.implicits._
import _root_.org.scalatest.wordspec.AnyWordSpec
import _root_.org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import java.util.UUID

final case class RootTestData(
    int: Int,
    string: String,
    // testSealedTraits: List[TestSealedTrait],
    // items: List[ItemTestDatas],
    rootTuple2: (String, Int)
)

final case class ItemTestDatas(
    listItems: List[ItemTestData],
    setItems: Set[ItemTestData],
    optionItem: Option[ItemTestData]
)

final case class ItemTestData(
    idData: TestData[_root_.cats.Id],
    listData: TestData[List],
    setData: TestData[Set],
    optionData: TestData[Option]
)

final case class TestData[L[_]](
    stringL: L[String],
    byteL: L[Byte],
    shortL: L[Short],
    intL: L[Int],
    longL: L[Long],
    instantL: L[java.time.Instant]
)

sealed trait TestSealedTrait

object TestSealedTrait {
  case object CObj1 extends TestSealedTrait
  case object CObj2 extends TestSealedTrait
  final case class CC1(
      objId: org.bson.types.ObjectId,
      string: String,
      instant: java.time.Instant
      // map: Map[String, (Int, java.time.Instant)]
  ) extends TestSealedTrait
  final case class CC2(
      uuid: UUID,
      int: Option[Int] = 3.some,
      long: Long
  ) extends TestSealedTrait
  final case class CC3(
      byte: Byte,
      short: Short,
      int: Option[Int] = 5.some,
      tuple2: (Long, Int),
      tuple2Opt: Option[(String, Int)],
      tuple2OptWithDefault: Option[(String, Int)] = ("ten", 10).some
  ) extends TestSealedTrait
}

class AutoDerivationTest extends AnyWordSpec with ScalaCheckDrivenPropertyChecks {

  implicit val objectIdArb: Arbitrary[org.bson.types.ObjectId] =
    Arbitrary((Gen.choose(0, 16777215), Gen.choose(0, 16777215)).mapN(new org.bson.types.ObjectId(_, _)))

  implicit val shortStringArb: Arbitrary[String] =
    Arbitrary(Gen.choose(0, 5).flatMap(Gen.stringOfN(_, Gen.alphaChar)))

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 2000)

  "Derived direct Encode/Decode ADT to org.bson.BsonValue with same result as using mongo4cats.circe" in {

    val rootTestDataGen: Gen[RootTestData] =
      (
        arbitrary[Int],
        arbitrary[String],
        // Gen.listOf(
        //  Gen.oneOf(
        //    Gen.const(CObj1),
        //    Gen.const(CObj2),
        //    (
        //      arbitrary[org.bson.types.ObjectId],
        //      arbitrary[String],
        //      arbitrary[java.time.Instant]
        //      // arbitrary[Map[String, (Int, java.time.Instant)]]
        //    ).mapN(CC1.apply),
        //    (
        //      arbitrary[UUID],
        //      Gen.option(arbitrary[Int]),
        //      arbitrary[Long]
        //    ).mapN(CC2.apply),
        //    (
        //      arbitrary[Byte],
        //      arbitrary[Short],
        //      Gen.option(arbitrary[Int]),
        //      (arbitrary[Long], arbitrary[Int]).tupled,
        //      Gen.option((arbitrary[String], arbitrary[Int]).tupled),
        //      Gen.option((arbitrary[String], arbitrary[Int]).tupled)
        //    ).mapN(CC3.apply)
        //  )
        // ),
        (arbitrary[String], arbitrary[Int]).tupled
      ).mapN(RootTestData.apply)

    forAll(rootTestDataGen, Gen.oneOf(false, true)) { case (testData, dropNulls) =>
      // --- Encode ---
      val circeJson: Json       = testData.asJson
      val circeJsonStr: String  = circeJson.noSpaces
      val bsonDoc: BsonDocument = BsonEncoder[RootTestData].apply(testData).asInstanceOf[BsonDocument]
      val bsonStr: String       = bsonDoc.toJson().replace("\": ", "\":").replace(", ", ",")
      assert(
        bsonStr == circeJsonStr,
        s"""|, 1) Json String from Bson != Json String from Circe
            |Bson: $bsonStr
            |Json: $circeJsonStr""".stripMargin
      )

      // --- Decode ---
      val jsonFromBsonStr: Either[ParsingFailure, Json] = io.circe.parser.parse(bsonStr)
      assert(jsonFromBsonStr.isRight, ", 2) BsonStr Can't be Circe Json Decoded")
      assert(jsonFromBsonStr == circeJson.asRight, ", 3) BsonStr Circe Json Decoded != Circe Json Encoded")

      val expected: Decoder.Result[RootTestData] =
        (if (dropNulls) circeJson.deepDropNullValues else circeJson).as[RootTestData]
      val decodedFromBson: BsonDecoder.Result[RootTestData] =
        BsonDecoder[RootTestData].apply(if (dropNulls) bsonDoc.deepDropNullValues else bsonDoc)
      assert(decodedFromBson == expected, ", 4) Bson Decoder != Circe Decoder")
    }
  }
}
