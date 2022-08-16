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

import mongo4cats.bson.syntax._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

class DocumentSpec extends AnyWordSpec with Matchers {

  "A MyDocument" when {
    val jsonString = """{"name": {"first": "John", "last": "Smith", "aliases": ["foo", "bar"]}, "tags": [42, "test"]}"""

    val nameDoc = Document("first" := "John", "last" := "Smith", "aliases" := List("foo", "bar"))
    val tags    = List(BsonValue.int(42), BsonValue.string("test"))

    val testDocument = Document("name" := nameDoc, "tags" -> tags.toBson)

    "dealing with json" should {
      "create itself from json string" in {
        val result = Document.parse(jsonString)

        result mustBe testDocument
        result.getList("tags") mustBe Some(tags)
        result.getDocument("name") mustBe Some(nameDoc)
        result.getObjectId("_id") mustBe None
      }

      "convert itself to json" in {
        testDocument.toJson mustBe jsonString
      }

      "handle arrays with json" in {
        val result = Document.parse(s"""{"people": [$jsonString]}""")

        result.getList("people") mustBe Some(List(testDocument.toBson))
      }
    }

    "calling toString" should {
      "produce string representation" in {
        testDocument.toString mustBe jsonString
      }
    }

    "getting a value by key" should {
      "handle null and undefined" in {
        val doc = Document.parse("""{"propA":null,"propB":undefined}""")

        doc.get("propA") mustBe Some(BsonValue.Null)
        doc.get("propB") mustBe Some(BsonValue.Undefined)
        doc.getString("propA") mustBe None
        doc.getString("propB") mustBe None
      }

      "handle time" in {
        val doc = Document.parse("""{"time":{"$date":1640995200000}}""")

        doc.get("time") mustBe Some(Instant.parse("2022-01-01T00:00:00.0+00:00").toBson)
      }

      "retrieve nested fields" in {
        testDocument.getNested("name.first") mustBe Some("John".toBson)
      }

      "return empty option when nested field does not exist" in {
        testDocument.getNested("foo.bar") mustBe None
      }
    }
  }
}
