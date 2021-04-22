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

package mongo4cats

import io.circe.{Decoder, Encoder, Json, JsonObject}
import org.mongodb.scala.bson.ObjectId

import java.time.Instant
import scala.util.Try

private[mongo4cats] trait JsonCodecs {

  implicit val encodeObjectId: Encoder[ObjectId] =
    Encoder.encodeJsonObject.contramap[ObjectId](i => JsonObject("$oid" -> Json.fromString(i.toHexString)))
  implicit val decodeObjectId: Decoder[ObjectId] =
    Decoder.decodeJsonObject.emapTry(id => Try(new ObjectId(id("$oid").flatMap(_.asString).get)))

  implicit val encodeInstant: Encoder[Instant] =
    Encoder.encodeJsonObject.contramap[Instant](i => JsonObject("$date" -> Json.fromString(i.toString)))
  implicit val decodeInstant: Decoder[Instant] =
    Decoder.decodeJsonObject.emapTry(dateObj => Try(Instant.parse(dateObj("$date").flatMap(_.asString).get)))

}
