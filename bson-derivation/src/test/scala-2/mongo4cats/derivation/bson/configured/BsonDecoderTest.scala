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
import io.circe.Decoder.Result
import io.circe.generic.extras.auto._
import io.circe.{Decoder, DecodingFailure, Encoder, Json, ParsingFailure}
import io.circe.syntax._
import mongo4cats.circe._
import mongo4cats.codecs.MongoCodecProvider
import mongo4cats.derivation.bson.AllBsonEncoders._
import mongo4cats.derivation.bson.AllBsonDecoders._
import mongo4cats.derivation.bson.configured.AutoDerivationTest.bsonConfigurationGen
import mongo4cats.derivation.bson.configured.decoder.auto._
import mongo4cats.derivation.bson.configured.encoder.auto._
import mongo4cats.derivation.bson.{
  bsonDecoderContextSingleton,
  bsonEncoderContextSingleton,
  BsonDecoder,
  BsonDocumentEncoder,
  BsonEncoder,
  BsonValueOps
}
import org.bson.{BsonBinaryReader, BsonBinaryWriter, BsonDocument, BsonDocumentWriter}
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries, CodecRegistry}
import org.bson.internal.ProvidersCodecRegistry
import org.bson.io.BasicOutputBuffer
import org.scalacheck.ScalacheckShapeless._
import org.scalacheck._
import org.scalacheck.Gen._
import org.scalacheck.cats.implicits._
import org.scalatest._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import scala.util.Try

class BsonDecoderTest extends AnyFreeSpec with ScalaCheckDrivenPropertyChecks {

  implicit override val generatorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration(minSuccessful = 2000)

  "BsonDecoder should acts more or less like Circe Decoder for operator" - {

    final case class DocFor[A](value: A)

    def check[IN_JSON, A](
        circeDecoder: Decoder[A],
        bsonDecoder: BsonDecoder[A],
        f: A => IN_JSON,
        leftCheck: (DecodingFailure, Throwable) => Any = (_, _) => ()
    )(implicit
        encA: Encoder[IN_JSON],
        bsencA: BsonEncoder[IN_JSON],
        genA: Gen[IN_JSON]
    ): Assertion =
      forAll(genA, bsonConfigurationGen, Gen.oneOf(false, true).label("dropNulls")) { case (a, bsonConfiguration, dropNulls) =>
        implicit val bsonConf: mongo4cats.derivation.bson.configured.Configuration =
          bsonConfiguration

        implicit val circeConf: io.circe.generic.extras.Configuration =
          io.circe.generic.extras.Configuration(
            transformMemberNames = bsonConf.transformMemberNames,
            transformConstructorNames = bsonConf.transformConstructorNames,
            useDefaults = bsonConf.useDefaults,
            discriminator = bsonConf.discriminator
          )

        val doc     = DocFor(a)
        val jsonDoc = if (dropNulls) doc.asJson.deepDropNullValues else doc.asJson
        val bsonDoc = {
          val tmp =
            BsonEncoder.unsafeEncodeAsBsonDoc(doc)(BsonEncoder[DocFor[IN_JSON]].asInstanceOf[BsonDocumentEncoder[DocFor[IN_JSON]]])
          if (dropNulls) tmp.deepDropNullValues else tmp
        }

        val circeDecoded: Decoder.Result[DocFor[A]] = {
          implicit val circeDecA = circeDecoder
          Decoder[DocFor[A]].decodeJson(jsonDoc)
        }
        val bsonDecoded: BsonDecoder.Result[DocFor[A]] = {
          implicit val bsonDecA = bsonDecoder
          BsonDecoder.safeDecode(bsonDoc)(BsonDecoder[DocFor[A]])
        }

        if (circeDecoded.isRight != bsonDecoded.isRight)
          println(s"""|circe: $circeDecoded
                      | bson: $bsonDecoded
                      | --------------------------""".stripMargin)
        // else println(s"circe: $circeDecoded / bson: $bsonDecoded")

        (circeDecoded, bsonDecoded).mapN { case (a, b) => assert(a == b) }
        (circeDecoded.swap, bsonDecoded.swap).mapN(leftCheck(_, _))
        assert(circeDecoded.isRight == bsonDecoded.isRight)
      }

    "ensure" in {
      implicit val intGen = Gen.choose(Int.MinValue, Int.MaxValue)
      check[Int, Int](
        Decoder[Int].ensure(_ % 2 == 0, "Not even"),
        BsonDecoder[Int].ensure(_ % 2 == 0, "Not even"),
        identity
      )
    }

    "or" in {
      implicit val intGen = Gen.choose(-1000, 1000)
      implicit val strGen = Gen.stringOfN(3, Gen.oneOf(Gen.choose('0', '9'), Gen.choose('a', 'z')))
      check[String, Int](
        Decoder[String].emapTry(s => Try(s.toInt)).or(Decoder.const(-1)),
        BsonDecoder[String].emap(s => Either.catchNonFatal(s.toInt)).or(BsonDecoder.const(-1)),
        _.toString + "123"
      )
    }

    "either" in {
      implicit val intGen = Gen.choose(-1000, 1000)
      implicit val strGen = Gen.stringOfN(3, Gen.asciiPrintableChar)
      check[String, Either[Int, String]](
        Decoder[String].emapTry(s => Try(s.toInt)).either(Decoder[String]),
        BsonDecoder[String].emapTry(s => Try(s.toInt)).either(BsonDecoder[String]),
        _.toString + "123"
      )
    }

    "product" in {
      implicit val intGen = Gen.choose(-1000, 1000)
      implicit val strGen = Gen.stringOfN(3, Gen.asciiPrintableChar)
      check[String, (String, String)](
        Decoder[String].map(_.toUpperCase).product(Decoder[String]),
        BsonDecoder[String].map(_.toUpperCase).product(BsonDecoder[String]),
        _._1
      )
    }

    "withErrorMessage" in {
      implicit val intGen = Gen.choose(-1000, 1000)
      implicit val strGen = Gen.stringOfN(3, Gen.oneOf(Gen.choose('0', '9'), Gen.choose('a', 'z')))
      val msg             = "Not an Int"
      check[String, Int](
        Decoder[String].emapTry(s => Try(s.toInt)).withErrorMessage(msg),
        BsonDecoder[String].emapTry(s => Try(s.toInt)).withErrorMessage(msg),
        _.toString + "123",
        (decodingFailure, ex) => assert(ex.getMessage == decodingFailure.message)
      )
    }
  }
}
