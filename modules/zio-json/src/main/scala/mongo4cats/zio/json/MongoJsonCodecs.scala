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

import mongo4cats.bson.json._
import mongo4cats.bson._
import mongo4cats.codecs.MongoCodecProvider
import mongo4cats.errors.MongoJsonParsingException
import org.bson.codecs.configuration.CodecProvider
import zio.json.ast.Json
import zio.json.{JsonDecoder, JsonEncoder}

import java.time.{Instant, LocalDate}
import java.util.{Base64, UUID}
import scala.reflect.ClassTag
import scala.util.Try

trait MongoJsonCodecs {
  private val emptyJsonObject = Json.Obj()

  implicit def deriveJsonBsonValueEncoder[A](implicit e: JsonEncoder[A]): BsonValueEncoder[A] =
    value => ZioJsonMapper.toBson(e.toJsonAST(value).toOption.get)

  implicit def deriveJsonBsonValueDecoder[A](implicit d: JsonDecoder[A]): BsonValueDecoder[A] =
    bson => ZioJsonMapper.fromBson(bson).flatMap(d.fromJsonAST).toOption

  implicit val documentEncoder: JsonEncoder[Document] =
    Json.encoder.contramap[Document](d => ZioJsonMapper.fromBson(BsonValue.document(d)).getOrElse(emptyJsonObject))

  implicit val documentDecoder: JsonDecoder[Document] =
    Json.decoder.mapOrFail(j => ZioJsonMapper.toBson(j).asDocument.toRight(s"$j is not a valid document"))

  implicit val objectIdEncoder: JsonEncoder[ObjectId] =
    Json.encoder.contramap[ObjectId](ZioJsonMapper.objectIdToJson)

  implicit val objectIdDecoder: JsonDecoder[ObjectId] =
    Json.decoder.mapOrFail[ObjectId] { id =>
      ZioJsonMapper.jsonToObjectIdString(id).toRight(s"$id is not a valid object id").flatMap(ObjectId.from)
    }

  implicit val instantEncoder: JsonEncoder[Instant] =
    Json.encoder.contramap[Instant](ZioJsonMapper.instantToJson)

  implicit val instantDecoder: JsonDecoder[Instant] =
    Json.decoder.mapOrFail[Instant] { dateObj =>
      ZioJsonMapper
        .jsonToDateString(dateObj)
        .flatMap(tsStr => Try(Instant.parse(tsStr)).toOption)
        .toRight(s"$dateObj is not a valid instant object")
    }

  implicit val localDateEncoder: JsonEncoder[LocalDate] =
    Json.encoder.contramap[LocalDate](ZioJsonMapper.localDateToJson)

  implicit val localDateDecoder: JsonDecoder[LocalDate] =
    Json.decoder.mapOrFail[LocalDate] { dateObj =>
      ZioJsonMapper
        .jsonToDateString(dateObj)
        .flatMap(ldStr => Try(LocalDate.parse(ldStr.slice(0, 10))).toOption)
        .toRight(s"$dateObj is not a valid local date object")
    }

  implicit val uuidEncoder: JsonEncoder[UUID] =
    Json.encoder.contramap[UUID](ZioJsonMapper.uuidToJson)

  implicit val uuidDecoder: JsonDecoder[UUID] =
    Json.decoder.mapOrFail[UUID] { uuidObj =>
      Try(ZioJsonMapper.jsonToUuid(uuidObj)).toEither.left.map(_.getMessage)
    }

  implicit val binaryEncoder: JsonEncoder[Array[Byte]] =
    Json.encoder.contramap[Array[Byte]](ZioJsonMapper.binaryArrayToJson)

  implicit val binaryDecoder: JsonDecoder[Array[Byte]] =
    Json.decoder.mapOrFail[Array[Byte]] { json =>
      ZioJsonMapper
        .jsonToBinaryBase64(json)
        .toRight(s"$json is not a valid binary object")
        .flatMap(base64 => Try(Base64.getDecoder.decode(base64)).toEither.left.map(_.getMessage))
    }

  implicit def deriveZioJsonCodecProvider[T: ClassTag](implicit enc: JsonEncoder[T], dec: JsonDecoder[T]): MongoCodecProvider[T] =
    new MongoCodecProvider[T] {
      override def get: CodecProvider = codecProvider[T](
        enc.toJsonAST(_).left.map(MongoJsonParsingException(_, None)).fold(throw _, ZioJsonMapper.toBson),
        ZioJsonMapper.fromBson(_).flatMap(j => dec.fromJsonAST(j).left.map(e => MongoJsonParsingException(e, Some(j.toString))))
      )
    }
}
