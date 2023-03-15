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

import com.mongodb.MongoClientException
import mongo4cats.codecs.{CodecRegistry, ContainerValueReader, ContainerValueWriter}
import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.CodecProvider

final case class MongoJsonParsingException(message: String, json: Option[String] = None) extends MongoClientException(message)

private[mongo4cats] object json {

  trait JsonMapper[J] {
    def toBson(json: J): BsonValue

    def fromBson(bson: BsonValue): Either[MongoJsonParsingException, J]
  }

  object JsonMapper {
    val idTag   = "$oid"
    val dateTag = "$date"

    def codecProvider[T](
        toBson: Any => BsonValue,
        fromBson: BsonValue => Either[MongoJsonParsingException, T],
        classT: Class[T]
    ): CodecProvider =
      new CodecProvider {
        override def get[Y](classY: Class[Y], registry: CodecRegistry): Codec[Y] =
          if (classY == classT || classT.isAssignableFrom(classY))
            new Codec[Y] {
              override def getEncoderClass: Class[Y] = classY

              override def encode(writer: BsonWriter, t: Y, encoderContext: EncoderContext): Unit =
                ContainerValueWriter.writeBsonValue(toBson(t.asInstanceOf[T]), writer)

              override def decode(reader: BsonReader, decoderContext: DecoderContext): Y =
                (for {
                  bson <- ContainerValueReader
                    .readBsonValue(reader)
                    .toRight(MongoJsonParsingException(s"Unable to read bson value for ${classY.getName} class"))
                  result <- fromBson(bson)
                } yield result).fold(throw _, _.asInstanceOf[Y])
            }
          else null // scalastyle:ignore
      }
  }
}
