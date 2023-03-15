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
import mongo4cats.Clazz
import mongo4cats.bson.json._
import mongo4cats.bson.syntax._
import mongo4cats.bson._
import mongo4cats.codecs.MongoCodecProvider
import org.bson.codecs.configuration.CodecProvider

import java.time.{Instant, LocalDate}
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
    Encoder.encodeJsonObject.contramap[ObjectId](id => JsonObject(Tag.id -> Json.fromString(id.toHexString)))

  implicit val objectIdDecoder: Decoder[ObjectId] =
    Decoder.decodeJsonObject.emap { idObj =>
      idObj(Tag.id)
        .flatMap(_.asString)
        .toRight(s"$idObj is not a valid id")
        .flatMap(ObjectId.from)
    }

  implicit val instantEncoder: Encoder[Instant] =
    Encoder.encodeJsonObject.contramap[Instant](i => JsonObject(Tag.date -> Json.fromString(i.toString)))

  implicit val instantDecoder: Decoder[Instant] =
    Decoder.decodeJsonObject.emap { instantObj =>
      instantObj(Tag.date)
        .flatMap(_.asString)
        .flatMap(s => Try(Instant.parse(s)).toOption)
        .toRight(s"$instantObj is not a valid instant object")
    }

  implicit val localDateEncoder: Encoder[LocalDate] =
    Encoder.encodeJsonObject.contramap[LocalDate](i => JsonObject(Tag.date -> Json.fromString(i.toString)))

  implicit val localDateDecoder: Decoder[LocalDate] =
    Decoder.decodeJsonObject.emap { dateObj =>
      dateObj(Tag.date)
        .flatMap(_.asString)
        .map(_.slice(0, 10))
        .flatMap(s => Try(LocalDate.parse(s)).toOption)
        .toRight(s"$dateObj is not a valid date object")
    }

  implicit def deriveCirceCodecProvider[T: ClassTag](implicit enc: Encoder[T], dec: Decoder[T]): MongoCodecProvider[T] =
    new MongoCodecProvider[T] {
      override def get: CodecProvider = codecProvider[T](
        t => t.toBson,
        b =>
          CirceJsonMapper
            .fromBson(b)
            .flatMap(j => dec.decodeJson(j).left.map(e => MongoJsonParsingException(e.getMessage, Some(j.noSpaces)))),
        Clazz.tag[T]
      )
    }
}
