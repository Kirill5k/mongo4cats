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
import io.circe.parser.{decode => circeDecode}
import mongo4cats.codecs.MongoCodecProvider
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}
import org.bson.codecs._
import org.bson.types.ObjectId
import org.bson.{BsonReader, BsonType, BsonWriter, Document}

import java.time.{Instant, LocalDate}
import scala.reflect.ClassTag
import scala.util.Try

final case class MongoJsonParsingException(jsonString: String, message: String) extends MongoClientException(message)

trait MongoJsonCodecs {

  implicit val encodeObjectId: Encoder[ObjectId] =
    Encoder.encodeJsonObject.contramap[ObjectId](i => JsonObject("$oid" -> Json.fromString(i.toHexString)))
  implicit val decodeObjectId: Decoder[ObjectId] =
    Decoder.decodeJsonObject.emapTry(id => Try(new ObjectId(id("$oid").flatMap(_.asString).get)))

  implicit val encodeInstant: Encoder[Instant] =
    Encoder.encodeJsonObject.contramap[Instant](i => JsonObject("$date" -> Json.fromString(i.toString)))
  implicit val decodeInstant: Decoder[Instant] =
    Decoder.decodeJsonObject.emapTry(dateObj => Try(Instant.parse(dateObj("$date").flatMap(_.asString).get)))

  implicit val encodeLocalDate: Encoder[LocalDate] =
    Encoder.encodeJsonObject.contramap[LocalDate](i => JsonObject("$date" -> Json.fromString(i.toString)))
  implicit val decodeLocalDate: Decoder[LocalDate] =
    Decoder.decodeJsonObject.emapTry(dateObj => Try(LocalDate.parse(dateObj("$date").flatMap(_.asString).map(_.slice(0, 10)).get)))

  implicit def circeCodecProvider[T: Encoder: Decoder: ClassTag]: MongoCodecProvider[T] =
    new MongoCodecProvider[T] {
      implicit val classT: Class[T]   = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
      override def get: CodecProvider = circeBasedCodecProvider[T]
    }

  private def circeBasedCodecProvider[T](implicit enc: Encoder[T], dec: Decoder[T], classT: Class[T]): CodecProvider =
    new CodecProvider {
      override def get[Y](classY: Class[Y], registry: CodecRegistry): Codec[Y] =
        if (classY == classT || classT.isAssignableFrom(classY)) {
          new Codec[Y] {
            private val documentCodec: Codec[Document] = new DocumentCodec(registry).asInstanceOf[Codec[Document]]
            private val stringCodec: Codec[String]     = new StringCodec()

            override def encode(writer: BsonWriter, t: Y, encoderContext: EncoderContext): Unit = {
              val json = enc(t.asInstanceOf[T])
              if (json.isObject) {
                val document = Document.parse(json.noSpaces)
                documentCodec.encode(writer, document, encoderContext)
              } else {
                stringCodec.encode(writer, json.noSpaces.replaceAll("\"", ""), encoderContext)
              }
            }

            override def getEncoderClass: Class[Y] = classY

            override def decode(reader: BsonReader, decoderContext: DecoderContext): Y =
              reader.getCurrentBsonType match {
                case BsonType.DOCUMENT =>
                  val json = documentCodec.decode(reader, decoderContext).toJson()
                  circeDecode[T](json).fold(e => throw MongoJsonParsingException(json, e.getMessage), _.asInstanceOf[Y])
                case _ =>
                  val string = stringCodec.decode(reader, decoderContext)
                  dec
                    .decodeJson(Json.fromString(string))
                    .fold(e => throw MongoJsonParsingException(string, e.getMessage), _.asInstanceOf[Y])
              }

          }
        } else {
          null // scalastyle:ignore
        }
    }
}
