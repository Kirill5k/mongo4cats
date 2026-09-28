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

package mongo4cats.models.collection

import com.mongodb.client.model.changestream.{ChangeStreamDocument => JChangeStreamDocument, OperationType}
import mongo4cats.bson.{BsonJsonMode, Document}
import mongo4cats.codecs.CodecRegistry
import mongo4cats.queries.WatchFixture
import org.bson.{BsonDocument, BsonDocumentReader}
import org.bson.codecs.DecoderContext
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ChangeStreamDocumentSpec extends AnyWordSpec with Matchers {
  private val codec = JChangeStreamDocument.createCodec(classOf[Document], CodecRegistry.Default)

  private def decode(event: BsonDocument): ChangeStreamDocument[Document] =
    ChangeStreamDocument.fromJava(codec.decode(new BsonDocumentReader(event), DecoderContext.builder().build()))

  "Change stream event conversion" should {
    "preserve both document images and the event namespace" in {
      val event = decode(WatchFixture.eventBson)

      event.fullDocument mustBe Some(Document.parse("""{"_id":1,"value":"after"}"""))
      event.fullDocumentBeforeChange mustBe Some(Document.parse("""{"_id":1,"value":"before"}"""))
      event.namespace mustBe Some(MongoNamespace("watched", "records"))
      event.resumeToken.toBsonDocument mustBe WatchFixture.eventBson.getDocument("_id")
    }

    "retain expanded create events and their additional metadata" in {
      val raw = BsonDocument.parse("""{
        "_id": {"_data": "expanded-event-token"},
        "operationType": "create",
        "ns": {"db": "watched", "coll": "records"},
        "collectionUUID": {"$binary": {"base64": "AQIDBAUGBwgJCgsMDQ4PEA==", "subType": "04"}},
        "operationDescription": {"options": {"capped": true}}
      }""")
      val event = decode(raw)

      event.operationType mustBe OperationType.OTHER
      event.namespace mustBe Some(MongoNamespace("watched", "records"))
      event.fullDocument mustBe None
      event.fullDocumentBeforeChange mustBe None
      event.extraElements.get.toBsonDocument mustBe new BsonDocument("collectionUUID", raw.get("collectionUUID"))
        .append("operationDescription", raw.get("operationDescription"))
    }

    "round-trip the entire opaque resume token through canonical Extended JSON" in {
      val token = BsonDocument.parse("""{
        "_data": "opaque-token",
        "_typeBits": {"$binary": {"base64": "AQIDBA==", "subType": "80"}},
        "timestamp": {"$timestamp": {"t": 4294967295, "i": 4294967295}},
        "sequence": {"$numberLong": "9223372036854775807"},
        "smallLong": {"$numberLong": "1"},
        "nested": {"values": [{"$numberInt": "1"}, {"$numberLong": "1"}]}
      }""")
      val raw = BsonDocument.parse("""{"operationType":"insert"}""").append("_id", token)
      val event = decode(raw)
      val restored = Document.parse(event.resumeToken.toJson(BsonJsonMode.Canonical))

      event.resumeToken.toBsonDocument mustBe token
      restored.toBsonDocument mustBe token
    }
  }
}
