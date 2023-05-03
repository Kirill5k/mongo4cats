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

package mongo4cats.codecs

import mongo4cats.Clazz
import org.bson.codecs.configuration.CodecProvider
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.types.Decimal128
import org.bson.{BsonInvalidOperationException, BsonReader, BsonType, BsonWriter}

private object BigDecimalCodec extends Codec[BigDecimal] {

  override def encode(writer: BsonWriter, bd: BigDecimal, encoderContext: EncoderContext): Unit =
    writer.writeDecimal128(new Decimal128(bd.bigDecimal))

  override def getEncoderClass: Class[BigDecimal] = Clazz.tag[BigDecimal]

  override def decode(reader: BsonReader, decoderContext: DecoderContext): BigDecimal =
    reader.getCurrentBsonType match {
      case BsonType.DECIMAL128 => reader.readDecimal128().bigDecimalValue()
      case otherType           => throw new BsonInvalidOperationException(s"Unexpected bson type $otherType when reading BigDecimal")
    }
}

object BigDecimalCodecProvider extends CodecProvider {
  override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
    if (classOf[BigDecimal].isAssignableFrom(clazz)) BigDecimalCodec.asInstanceOf[Codec[T]] else null
}
