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
import mongo4cats.bson.{Document, ObjectId}
import org.bson.codecs.{Codec, CollectibleCodec, DecoderContext, EncoderContext, IdGenerator, ObjectIdGenerator}
import org.bson.codecs.configuration.CodecProvider
import org.bson.{BsonObjectId, BsonReader, BsonValue => JBsonValue, BsonWriter}

final private class DocumentCodec(
    private val idGenerator: IdGenerator
) extends CollectibleCodec[Document] {

  private val idFieldName = "_id"

  override def getEncoderClass: Class[Document] = Clazz.tag[Document]

  override def encode(writer: BsonWriter, document: Document, encoderContext: EncoderContext): Unit =
    ContainerValueWriter.writeBsonDocument(document, writer, Some(idFieldName))

  override def decode(reader: BsonReader, decoderContext: DecoderContext): Document =
    ContainerValueReader.readBsonDocument(reader)

  override def generateIdIfAbsentFromDocument(document: Document): Document =
    if (documentHasId(document)) document else document.add(idFieldName -> idGenerator.generate().asInstanceOf[ObjectId])

  override def documentHasId(document: Document): Boolean =
    document.contains(idFieldName)

  override def getDocumentId(document: Document): JBsonValue =
    document.getObjectId(idFieldName) match {
      case None     => throw new IllegalStateException(s"The document does not contain an $idFieldName")
      case Some(id) => new BsonObjectId(id)
    }
}

object DocumentCodecProvider extends CodecProvider {
  private[mongo4cats] val DefaultCodec: Codec[Document] = new DocumentCodec(new ObjectIdGenerator)

  override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
    if (classOf[Document].isAssignableFrom(clazz)) DefaultCodec.asInstanceOf[Codec[T]] else null
}
