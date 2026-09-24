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

import org.bson.{BsonBinary, BsonBinarySubType, BsonSerializationException}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util.{Base64, UUID}

class BsonValueConverterSpec extends AnyWordSpec with Matchers {

  "BsonValueConverter" when {
    "converting Java BSON binary values" should {
      "decode standard UUID bytes without changing their value" in {
        val binary = new BsonBinary(BsonBinarySubType.UUID_STANDARD, Base64.getDecoder.decode("z7ynKE45RhOWvPkgtcN+Fg=="))

        val result = BsonValueConverter.fromJava(binary)

        result mustBe BsonValue.uuid(UUID.fromString("cfbca728-4e39-4613-96bc-f920b5c37e16"))
        result.asJava mustBe binary
      }

      "preserve UUID bits through a Java BSON round trip" in {
        val uuids = List(
          new UUID(0L, 0L),
          new UUID(-1L, -1L),
          UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")
        )

        uuids.foreach { uuid =>
          val original = BsonValue.uuid(uuid)

          withClue(s"UUID $uuid: ") {
            BsonValueConverter.fromJava(original.asJava) mustBe original
          }
        }
      }

      "keep non-standard UUID subtypes as binary values" in {
        val bytes = Array.tabulate[Byte](16)(_.toByte)

        List(BsonBinarySubType.BINARY, BsonBinarySubType.UUID_LEGACY).foreach { subtype =>
          BsonValueConverter.fromJava(new BsonBinary(subtype, bytes)) match {
            case BsonValue.BBinary(data, actualSubtype) =>
              data mustBe bytes
              actualSubtype mustBe subtype.getValue
            case other => fail(s"Expected BBinary for $subtype but got $other")
          }
        }
      }

      "reject standard UUID binary values with an invalid length" in {
        val binary = new BsonBinary(BsonBinarySubType.UUID_STANDARD, Array.ofDim[Byte](15))

        assertThrows[BsonSerializationException](BsonValueConverter.fromJava(binary))
      }
    }
  }
}
