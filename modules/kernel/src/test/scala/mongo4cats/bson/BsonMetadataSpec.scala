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

import mongo4cats.codecs.DocumentCodecProvider
import org.bson.codecs.{DecoderContext, EncoderContext}
import org.bson.{
  BsonArray,
  BsonBinary,
  BsonDocument,
  BsonDocumentReader,
  BsonDocumentWriter,
  BsonInt32,
  BsonRegularExpression,
  BsonValue => JBsonValue,
  Document => JDocument
}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.regex.Pattern

class BsonMetadataSpec extends AnyWordSpec with Matchers {

  private val bytes        = Array[Byte](1, 2, 3, 4)
  private val subtypes     = List[Byte](0, 1, 2, 3, 5, 6, 0x80.toByte, 0xff.toByte)
  private val regexOptions = List("", "im", "ilmsux")

  private def nested(value: JBsonValue): BsonDocument =
    new BsonDocument("value", value)
      .append("nested", new BsonDocument("value", value))
      .append("array", new BsonArray(java.util.Arrays.asList[JBsonValue](value, new BsonDocument("value", value))))

  private def encode(document: Document): BsonDocument = {
    val result = new BsonDocument()
    DocumentCodecProvider.DefaultCodec.encode(new BsonDocumentWriter(result), document, EncoderContext.builder().build())
    result
  }

  private def decode(document: BsonDocument): Document =
    DocumentCodecProvider.DefaultCodec.decode(new BsonDocumentReader(document), DecoderContext.builder().build())

  "BSON binary values" should {
    "default to generic binary in the existing one-argument factories" in {
      BsonValue.binary(bytes).asJava mustBe new BsonBinary(bytes)
      BsonValue.BBinary(bytes).subtype mustBe 0.toByte
      BsonValueConverter.fromAny(bytes).asJava mustBe new BsonBinary(bytes)
    }

    "retain the subtype when converting Java BSON and legacy Binary values" in
      subtypes.foreach { subtype =>
        withClue(s"subtype $subtype: ") {
          val original  = new BsonBinary(subtype, bytes)
          val converted = BsonValueConverter.fromJava(original)

          converted mustBe BsonValue.binary(bytes.clone(), subtype)
          converted.asJava mustBe original
          BsonValueConverter.fromAny(new org.bson.types.Binary(subtype, bytes)).asJava mustBe original

          val javaDoc = new JDocument("value", new org.bson.types.Binary(subtype, bytes))
            .append("nested", new JDocument("value", new org.bson.types.Binary(subtype, bytes)))
            .append(
              "array",
              java.util.Arrays.asList[AnyRef](
                new org.bson.types.Binary(subtype, bytes),
                new JDocument("value", new org.bson.types.Binary(subtype, bytes))
              )
            )

          Document.fromJava(javaDoc).toBsonDocument mustBe nested(original)
          Document.fromJava(nested(original)).toBsonDocument mustBe nested(original)
        }
      }

    "preserve subtypes through document codecs and read-modify-write cycles" in
      subtypes.foreach { subtype =>
        withClue(s"subtype $subtype: ") {
          val original = nested(new BsonBinary(subtype, bytes))
          val result   = decode(original).add("after", BsonValue.int(42))

          encode(result) mustBe original.append("after", new BsonInt32(42))
        }
      }

    "preserve subtypes when parsing and serializing extended JSON" in
      subtypes.foreach { subtype =>
        withClue(s"subtype $subtype: ") {
          val original = nested(new BsonBinary(subtype, bytes))
          val result   = Document.parse(original.toJson).add("after", BsonValue.int(42))

          BsonDocument.parse(result.toJson) mustBe original.append("after", new BsonInt32(42))
        }
      }

    "compare byte contents and subtype and use matching hash codes" in {
      val first        = BsonValue.binary(bytes, 5.toByte)
      val equal        = BsonValue.binary(bytes.clone(), 5.toByte)
      val otherSubtype = BsonValue.binary(bytes.clone(), 0.toByte)
      val otherBytes   = BsonValue.binary(Array[Byte](1, 2, 3, 5), 5.toByte)

      (first == equal) mustBe true
      first.hashCode() mustBe equal.hashCode()
      (first == otherSubtype) mustBe false
      (first == otherBytes) mustBe false
      Set(first, equal, otherSubtype, otherBytes).size mustBe 3
      Map(first -> "found").get(equal) mustBe Some("found")
    }

    "use binary content equality in nested arrays and documents used as collection keys" in {
      def document(data: Array[Byte], subtype: Byte): Document =
        Document(
          "binary" -> BsonValue.binary(data, subtype),
          "array"  -> BsonValue.array(BsonValue.binary(data, subtype)),
          "nested" -> BsonValue.document("binary" -> BsonValue.binary(data, subtype))
        )

      val first     = document(bytes, 5.toByte)
      val equal     = document(bytes.clone(), 5.toByte)
      val different = document(bytes.clone(), 0.toByte)

      (first == equal) mustBe true
      first.hashCode() mustBe equal.hashCode()
      (first == different) mustBe false
      first.getList("array") mustBe equal.getList("array")
      Set(first, equal, different).size mustBe 2
      Map(first -> "found").get(equal) mustBe Some("found")
    }
  }

  "BSON regular expressions" should {
    "default to empty options in the existing one-argument factories" in {
      BsonValue.regex("^hello".r).asJava mustBe new BsonRegularExpression("^hello", "")
      BsonValue.BRegex("^hello".r).options mustBe ""
    }

    "retain options when converting Java BSON values, including nested values" in
      regexOptions.foreach { options =>
        withClue(s"options '$options': ") {
          val original  = new BsonRegularExpression("^hello.*", options)
          val converted = BsonValueConverter.fromJava(original)

          converted.asJava mustBe original
          converted.asInstanceOf[BsonValue.BRegex].options mustBe options
          BsonValue.regex("^hello.*".r, options).asJava mustBe original
          BsonValueConverter.fromAny(original).asJava mustBe original
          Document.fromJava(nested(original)).toBsonDocument mustBe nested(original)
        }
      }

    "retain Java Pattern flags when converting Java documents" in {
      val pattern  = Pattern.compile("^hello.*", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE)
      val expected = new BsonRegularExpression("^hello.*", "im")
      val javaDoc  = new JDocument("value", pattern)
        .append("nested", new JDocument("value", pattern))
        .append("array", java.util.Arrays.asList[AnyRef](pattern, new JDocument("value", pattern)))

      BsonValueConverter.fromAny(pattern).asJava mustBe expected
      Document.fromJava(javaDoc).toBsonDocument mustBe nested(expected)
    }

    "preserve options through document codecs and read-modify-write cycles" in
      regexOptions.foreach { options =>
        withClue(s"options '$options': ") {
          val original = nested(new BsonRegularExpression("^hello.*", options))
          val result   = decode(original).add("after", BsonValue.int(42))

          encode(result) mustBe original.append("after", new BsonInt32(42))
        }
      }

    "preserve options when parsing and serializing extended JSON" in
      regexOptions.foreach { options =>
        withClue(s"options '$options': ") {
          val original = nested(new BsonRegularExpression("^hello.*", options))
          val result   = Document.parse(original.toJson).add("after", BsonValue.int(42))

          BsonDocument.parse(result.toJson) mustBe original.append("after", new BsonInt32(42))
        }
      }
  }
}
