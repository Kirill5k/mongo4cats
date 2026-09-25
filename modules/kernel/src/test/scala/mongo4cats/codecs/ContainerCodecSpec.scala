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

package mongo4cats.codecs

import org.bson.codecs.{DecoderContext, EncoderContext}
import org.bson.{BsonDocument, BsonDocumentReader, BsonDocumentWriter}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ContainerCodecSpec extends AnyWordSpec with Matchers {

  private val mapCodec       = CodecRegistry.Default.get(classOf[Map[String, Any]])
  private val iterableCodec  = CodecRegistry.Default.get(classOf[Iterable[Any]])
  private val encoderContext = EncoderContext.builder().build()
  private val decoderContext = DecoderContext.builder().build()

  private def encodeMap(value: Map[String, Any]): BsonDocument = {
    val result = new BsonDocument()
    mapCodec.encode(new BsonDocumentWriter(result), value, encoderContext)
    result
  }

  private def decodeMap(value: BsonDocument): Map[String, Any] =
    mapCodec.decode(new BsonDocumentReader(value), decoderContext)

  private def encodeIterable(value: Iterable[Any]): BsonDocument = {
    val result = new BsonDocument()
    val writer = new BsonDocumentWriter(result)
    writer.writeStartDocument()
    writer.writeName("values")
    iterableCodec.encode(writer, value, encoderContext)
    writer.writeEndDocument()
    result
  }

  private def decodeIterable(value: BsonDocument): Iterable[Any] = {
    val reader = new BsonDocumentReader(value)
    reader.readStartDocument()
    reader.readName("values")
    val result = iterableCodec.decode(reader, decoderContext)
    reader.readEndDocument()
    result
  }

  "Collection codecs" should {
    "encode JVM null map values as BSON null" in {
      encodeMap(Map[String, Any]("value" -> null)) mustBe BsonDocument.parse("""{"value": null}""")
    }

    "encode JVM nulls in nested Scala maps and lists" in {
      val value = Map[String, Any](
        "nested" -> Map[String, Any]("value" -> null, "after" -> 42),
        "values" -> List[Any](null, Map[String, Any]("value" -> null, "after" -> "kept"), List[Any](null, 7), "last")
      )
      val expected = BsonDocument.parse("""{
        "nested": {"value": null, "after": 42},
        "values": [null, {"value": null, "after": "kept"}, [null, 7], "last"]
      }""")

      encodeMap(value) mustBe expected
    }

    "preserve nulls when decoding and re-encoding maps containing nested documents and lists" in {
      val original = BsonDocument.parse("""{
        "value": null,
        "nested": {"value": null, "values": [null, 7], "after": "kept"},
        "values": [null, [null, 42], {"value": null, "after": "kept"}, "last"],
        "after": true
      }""")
      val decoded = decodeMap(original)

      (decoded("value") == null) mustBe true
      encodeMap(decoded) mustBe original
    }

    "preserve nulls and element positions when decoding and re-encoding nested lists" in {
      val original = BsonDocument.parse("""{
        "values": [null, [null, 42, null, "after"], {"value": null, "values": [null, 7], "after": "kept"}, "last", null]
      }""")
      val decoded = decodeIterable(original)

      (decoded.head == null) mustBe true
      (decoded.last == null) mustBe true
      encodeIterable(decoded) mustBe original
    }
  }
}
