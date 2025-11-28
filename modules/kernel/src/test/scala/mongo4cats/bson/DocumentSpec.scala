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
import org.bson.{Document => JDocument}
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

    "when converting from Java Document" should {
      "convert simple Java Document with basic types" in {
        val javaDoc = new JDocument()
          .append("name", "John")
          .append("age", 30)
          .append("active", true)

        val result = Document.fromJava(javaDoc)

        result.getString("name") mustBe Some("John")
        result.getInt("age") mustBe Some(30)
        result.getBoolean("active") mustBe Some(true)
      }

      "convert Java Document with nested documents" in {
        val nestedJavaDoc = new JDocument()
          .append("first", "John")
          .append("last", "Smith")

        val javaDoc = new JDocument()
          .append("name", nestedJavaDoc)
          .append("id", 1)

        val result = Document.fromJava(javaDoc)

        result.getDocument("name").flatMap(_.getString("first")) mustBe Some("John")
        result.getDocument("name").flatMap(_.getString("last")) mustBe Some("Smith")
        result.getInt("id") mustBe Some(1)
      }

      "convert Java Document with arrays" in {
        val javaDoc = new JDocument()
          .append("tags", java.util.Arrays.asList("scala", "mongodb", "test"))
          .append("numbers", java.util.Arrays.asList(1, 2, 3))

        val result = Document.fromJava(javaDoc)

        result.getAs[List[String]]("tags") mustBe Some(List("scala", "mongodb", "test"))
        result.getAs[List[Int]]("numbers") mustBe Some(List(1, 2, 3))
      }

      "convert Java Document with numeric types" in {
        val javaDoc = new JDocument()
          .append("intValue", 42)
          .append("longValue", Long.box(9876543210L))
          .append("doubleValue", 3.14)

        val result = Document.fromJava(javaDoc)

        result.getInt("intValue") mustBe Some(42)
        result.getLong("longValue") mustBe Some(9876543210L)
        result.getDouble("doubleValue") mustBe Some(3.14)
      }

      "convert Java Document with null values" in {
        val javaDoc = new JDocument()
          .append("name", "John")
          .append("middleName", null)

        val result = Document.fromJava(javaDoc)

        result.getString("name") mustBe Some("John")
        result.get("middleName") mustBe Some(BsonValue.Null)
      }

      "preserve field order when converting from Java Document" in {
        val javaDoc = new JDocument()
          .append("a", 1)
          .append("b", 2)
          .append("c", 3)
          .append("d", 4)

        val result = Document.fromJava(javaDoc)

        result.toJson mustBe """{"a": 1, "b": 2, "c": 3, "d": 4}"""
      }

      "convert empty Java Document" in {
        val javaDoc = new JDocument()

        val result = Document.fromJava(javaDoc)

        result.isEmpty mustBe true
        result.size mustBe 0
        result mustBe Document.empty
      }

      "convert Java Document with complex nested structures" in {
        val addressDoc = new JDocument()
          .append("street", "123 Main St")
          .append("city", "Springfield")

        val contactDoc = new JDocument()
          .append("email", "john@example.com")
          .append("address", addressDoc)

        val javaDoc = new JDocument()
          .append("name", "John")
          .append("contact", contactDoc)

        val result = Document.fromJava(javaDoc)

        result.getString("name") mustBe Some("John")
        result.getNestedAs[String]("contact.email") mustBe Some("john@example.com")
        result.getNestedAs[String]("contact.address.street") mustBe Some("123 Main St")
        result.getNestedAs[String]("contact.address.city") mustBe Some("Springfield")
      }

      "convert Java Document with org.bson.types.ObjectId" in {
        val objectId = new org.bson.types.ObjectId()
        val javaDoc  = new JDocument()
          .append("_id", objectId)
          .append("name", "Test")

        val result = Document.fromJava(javaDoc)

        result.getObjectId("_id") mustBe Some(objectId)
        result.getString("name") mustBe Some("Test")
      }

      "convert Java Document with org.bson.types.Decimal128" in {
        val decimal = new org.bson.types.Decimal128(java.math.BigDecimal.valueOf(123.456))
        val javaDoc = new JDocument()
          .append("price", decimal)

        val result = Document.fromJava(javaDoc)

        result.getAs[BigDecimal]("price") mustBe Some(BigDecimal(123.456))
      }

      "convert Java Document with org.bson.types.Binary" in {
        val binaryData = Array[Byte](1, 2, 3, 4, 5)
        val binary     = new org.bson.types.Binary(binaryData)
        val javaDoc    = new JDocument()
          .append("data", binary)

        val result = Document.fromJava(javaDoc)

        result.get("data") match {
          case Some(BsonValue.BBinary(data)) => data mustBe binaryData
          case other                         => fail(s"Expected BBinary but got $other")
        }
      }

      "convert Java Document with org.bson.types.BSONTimestamp" in {
        val timestamp = new org.bson.types.BSONTimestamp(1673600231, 1)
        val javaDoc   = new JDocument()
          .append("ts", timestamp)

        val result = Document.fromJava(javaDoc)

        result.get("ts") mustBe Some(BsonValue.timestamp(1673600231L, 1))
      }

      "convert Java Document with org.bson.types.Code" in {
        val code    = new org.bson.types.Code("function() { return 1; }")
        val javaDoc = new JDocument()
          .append("script", code)

        val result = Document.fromJava(javaDoc)

        result.getString("script") mustBe Some("function() { return 1; }")
      }

      "convert Java Document with org.bson.types.CodeWithScope" in {
        val codeWithScope = new org.bson.types.CodeWithScope("function() { return x; }", new JDocument())
        val javaDoc       = new JDocument()
          .append("script", codeWithScope)

        val result = Document.fromJava(javaDoc)

        result.getString("script") mustBe Some("function() { return x; }")
      }

      "convert Java Document with org.bson.types.CodeWScope" in {
        val scope      = new org.bson.types.BasicBSONList()
        val codeWScope = new org.bson.types.CodeWScope("function() { return y; }", scope)
        val javaDoc    = new JDocument()
          .append("script", codeWScope)

        val result = Document.fromJava(javaDoc)

        result.getString("script") mustBe Some("function() { return y; }")
      }

      "convert Java Document with org.bson.types.Symbol" in {
        val symbol  = new org.bson.types.Symbol("mySymbol")
        val javaDoc = new JDocument()
          .append("sym", symbol)

        val result = Document.fromJava(javaDoc)

        result.getString("sym") mustBe Some("mySymbol")
      }

      "convert Java Document with org.bson.types.MaxKey" in {
        val maxKey  = new org.bson.types.MaxKey()
        val javaDoc = new JDocument()
          .append("max", maxKey)

        val result = Document.fromJava(javaDoc)

        result.get("max") mustBe Some(BsonValue.MaxKey)
      }

      "convert Java Document with org.bson.types.MinKey" in {
        val minKey  = new org.bson.types.MinKey()
        val javaDoc = new JDocument()
          .append("min", minKey)

        val result = Document.fromJava(javaDoc)

        result.get("min") mustBe Some(BsonValue.MinKey)
      }

      "convert Java Document with org.bson.types.BasicBSONList" in {
        val bsonList = new org.bson.types.BasicBSONList()
        bsonList.add("item1")
        bsonList.add("item2")
        bsonList.add(42)
        val javaDoc = new JDocument()
          .append("list", bsonList)

        val result = Document.fromJava(javaDoc)

        result.getAs[List[String]]("list") mustBe None // Mixed types
        val list = result.getList("list")
        list.map(_.size) mustBe Some(3)
      }

      "convert Java Document with mixed org.bson.types" in {
        val objectId  = new org.bson.types.ObjectId()
        val decimal   = new org.bson.types.Decimal128(java.math.BigDecimal.valueOf(99.99))
        val timestamp = new org.bson.types.BSONTimestamp(1000000, 1)

        val javaDoc = new JDocument()
          .append("_id", objectId)
          .append("price", decimal)
          .append("created", timestamp)
          .append("name", "Product")

        val result = Document.fromJava(javaDoc)

        result.getObjectId("_id") mustBe Some(objectId)
        result.getAs[BigDecimal]("price") mustBe Some(BigDecimal(99.99))
        result.get("created") mustBe Some(BsonValue.timestamp(1000000L, 1))
        result.getString("name") mustBe Some("Product")
      }
    }
  }
}
