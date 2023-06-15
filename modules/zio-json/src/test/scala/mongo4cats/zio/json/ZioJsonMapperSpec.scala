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

package mongo4cats.zio.json

import mongo4cats.bson.{BsonValue, Document, ObjectId}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio.json.ast.Json
import zio.json.JsonEncoder

import java.time.Instant
import java.util.UUID

class ZioJsonMapperSpec extends AnyWordSpec with Matchers {

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
      "document"      -> BsonValue.document(Document("field1" -> BsonValue.string("1"), "field2" -> BsonValue.int(2))),
      "uuid"          -> BsonValue.uuid(UUID.fromString("cfbca728-4e39-4613-96bc-f920b5c37e16"))
    )
  )

  def jsonString(s: String): Json          = JsonEncoder.string.toJsonAST(s).toOption.get
  def jsonBool(b: Boolean): Json           = JsonEncoder.boolean.toJsonAST(b).toOption.get
  def jsonInt(i: Int): Json                = JsonEncoder.int.toJsonAST(i).toOption.get
  def jsonLong(l: Long): Json              = JsonEncoder.long.toJsonAST(l).toOption.get
  def jsonBigDecimal(bd: BigDecimal): Json = JsonEncoder.scalaBigDecimal.toJsonAST(bd).toOption.get

  "A ZioJsonMapper" when {
    "toBson" should {
      "accurately convert json to bson" in {
        val jsonObject = Json.Obj(
          "_id"           -> Json.Obj("$oid" -> Json.Str(id.toHexString)),
          "string"        -> jsonString("string"),
          "null"          -> Json.Null,
          "boolean"       -> jsonBool(true),
          "long"          -> jsonLong(ts.toEpochMilli),
          "int"           -> jsonInt(1),
          "bigDecimal"    -> jsonBigDecimal(BigDecimal(100.0)),
          "array"         -> Json.Arr(jsonString("a"), jsonString("b")),
          "dateInstant"   -> Json.Obj("$date" -> jsonString(ts.toString)),
          "dateEpoch"     -> Json.Obj("$date" -> jsonLong(ts.toEpochMilli)),
          "dateLocalDate" -> Json.Obj("$date" -> jsonString("2022-01-01")),
          "document"      -> Json.Obj("field1" -> jsonString("1"), "field2" -> jsonInt(2)),
          "uuid" -> Json.Obj("$binary" -> Json.Obj("base64" -> jsonString("z7ynKE45RhOWvPkgtcN+Fg=="), "subType" -> jsonString("00")))
        )

        ZioJsonMapper.toBson(jsonObject).asDocument.map(_.toJson) mustBe bsonDocument.asDocument.map(_.toJson)
      }

      "accurately convert bson to json" in {
        ZioJsonMapper.fromBson(bsonDocument) mustBe Right(
          Json.Obj(
            "_id"           -> Json.Obj("$oid" -> jsonString(id.toHexString)),
            "string"        -> jsonString("string"),
            "null"          -> Json.Null,
            "boolean"       -> jsonBool(true),
            "long"          -> jsonLong(ts.toEpochMilli),
            "int"           -> jsonInt(1),
            "bigDecimal"    -> jsonBigDecimal(BigDecimal(100.0)),
            "array"         -> Json.Arr(jsonString("a"), jsonString("b")),
            "dateInstant"   -> Json.Obj("$date" -> jsonString(ts.toString)),
            "dateEpoch"     -> Json.Obj("$date" -> jsonString(ts.toString)),
            "dateLocalDate" -> Json.Obj("$date" -> jsonString("2022-01-01T00:00:00Z")),
            "document"      -> Json.Obj("field1" -> jsonString("1"), "field2" -> jsonInt(2)),
            "uuid" -> Json.Obj("$binary" -> Json.Obj("base64" -> jsonString("z7ynKE45RhOWvPkgtcN+Fg=="), "subType" -> jsonString("00")))
          )
        )
      }
    }
  }
}
