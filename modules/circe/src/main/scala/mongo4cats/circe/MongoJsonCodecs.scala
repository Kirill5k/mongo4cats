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

import io.circe.{Decoder, Encoder, Json, JsonObject}
import mongo4cats.bson._
import mongo4cats.bson.json._
import mongo4cats.bson.syntax._
import mongo4cats.codecs.MongoCodecProvider
import mongo4cats.errors.MongoJsonParsingException
import org.bson.codecs.configuration.CodecProvider

import java.time.{Instant, LocalDate}
import java.util.UUID
import scala.reflect.ClassTag
import scala.util.Try

trait MongoJsonCodecs {
  private val emptyJsonObject = Json.fromJsonObject(JsonObject.empty)

  implicit def deriveJsonBsonValueDecoder[A](implicit d: Decoder[A]): BsonValueDecoder[A] =
    bson => CirceJsonMapper.fromBson(bson).flatMap(d.decodeJson).toOption

  implicit def deriveJsonBsonValueEncoder[A](implicit e: Encoder[A]): BsonValueEncoder[A] =
    value => CirceJsonMapper.toBson(e(value))

  implicit val documentEncoder: Encoder[Document] =
    Encoder.encodeJson.contramap[Document](d => CirceJsonMapper.fromBsonOpt(BsonValue.document(d)).getOrElse(emptyJsonObject))

  implicit val documentDecoder: Decoder[Document] =
    Decoder.decodeJson.emap(j => CirceJsonMapper.toBson(j).asDocument.toRight(s"$j is not a valid document"))

  implicit val objectIdEncoder: Encoder[ObjectId] =
    Encoder.encodeJson.contramap[ObjectId](CirceJsonMapper.objectIdToJson)

  implicit val objectIdDecoder: Decoder[ObjectId] =
    Decoder.decodeJson.emap(id => CirceJsonMapper.jsonToObjectIdString(id).toRight(s"$id is not a valid id").flatMap(ObjectId.from))

  implicit val instantEncoder: Encoder[Instant] =
    Encoder.encodeJson.contramap[Instant](CirceJsonMapper.instantToJson)

  implicit val instantDecoder: Decoder[Instant] =
    Decoder.decodeJson.emap { instantObj =>
      CirceJsonMapper
        .jsonToDateString(instantObj)
        .flatMap(s => Try(Instant.parse(s)).toOption)
        .toRight(s"$instantObj is not a valid instant object")
    }

  implicit val localDateEncoder: Encoder[LocalDate] =
    Encoder.encodeJson.contramap[LocalDate](CirceJsonMapper.localDateToJson)

  implicit val localDateDecoder: Decoder[LocalDate] =
    Decoder.decodeJson.emap { dateObj =>
      CirceJsonMapper
        .jsonToDateString(dateObj)
        .map(_.slice(0, 10))
        .flatMap(s => Try(LocalDate.parse(s)).toOption)
        .toRight(s"$dateObj is not a valid date object")
    }

  implicit val uuidEncoder: Encoder[UUID] =
    Encoder.encodeJson.contramap[UUID](CirceJsonMapper.uuidToJson)

  implicit val uuidDecoder: Decoder[UUID] =
    Decoder.decodeJson.emapTry(uuidObj => Try(CirceJsonMapper.jsonToUuid(uuidObj)))

  implicit def deriveCirceCodecProvider[T: ClassTag](implicit enc: Encoder[T], dec: Decoder[T]): MongoCodecProvider[T] =
    new MongoCodecProvider[T] {
      override def get: CodecProvider = codecProvider[T](
        _.toBson,
        CirceJsonMapper
          .fromBson(_)
          .flatMap(j => dec.decodeJson(j).left.map(e => MongoJsonParsingException(e.getMessage, Some(j.noSpaces))))
      )
    }
}
