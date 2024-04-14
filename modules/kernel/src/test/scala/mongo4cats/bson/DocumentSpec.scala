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
import java.util.UUID

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
        result.getAs[List[Document]]("people") mustBe Some(List(testDocument))
      }

      "convert dates to json accurately" in {
        val document = Document("time" := Instant.parse("2022-01-01T00:00:00Z"))

        document.toJson mustBe """{"time": {"$date": "2022-01-01T00:00:00Z"}}"""
      }

      "convert uuid to json accurately" in {
        val document = Document("uuid" := UUID.fromString("cfbca728-4e39-4613-96bc-f920b5c37e16"))

        document.toJson mustBe """{"uuid": {"$binary": {"base64": "z7ynKE45RhOWvPkgtcN+Fg==", "subType": "04"}}}"""
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

        doc("propA") mustBe Some(BsonValue.Null)
        doc("propB") mustBe Some(BsonValue.Undefined)
        doc.getString("propA") mustBe None
        doc.getString("propB") mustBe None
      }

      "handle timestamp" in {
        val doc = Document.parse("""{"timestamp":{"$timestamp": {"t": 1673600231, "i": 1}}}""")

        doc.get("timestamp") mustBe Some(BsonValue.timestamp(1673600231L, 1))
        doc.getAs[Instant]("timestamp") mustBe Some(Instant.parse("2023-01-13T08:57:11Z"))
        doc.getAs[Long]("timestamp") mustBe Some(1673600231L)
      }

      "handle date-time" in {
        val doc = Document.parse("""{"time":{"$date":1640995200000}}""")

        doc.get("time") mustBe Some(Instant.parse("2022-01-01T00:00:00.0+00:00").toBson)
        doc.getAs[Instant]("time") mustBe Some(Instant.parse("2022-01-01T00:00:00.0+00:00"))
      }

      "handle date" in {
        val doc = Document.parse("""{"time":{"$date":"2020-01-01"}}""")

        doc.toJson mustBe """{"time": {"$date": "2020-01-01T00:00:00Z"}}"""
      }

      "handle uuid" in {
        val doc = Document.parse("""{"uuid": {"$binary": {"base64": "z7ynKE45RhOWvPkgtcN+Fg==", "subType": "04"}}}""")

        doc.getAs[UUID]("uuid") mustBe Some(UUID.fromString("cfbca728-4e39-4613-96bc-f920b5c37e16"))
      }

      "retrieve nested fields" in {
        testDocument.getNested("name.first").flatMap(_.asString) mustBe Some("John")
        testDocument.getNestedAs[String]("name.first") mustBe Some("John")
      }

      "return empty option when nested field does not exist" in {
        testDocument.getNested("foo.bar") mustBe None
        testDocument.getNestedAs[String]("foo.bar") mustBe None
      }

      "return List[A] when when it can be decoded" in {
        val doc = Document.parse("""{"array":["a", "b", "c"]}""")

        doc.getAs[List[String]]("array") mustBe Some(List("a", "b", "c"))
        doc.getAs[List[Int]]("array") mustBe None
      }

      "return empty option when structure of the list is different" in {
        val doc = Document.parse("""{"array":["a", "b", 1]}""")

        doc.getAs[List[String]]("array") mustBe None
      }
    }

    "when adding new elements" should {
      "preserve order of the inserted elements" in {
        val result = Document.empty += "a" -> 1 += "b" -> 2 += "c" -> 3 += "d" -> 4

        result.toJson mustBe """{"a": 1, "b": 2, "c": 3, "d": 4}"""
      }
    }

    "when comparing 2 documents" should {
      "return true if both documents have same fields" in {
        Document.parse(nameDoc.toJson) mustBe nameDoc
        Document("first" := "John", "last" := "Smith", "aliases" := List("foo", "bar")) mustBe nameDoc
      }

      "return false when 2 documents are different" in {
        (nameDoc += "age" -> 30) must not be nameDoc
      }
    }
  }
}
