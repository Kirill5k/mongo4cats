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

import com.mongodb.MongoClientException
import io.circe.{Decoder, Encoder, Json, JsonObject}
import mongo4cats.Clazz
import mongo4cats.bson.{BsonValue, BsonValueDecoder, BsonValueEncoder, Document, ObjectId}
import mongo4cats.bson.syntax._
import mongo4cats.codecs.{ContainerValueReader, ContainerValueWriter, MongoCodecProvider}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}

import java.time.{Instant, LocalDate}
import scala.reflect.ClassTag
import scala.util.Try

final case class MongoJsonParsingException(message: String, json: Option[String] = None) extends MongoClientException(message)

trait MongoJsonCodecs {
  private val emptyJsonObject = Json.fromJsonObject(JsonObject.empty)

  implicit def deriveJsonBsonValueDecoder[A](implicit d: Decoder[A]): BsonValueDecoder[A] =
    bson => JsonMapper.fromBson(bson).flatMap(d.decodeJson).toOption

  implicit def deriveJsonBsonValueEncoder[A](implicit e: Encoder[A]): BsonValueEncoder[A] =
    value => JsonMapper.toBson(e(value))

  implicit val documentEncoder: Encoder[Document] =
    Encoder.encodeJson.contramap[Document](d => JsonMapper.fromBsonOpt(BsonValue.document(d)).getOrElse(emptyJsonObject))
  implicit val documentDecoder: Decoder[Document] =
    Decoder.decodeJson.emap(j => JsonMapper.toBson(j).asDocument.toRight(s"$j is not a valid document"))

  implicit val objectIdEncoder: Encoder[ObjectId] =
    Encoder.encodeJsonObject.contramap[ObjectId](i => JsonObject(JsonMapper.idTag -> Json.fromString(i.toHexString)))
  implicit val objectIdDecoder: Decoder[ObjectId] =
    Decoder.decodeJsonObject.emapTry(id => Try(ObjectId(id(JsonMapper.idTag).flatMap(_.asString).get)))

  implicit val instantEncoder: Encoder[Instant] =
    Encoder.encodeJsonObject.contramap[Instant](i => JsonObject(JsonMapper.dateTag -> Json.fromString(i.toString)))
  implicit val instantDecoder: Decoder[Instant] =
    Decoder.decodeJsonObject.emapTry(dateObj => Try(Instant.parse(dateObj(JsonMapper.dateTag).flatMap(_.asString).get)))

  implicit val localDateEncoder: Encoder[LocalDate] =
    Encoder.encodeJsonObject.contramap[LocalDate](i => JsonObject(JsonMapper.dateTag -> Json.fromString(i.toString)))
  implicit val localDateDecoder: Decoder[LocalDate] =
    Decoder.decodeJsonObject.emapTry(dateObj =>
      Try(LocalDate.parse(dateObj(JsonMapper.dateTag).flatMap(_.asString).map(_.slice(0, 10)).get))
    )

  implicit def deriveCirceCodecProvider[T: Encoder: Decoder: ClassTag]: MongoCodecProvider[T] =
    new MongoCodecProvider[T] {
      implicit val classT: Class[T]   = Clazz.tag[T]
      override def get: CodecProvider = circeBasedCodecProvider[T]
    }

  private def circeBasedCodecProvider[T](implicit enc: Encoder[T], dec: Decoder[T], classT: Class[T]): CodecProvider =
    new CodecProvider {
      override def get[Y](classY: Class[Y], registry: CodecRegistry): Codec[Y] =
        if (classY == classT || classT.isAssignableFrom(classY))
          new Codec[Y] {
            override def getEncoderClass: Class[Y] = classY
            override def encode(writer: BsonWriter, t: Y, encoderContext: EncoderContext): Unit =
              ContainerValueWriter.writeBsonValue(t.asInstanceOf[T].toBson, writer)

            override def decode(reader: BsonReader, decoderContext: DecoderContext): Y =
              (for {
                bson <- ContainerValueReader
                  .readBsonValue(reader)
                  .toRight(MongoJsonParsingException(s"Unable to read bson value for ${classY.getName} class"))
                json   <- JsonMapper.fromBson(bson)
                result <- dec.decodeJson(json).left.map(e => MongoJsonParsingException(e.getMessage, Some(json.noSpaces)))
              } yield result).fold(e => throw e, c => c.asInstanceOf[Y])
          }
        else null // scalastyle:ignore
    }
}
