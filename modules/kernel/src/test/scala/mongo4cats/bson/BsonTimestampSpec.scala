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

import com.mongodb.client.model.changestream.{ChangeStreamDocument => JChangeStreamDocument}
import mongo4cats.codecs.CodecRegistry
import mongo4cats.models.collection.ChangeStreamDocument
import org.bson.codecs.{DecoderContext, EncoderContext}
import org.bson.{BsonDocument, BsonDocumentReader, BsonDocumentWriter, BsonString, BsonTimestamp, Document => JDocument}
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

class BsonTimestampSpec extends AnyWordSpec with Matchers {

  private val secondsBoundaries = List(0L, Int.MaxValue.toLong, 0x80000000L, 0xffffffffL)
  private val incrementPatterns = List(0, Int.MaxValue, Int.MinValue, -1)
  private val decoderContext    = DecoderContext.builder().build()
  private val encoderContext    = EncoderContext.builder().build()
  private val documentCodec     = CodecRegistry.Default.get(classOf[Document])
  private val changeStreamCodec = JChangeStreamDocument.createCodec(classOf[Document], CodecRegistry.Default)

  private def forEachTimestamp(check: (Long, Int) => Assertion): Unit =
    for {
      seconds <- secondsBoundaries
      inc     <- incrementPatterns
    } withClue(s"seconds=$seconds, increment=$inc: ")(check(seconds, inc))

  "BSON timestamps" should {
    "decode Java timestamp seconds as unsigned while preserving increment bits" in
      forEachTimestamp { (seconds, inc) =>
        val timestamp = new BsonTimestamp(seconds.toInt, inc)
        val decoded   = BsonValueConverter.fromJava(timestamp)

        decoded mustBe BsonValue.timestamp(seconds, inc)
        decoded.asLong mustBe Some(seconds)
        decoded.asInstant mustBe Some(Instant.ofEpochSecond(seconds))
        decoded.asJava mustBe timestamp
      }

    "preserve all unsigned seconds through Java BSON round trips" in
      forEachTimestamp { (seconds, inc) =>
        val original = BsonValue.timestamp(seconds, inc)

        BsonValueConverter.fromJava(original.asJava) mustBe original
      }

    "decode unsigned seconds from legacy Java document timestamps" in
      forEachTimestamp { (seconds, inc) =>
        val timestamp = new org.bson.types.BSONTimestamp(seconds.toInt, inc)
        val original  = new JDocument("ts", timestamp)

        Document.fromJava(original).get("ts") mustBe Some(BsonValue.timestamp(seconds, inc))
      }

    "preserve timestamp values through document encoding and decoding" in
      forEachTimestamp { (seconds, inc) =>
        val original = Document("ts" -> BsonValue.timestamp(seconds, inc))
        val encoded  = new BsonDocument()
        documentCodec.encode(new BsonDocumentWriter(encoded), original, encoderContext)

        encoded.getTimestamp("ts") mustBe new BsonTimestamp(seconds.toInt, inc)
        documentCodec.decode(new BsonDocumentReader(encoded), decoderContext) mustBe original
        Document.fromJava(encoded) mustBe original
      }

    "preserve unsigned seconds and increment bits through Extended JSON" in
      forEachTimestamp { (seconds, inc) =>
        val original = Document("ts" -> BsonValue.timestamp(seconds, inc))

        Document.parse(original.toJson) mustBe original
      }

    "decode unsigned cluster times in change stream events" in
      forEachTimestamp { (seconds, inc) =>
        val event = new BsonDocument("_id", new BsonDocument())
          .append("operationType", new BsonString("insert"))
          .append("clusterTime", new BsonTimestamp(seconds.toInt, inc))
        val decoded = changeStreamCodec.decode(new BsonDocumentReader(event), decoderContext)

        ChangeStreamDocument.fromJava(decoded).clusterTime mustBe Some(BsonValue.timestamp(seconds, inc))
      }
  }
}
