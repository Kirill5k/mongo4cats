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

import mongo4cats.bson.BsonValue.BBinary
import org.bson.BsonBinary
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.immutable.{HashMap, HashSet}

class BinaryValueSpec extends AnyWordSpec with Matchers {

  private def document(binary: BsonValue): Document =
    Document(
      "binary" -> binary,
      "array"  -> BsonValue.array(binary, BsonValue.document("binary" -> binary)),
      "nested" -> BsonValue.document("array" -> BsonValue.array(BsonValue.array(binary)))
    )

  "BSON binary values" should {
    "compare byte contents and subtype consistently in hash collections" in {
      List(Array.emptyByteArray, Array[Byte](1, -1, 0, 127)).foreach { bytes =>
        val first        = BBinary(bytes, 0x80.toByte)
        val equal        = BBinary(bytes.clone(), 0x80.toByte)
        val otherSubtype = BBinary(bytes.clone(), 0.toByte)
        val otherBytes   = BBinary(bytes ++ Array[Byte](1), 0x80.toByte)

        (first == equal) mustBe true
        (equal == first) mustBe true
        first.hashCode() mustBe equal.hashCode()
        (first == otherSubtype) mustBe false
        (first == otherBytes) mustBe false
        first.equals(null) mustBe false
        first.equals(BsonValue.int(1)) mustBe false
        HashSet(first, equal, otherSubtype, otherBytes).size mustBe 3
        HashMap(first -> "found").get(equal) mustBe Some("found")
      }
    }

    "give nested arrays and documents matching equality and hash codes" in {
      val firstBinary = BBinary(Array[Byte](1, 2, 3), 5.toByte)
      val equalBinary = BBinary(Array[Byte](1, 2, 3), 5.toByte)
      val otherBinary = BBinary(Array[Byte](1, 2, 3), 0.toByte)

      val structures = List[(String, BsonValue => Any)](
        "document" -> ((binary: BsonValue) => document(binary)),
        "array"    -> ((binary: BsonValue) => BsonValue.array(binary, BsonValue.document(document(binary)))),
        "value"    -> ((binary: BsonValue) => BsonValue.document(document(binary)))
      )

      structures.foreach { case (name, build) =>
        withClue(s"$name: ") {
          val first     = build(firstBinary)
          val equal     = build(equalBinary)
          val different = build(otherBinary)

          (first == equal) mustBe true
          first.hashCode() mustBe equal.hashCode()
          (first == different) mustBe false
          HashSet(first, equal, different).size mustBe 2
          HashMap(first -> "found").get(equal) mustBe Some("found")
        }
      }
    }

    "snapshot arrays supplied to factories and public constructors" in {
      val factories = List[(String, Array[Byte] => BsonValue)](
        "apply"       -> ((bytes: Array[Byte]) => BBinary(value = bytes)),
        "constructor" -> ((bytes: Array[Byte]) => new BBinary(value = bytes)),
        "factory"     -> ((bytes: Array[Byte]) => BsonValue.binary(bytes))
      )

      factories.foreach { case (name, create) =>
        withClue(s"$name: ") {
          val bytes        = Array[Byte](1, 2, 3)
          val binary       = create(bytes)
          val original     = create(bytes.clone())
          val originalHash = binary.hashCode()
          val originalDoc  = document(original)
          val doc          = document(binary)
          val keys         = HashMap(doc -> "found")

          bytes(0) = 9

          binary mustBe original
          binary.hashCode() mustBe originalHash
          doc mustBe originalDoc
          keys.get(originalDoc) mustBe Some("found")
          binary.asJava mustBe original.asJava
        }
      }
    }

    "keep stored bytes private when exposing arrays" in {
      val readers = List[(String, BBinary => Array[Byte])](
        "value"            -> ((binary: BBinary) => binary.value),
        "extractor"        -> ((binary: BBinary) => BBinary.unapply(binary).get._1),
        "product element"  -> ((binary: BBinary) => binary.productElement(0).asInstanceOf[Array[Byte]]),
        "product iterator" -> ((binary: BBinary) => binary.productIterator.next().asInstanceOf[Array[Byte]]),
        "Java BSON"        -> ((binary: BBinary) => binary.asJava.asBinary().getData)
      )

      readers.foreach { case (name, read) =>
        withClue(s"$name: ") {
          val binary       = BBinary(Array[Byte](1, 2, 3), 5.toByte)
          val original     = BBinary(Array[Byte](1, 2, 3), 5.toByte)
          val originalHash = binary.hashCode()
          val doc          = document(binary)
          val keys         = HashSet(doc)
          val exposed      = read(binary)

          exposed(0) = 9

          binary mustBe original
          binary.hashCode() mustBe originalHash
          binary.value mustBe Array[Byte](1, 2, 3)
          keys.contains(document(original)) mustBe true
          binary.asJava mustBe new BsonBinary(5.toByte, Array[Byte](1, 2, 3))
        }
      }
    }

    "preserve copy defaults and snapshot replacement arrays" in {
      val original  = BBinary(Array[Byte](1, 2, 3), 5.toByte)
      val bytes     = Array[Byte](4, 5, 6)
      val copy      = original.copy(value = bytes)
      val equalCopy = BBinary(Array[Byte](4, 5, 6), 5.toByte)
      val keys      = HashMap(copy -> "found")

      original.copy() mustBe original
      original.copy().hashCode() mustBe original.hashCode()
      original.copy(subtype = 0.toByte) mustBe BBinary(Array[Byte](1, 2, 3))

      bytes(0) = 9

      copy mustBe equalCopy
      keys.get(equalCopy) mustBe Some("found")
      original.value mustBe Array[Byte](1, 2, 3)
    }

    "snapshot mutable Java binary values during conversion" in {
      val binary   = new BsonBinary(5.toByte, Array[Byte](1, 2, 3))
      val result   = BsonValueConverter.fromJava(binary)
      val original = BBinary(Array[Byte](1, 2, 3), 5.toByte)
      val keys     = HashMap(document(result) -> "found")

      binary.getData()(0) = 9

      result mustBe original
      keys.get(document(original)) mustBe Some("found")
      result.asJava mustBe original.asJava
    }
  }
}
