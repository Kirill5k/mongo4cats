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

import mongo4cats.Clazz
import mongo4cats.codecs.{CodecRegistry, ContainerValueReader, ContainerValueWriter}
import mongo4cats.errors.MongoJsonParsingException
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.{BsonReader, BsonWriter}

import scala.reflect.ClassTag

private[mongo4cats] object json {

  object Tag {
    val id   = "$" + "oid"
    val date = "$" + "date"
  }

  trait JsonMapper[J] {
    def toBson(json: J): BsonValue

    def fromBson(bson: BsonValue): Either[MongoJsonParsingException, J]
  }

  def codecProvider[T: ClassTag](
      toBson: T => BsonValue,
      fromBson: BsonValue => Either[MongoJsonParsingException, T]
  ): CodecProvider =
    new CodecProvider {
      private val classT = Clazz.tag[T]
      override def get[Y](classY: Class[Y], registry: CodecRegistry): Codec[Y] =
        if (classY == classT || classT.isAssignableFrom(classY))
          new Codec[Y] {
            override def getEncoderClass: Class[Y] = classY
            override def encode(writer: BsonWriter, t: Y, encoderContext: EncoderContext): Unit =
              ContainerValueWriter.writeBsonValue(toBson(t.asInstanceOf[T]), writer)
            override def decode(reader: BsonReader, decoderContext: DecoderContext): Y =
              ContainerValueReader
                .readBsonValue(reader)
                .toRight(MongoJsonParsingException(s"Unable to read bson value for ${classY.getName} class"))
                .flatMap(fromBson)
                .fold(throw _, _.asInstanceOf[Y])
          }
        else null // scalastyle:ignore
    }
}
