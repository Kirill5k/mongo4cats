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
import cats.data.Validated
import io.circe.generic.extras.auto._
import io.circe.disjunctionCodecs.encodeEither
import io.circe.disjunctionCodecs.decoderEither
import io.circe.disjunctionCodecs.encodeValidated
import io.circe.disjunctionCodecs.decodeValidated
import io.circe.syntax._
import mongo4cats.circe._
import mongo4cats.codecs.MongoCodecProvider
import mongo4cats.derivation.bson.AllBsonEncoders._
import mongo4cats.derivation.bson.AllBsonDecoders._
import mongo4cats.derivation.bson.configured.AutoDerivationTest.bsonConfigurationGen
import mongo4cats.derivation.bson.configured.TestSealedTrait.{CC1, CC2, CC3}
import mongo4cats.derivation.bson.configured.decoder.auto._
import mongo4cats.derivation.bson.configured.encoder.auto._
import mongo4cats.derivation.bson.{bsonDecoderContextSingleton, bsonEncoderContextSingleton, BsonDecoder, BsonEncoder, BsonValueOps}
import org.bson.{BsonBinaryReader, BsonBinaryWriter, BsonDocument, BsonDocumentWriter}
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries, CodecRegistry}
import org.bson.internal.ProvidersCodecRegistry
import org.bson.io.BasicOutputBuffer
import org.scalacheck.ScalacheckShapeless._
import org.scalacheck._
import org.scalacheck.cats.implicits._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import java.nio.ByteBuffer
import java.time.Instant
import java.util
import java.util.UUID

final case class RootTestData(
    testSealedTrait: TestSealedTrait,
    items: ItemTestDatas
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
    instantL: L[Instant]
)

sealed trait TestSealedTrait

object TestSealedTrait {
  final case object CObj1 extends TestSealedTrait
  final case object CObj2 extends TestSealedTrait

  final case class CC1(
      objId: org.bson.types.ObjectId,
      string: String,
      instant: Instant,
      map: Map[String, Int],
      mapTuple: Map[String, (Int, Long)],
      mapInstant: Map[String, (Int, java.time.Instant)]
  ) extends TestSealedTrait

  final case class CC2(
      uuid: UUID,
      int: Option[Int] = 3.some,
      long: Long,
      eitherIntString: Either[Int, String],
      validatedLongChar: Either[Long, Char]
  ) extends TestSealedTrait

  final case class CC3(
      char: Char,
      byte: Byte,
      short: Short,
      int: Option[Int] = 5.some,
      tuple2: (Long, Int),
      tuple2Opt: Option[(String, Int)],
      tuple2OptWithDefault: Option[(String, Int)] = ("ten", 10).some
  ) extends TestSealedTrait
}

