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

package mongo4cats.circe

import io.circe.Json
import mongo4cats.bson.{BsonValue, Document, ObjectId}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant

class JsonMapperSpec extends AnyWordSpec with Matchers {

  val ts = Instant.now()
  val id = ObjectId.gen

  val bsonDocument = BsonValue.document(
    Document(
      "_id"           -> BsonValue.objectId(id),
      "string"        -> BsonValue.string("string"),
      "null"          -> BsonValue.Null,
      "boolean"       -> BsonValue.True,
      "long"          -> BsonValue.long(ts.toEpochMilli),
      "int"           -> BsonValue.int(1),
      "bigDecimal"    -> BsonValue.bigDecimal(BigDecimal(100.0)),
      "array"         -> BsonValue.array(BsonValue.string("a"), BsonValue.string("b")),
      "dateInstant"   -> BsonValue.instant(ts),
      "dateEpoch"     -> BsonValue.instant(ts),
      "dateLocalDate" -> BsonValue.instant(Instant.parse("2022-01-01T00:00:00Z")),
      "document"      -> BsonValue.document(Document("field1" -> BsonValue.string("1"), "field2" -> BsonValue.int(2)))
    )
  )

  "A CirceMapper" when {
    "toBson" should {
      "accurately convert json to bson" in {
        val jsonObject = Json.obj(
          "_id"           -> Json.obj("$oid" -> Json.fromString(id.toHexString)),
          "string"        -> Json.fromString("string"),
          "null"          -> Json.Null,
          "boolean"       -> Json.fromBoolean(true),
          "long"          -> Json.fromLong(ts.toEpochMilli),
          "int"           -> Json.fromInt(1),
          "bigDecimal"    -> Json.fromBigDecimal(BigDecimal(100.0)),
          "array"         -> Json.arr(Json.fromString("a"), Json.fromString("b")),
          "dateInstant"   -> Json.obj("$date" -> Json.fromString(ts.toString)),
          "dateEpoch"     -> Json.obj("$date" -> Json.fromLong(ts.toEpochMilli)),
          "dateLocalDate" -> Json.obj("$date" -> Json.fromString("2022-01-01")),
          "document"      -> Json.obj("field1" -> Json.fromString("1"), "field2" -> Json.fromInt(2))
        )

        JsonMapper.toBson(jsonObject).asDocument.map(_.toJson) mustBe bsonDocument.asDocument.map(_.toJson)
      }

      "accurately convert bson to json" in {
        JsonMapper.fromBson(bsonDocument) mustBe Right(
          Json.obj(
            "_id"           -> Json.obj("$oid" -> Json.fromString(id.toHexString)),
            "string"        -> Json.fromString("string"),
            "null"          -> Json.Null,
            "boolean"       -> Json.fromBoolean(true),
            "long"          -> Json.fromLong(ts.toEpochMilli),
            "int"           -> Json.fromInt(1),
            "bigDecimal"    -> Json.fromBigDecimal(BigDecimal(100.0)),
            "array"         -> Json.arr(Json.fromString("a"), Json.fromString("b")),
            "dateInstant"   -> Json.obj("$date" -> Json.fromString(ts.toString)),
            "dateEpoch"     -> Json.obj("$date" -> Json.fromString(ts.toString)),
            "dateLocalDate" -> Json.obj("$date" -> Json.fromString("2022-01-01T00:00:00Z")),
            "document"      -> Json.obj("field1" -> Json.fromString("1"), "field2" -> Json.fromInt(2))
          )
        )
      }
    }
  }
}
