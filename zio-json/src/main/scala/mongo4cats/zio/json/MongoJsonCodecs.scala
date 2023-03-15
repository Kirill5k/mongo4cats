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

import mongo4cats.Clazz
import mongo4cats.bson.json.JsonMapper
import mongo4cats.bson.{BsonValue, BsonValueDecoder, BsonValueEncoder, Document, MongoJsonParsingException, ObjectId}
import mongo4cats.bson.syntax._
import mongo4cats.codecs.{ContainerValueReader, ContainerValueWriter, MongoCodecProvider}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}
import zio.json.{JsonDecoder, JsonEncoder}
import zio.json.ast.Json

import java.time.{Instant, LocalDate}
import scala.reflect.ClassTag

trait MongoJsonCodecs {
  private val emptyJsonObject = Json.Obj()

  implicit def deriveJsonBsonValueEncoder[A](implicit e: JsonEncoder[A]): BsonValueEncoder[A] =
    value => ZioJsonMapper.toBson(e.toJsonAST(value).toOption.get)

  implicit def deriveJsonBsonValueDecoder[A](implicit d: JsonDecoder[A]): BsonValueDecoder[A] =
    bson => ZioJsonMapper.fromBson(bson).flatMap(d.fromJsonAST).toOption

  implicit val documentEncoder: JsonEncoder[Document] =
    Json.encoder.contramap[Document](d => ZioJsonMapper.fromBsonOpt(BsonValue.document(d)).getOrElse(emptyJsonObject))

  implicit val documentDecoder: JsonDecoder[Document] =
    Json.decoder.mapOrFail(j => ZioJsonMapper.toBson(j).asDocument.toRight(s"$j is not a valid document"))

  implicit val objectIdEncoder: JsonEncoder[ObjectId] =
    Json.encoder.contramap[ObjectId](i => Json.Obj(JsonMapper.idTag -> Json.Str(i.toHexString)))

  implicit val objectIdDecoder: JsonDecoder[ObjectId] =
    Json.decoder.mapOrFail[ObjectId] { id =>
      (for {
        obj  <- id.asObject
        json <- obj.get(JsonMapper.idTag)
        str  <- json.asString
      } yield ObjectId(str)).toRight(s"$id is not a valid object id")
    }

  implicit val instantEncoder: JsonEncoder[Instant] =
    Json.encoder.contramap[Instant](i => Json.Obj(JsonMapper.dateTag -> Json.Str(i.toString)))

  implicit val instantDecoder: JsonDecoder[Instant] =
    Json.decoder.mapOrFail[Instant] { dateObj =>
      (for {
        obj  <- dateObj.asObject
        date <- obj.get(JsonMapper.dateTag)
        ts   <- date.asString
      } yield Instant.parse(ts)).toRight(s"$dateObj is not a valid instant object")
    }

  implicit val localDateEncoder: JsonEncoder[LocalDate] =
    Json.encoder.contramap[LocalDate](i => Json.Obj(JsonMapper.dateTag -> Json.Str(i.toString)))

  implicit val localDateDecoder: JsonDecoder[LocalDate] =
    Json.decoder.mapOrFail[LocalDate] { dateObj =>
      (for {
        obj  <- dateObj.asObject
        date <- obj.get(JsonMapper.dateTag)
        ld   <- date.asString
      } yield LocalDate.parse(ld.slice(0, 10))).toRight(s"$dateObj is not a valid local date object")
    }

  implicit def deriveZioJsonCodecProvider[A: JsonEncoder: JsonDecoder: ClassTag]: MongoCodecProvider[A] =
    new MongoCodecProvider[A] {
      implicit val classT: Class[A]   = Clazz.tag[A]
      override def get: CodecProvider = zioJsonBasedCodecProvider[A]
    }

  private def zioJsonBasedCodecProvider[A](implicit enc: JsonEncoder[A], dec: JsonDecoder[A], classT: Class[A]): CodecProvider =
    new CodecProvider {
      override def get[Y](classY: Class[Y], registry: CodecRegistry): Codec[Y] =
        if (classY == classT || classT.isAssignableFrom(classY)) {
          new Codec[Y] {
            override def getEncoderClass: Class[Y] = classY
            override def encode(writer: BsonWriter, t: Y, encoderContext: EncoderContext): Unit =
              ContainerValueWriter.writeBsonValue(t.asInstanceOf[A].toBson, writer)

            override def decode(reader: BsonReader, decoderContext: DecoderContext): Y =
              (for {
                bson <- ContainerValueReader
                  .readBsonValue(reader)
                  .toRight(MongoJsonParsingException(s"Unable to read bson value for ${classY.getName} class"))
                json   <- ZioJsonMapper.fromBson(bson)
                result <- dec.fromJsonAST(json).left.map(e => MongoJsonParsingException(e, Some(json.toString)))
              } yield result).fold(throw _, _.asInstanceOf[Y])
          }
        } else null // scalastyle:ignore
    }
}