// $ sbt "~+mongo4cats-bson-derivation/testOnly mongo4cats.derivation.bson.configured.AutoDerivationTest"
class AutoDerivationTest extends AnyWordSpec with ScalaCheckDrivenPropertyChecks {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 2000)

  implicit val instantArb: Arbitrary[Instant] =
    Arbitrary(Gen.choose(0, 1000000L).map(Instant.ofEpochSecond))

  implicit val objectIdArb: Arbitrary[org.bson.types.ObjectId] =
    Arbitrary((Gen.choose(0, 16777215), Gen.choose(0, 16777215)).mapN(new org.bson.types.ObjectId(_, _)))

  val rootTestDataGen: Gen[RootTestData] = Arbitrary.arbitrary[RootTestData]

  "Derived direct Encode/Decode ADT to org.bson.BsonValue with same result as using mongo4cats.circe with io.circe.generic.extras.auto" in {
    val outputBufferCirce      = new BasicOutputBuffer(10 * 1000 * 1000)
    val outputBufferDerivation = new BasicOutputBuffer(10 * 1000 * 1000)

    forAll(rootTestDataGen, bsonConfigurationGen, Gen.oneOf(false, true).label("dropNulls")) {
      case (testData, bsonConfiguration, dropNulls) =>
        implicit val bsonConf: mongo4cats.derivation.bson.configured.Configuration =
          bsonConfiguration

        implicit val circeConf: io.circe.generic.extras.Configuration =
          io.circe.generic.extras.Configuration(
            transformMemberNames = bsonConf.transformMemberNames,
            transformConstructorNames = bsonConf.transformConstructorNames,
            useDefaults = bsonConf.useDefaults,
            discriminator = bsonConf.discriminator
          )

        // --- Encode ---
        val expectedRight = testData.asRight[Throwable]

        // --- Circe ---
        val circeCodecProvider = mongo4cats.circe.deriveCirceCodecProvider[RootTestData].get
        val circeCodec   = circeCodecProvider.get(classOf[RootTestData], new ProvidersCodecRegistry(util.Arrays.asList(circeCodecProvider)))
        val circeJson    = testData.asJson
        val circeJsonStr = circeJson.noSpacesSortKeys

        // --- Bson ---
        val bsonEncoder = BsonEncoder[RootTestData]
        val bsonDoc     = new BsonDocument()
        val bsonWriter  = new BsonDocumentWriter(bsonDoc)
        val runEncoding = bsonEncoder.safeBsonEncode(bsonWriter, testData, bsonEncoderContextSingleton)
        runEncoding.leftMap(_.printStackTrace())
        assert(runEncoding.isRight, "--- 0) Encoding with BsonEncoder fail")
        val bsonStr       = bsonDoc.toJson()
        val parsedBsonStr = io.circe.parser.parse(bsonStr)
        assert(parsedBsonStr.isRight, "Unparsable bsonStr")
        val bsonStr2 = parsedBsonStr.toOption.get.noSpacesSortKeys
        val jsons = s"""|Bson: ${bsonStr2}
                        |Json: $circeJsonStr
                        |""".stripMargin

        assert(bsonStr2 == circeJsonStr, s", --- 1) Json String from Bson != Json String from Circe\n$jsons")

        // --- Decode ---
        val canDropNulls    = bsonConf.useDefaults || dropNulls
        val expected        = (if (canDropNulls) circeJson.deepDropNullValues else circeJson).as[RootTestData]
        val decodedFromBson = BsonDecoder.safeDecode[RootTestData](if (canDropNulls) bsonDoc.deepDropNullValues else bsonDoc)
        decodedFromBson.leftMap(_.printStackTrace())
        assert(decodedFromBson == expected, s", --- 2) Bson Decoder != Circe Decoder\n$jsons")

        // --- Binary ---
        val bytesCirce = ByteBuffer.wrap {
          outputBufferCirce.truncateToPosition(0)
          circeCodec.encode(new BsonBinaryWriter(outputBufferCirce), testData, bsonEncoderContextSingleton)
          outputBufferCirce.toByteArray
        }

        val bytesDerivation = ByteBuffer.wrap {
          outputBufferDerivation.truncateToPosition(0)
          val encoding = bsonEncoder.safeBsonEncode(new BsonBinaryWriter(outputBufferDerivation), testData, bsonEncoderContextSingleton)
          encoding.leftMap { ex =>
            println("------------ Can't Bson Encode -------------------")
            ex.printStackTrace()
            throw ex
          }
          outputBufferDerivation.toByteArray
        }

        {
          bytesCirce.position(0)
          val reader = new BsonBinaryReader(bytesCirce)
          reader.readBsonType()
          val decoded = Either.catchNonFatal(circeCodec.decode(reader, bsonDecoderContextSingleton))
          decoded.leftMap { ex =>
            println(s"--- 3) Can't Circe Decode\n$jsons")
            ex.printStackTrace()
            throw ex
          }
          assert(decoded == expectedRight, s", --- 4) Binary CirceEncoder/CirceDecoder\n$jsons")
        }

        {
          bytesDerivation.position(0)
          val reader = new BsonBinaryReader(bytesDerivation)
          reader.readBsonType()
          val decoded = BsonDecoder.safeDecode[RootTestData](reader)
          decoded.leftMap { ex =>
            println(s"--- 5) Can't BsonDecode\n$jsons")
            ex.printStackTrace()
            throw ex
          }
          assert(decoded == expectedRight, s", --- 6) Binary BsonEncoder/BsonDecoder\n$jsons")
        }

        {
          bytesCirce.position(0)
          val reader = new BsonBinaryReader(bytesCirce)
          reader.readBsonType()
          val decoded = BsonDecoder.safeDecode[RootTestData](reader)
          decoded.leftMap(_.printStackTrace())
          assert(decoded == expectedRight, s", --- 7) Binary CirceEncoder/BsonDecoder\n$jsons")
        }

        {
          bytesDerivation.position(0)
          val reader = new BsonBinaryReader(bytesDerivation)
          reader.readBsonType()
          val decoded = Either.catchNonFatal(circeCodec.decode(reader, bsonDecoderContextSingleton))
          assert(decoded == expectedRight, ", --- 8) Binary BsonEncoder/CirceDecoder")
        }
    }
  }
}

object AutoDerivationTest {

  val bsonConfigurationGen: Gen[mongo4cats.derivation.bson.configured.Configuration] = {
    val transformationGen: Gen[String => String] =
      Gen.oneOf(
        Seq(
          identity[String](_),
          mongo4cats.derivation.bson.configured.Configuration.kebabCaseTransformation,
          mongo4cats.derivation.bson.configured.Configuration.snakeCaseTransformation
        )
      )

    (
      transformationGen,
      transformationGen,
      Gen.oneOf(false, true).label("useDefaults"),
      Gen.option(Gen.stringOfN(5, Gen.alphaUpperChar)).label("discriminator"),
      Gen.oneOf(false, true).label("yoloWriterMode")
    ).mapN(mongo4cats.derivation.bson.configured.Configuration(_, _, _, _, _))
  }
}
