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
import org.bson.types.Decimal128

import java.time.{Instant, LocalDate, ZoneOffset}
import scala.reflect.ClassTag
import scala.util.control.NonFatal

private[mongo4cats] object json {

  object Tag {
    val id            = "$" + "oid"
    val date          = "$" + "date"
    val binary        = "$" + "binary"
    val numberLong    = "$" + "numberLong"
    val numberDecimal = "$" + "numberDecimal"
  }

  object ExtendedJson {
    def parseDateString(value: String): Instant = {
      val instant =
        if (value.length == 10) LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC)
        else Instant.parse(value)
      // BSON dates must fit into signed 64-bit milliseconds.
      val _ = instant.toEpochMilli
      instant
    }

    def parseDateMillis(value: String): Instant =
      Instant.ofEpochMilli(java.lang.Long.parseLong(value))

    def parseDecimal(value: String): BigDecimal = {
      val decimal = Decimal128.parse(value)
      require(decimal.isFinite, "Non-finite Decimal128 values cannot be represented as BigDecimal")
      // bigDecimalValue also rejects negative zero, whose sign BigDecimal cannot preserve.
      BigDecimal(decimal.bigDecimalValue())
    }
  }

  trait JsonMapper[J] {
    def toBson(json: J): BsonValue

    def toBsonEither(json: J): Either[MongoJsonParsingException, BsonValue] =
      try Right(toBson(json))
      catch {
        case error: MongoJsonParsingException => Left(error)
        case NonFatal(error)                  =>
          Left(MongoJsonParsingException(Option(error.getMessage).getOrElse(error.getClass.getSimpleName), Some(json.toString)))
      }

    def fromBson(bson: BsonValue): Either[MongoJsonParsingException, J]
  }

  def codecProvider[T: ClassTag](
      toBson: T => BsonValue,
      fromBson: BsonValue => Either[MongoJsonParsingException, T]
  ): CodecProvider =
    new CodecProvider {
      private val classT                                                       = Clazz.tag[T]
      override def get[Y](classY: Class[Y], registry: CodecRegistry): Codec[Y] =
        if (classY == classT || classT.isAssignableFrom(classY))
          new Codec[Y] {
            override def getEncoderClass: Class[Y]                                              = classY
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
