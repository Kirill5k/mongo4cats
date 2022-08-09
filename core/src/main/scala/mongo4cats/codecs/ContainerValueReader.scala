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

import org.bson.{BsonReader, BsonType, Transformer, UuidRepresentation}
import org.bson.codecs.{BsonTypeCodecMap, DecoderContext}

import java.util.UUID

private[codecs] object ContainerValueReader {

  private[codecs] def read(
      reader: BsonReader,
      decoderContext: DecoderContext,
      bsonTypeCodecMap: BsonTypeCodecMap,
      uuidRepresentation: UuidRepresentation,
      registry: CodecRegistry,
      valueTransformer: Transformer
  ): AnyRef = {
    val bsonType = reader.getCurrentBsonType
    if (bsonType == BsonType.NULL) {
      reader.readNull()
      null
    } else {
      val codec = reader.peekBinarySubType match {
        case 3 if bsonType == BsonType.BINARY && reader.peekBinarySize == 16 && isLegacyUuid(uuidRepresentation) =>
          registry.get(classOf[UUID])
        case 4 if bsonType == BsonType.BINARY && reader.peekBinarySize == 16 && uuidRepresentation == UuidRepresentation.STANDARD =>
          registry.get(classOf[UUID])
        case _ => bsonTypeCodecMap.get(bsonType)
      }
      valueTransformer.transform(codec.decode(reader, decoderContext))
    }
  }

  private def isLegacyUuid(uuidRepresentation: UuidRepresentation): Boolean =
    uuidRepresentation == UuidRepresentation.JAVA_LEGACY ||
      uuidRepresentation == UuidRepresentation.C_SHARP_LEGACY ||
      uuidRepresentation == UuidRepresentation.PYTHON_LEGACY
}
