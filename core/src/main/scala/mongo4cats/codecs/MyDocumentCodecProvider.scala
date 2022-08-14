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

import mongo4cats.bson.MyDocument
import org.bson.codecs.{
  BsonTypeClassMap,
  BsonTypeCodecMap,
  BsonValueCodecProvider,
  Codec,
  CollectibleCodec,
  DecoderContext,
  EncoderContext,
  IdGenerator,
  ObjectIdGenerator,
  OverridableUuidRepresentationCodec,
  ValueCodecProvider
}
import org.bson.{BsonDocument, BsonDocumentWriter, BsonReader, BsonType, BsonValue, BsonWriter, Transformer, UuidRepresentation}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries}
import org.bson.codecs.jsr310.Jsr310CodecProvider

import scala.annotation.tailrec
import scala.reflect.ClassTag

final private class MyDocumentCodec(
    private val registry: CodecRegistry,
    private val valueTransformer: Transformer,
    private val bsonTypeClassMap: BsonTypeClassMap,
    private val uuidRepresentation: UuidRepresentation,
    private val idGenerator: IdGenerator
) extends CollectibleCodec[MyDocument] with OverridableUuidRepresentationCodec[MyDocument] {

  private val idFieldName = "_id"

  private val bsonTypeCodecMap: BsonTypeCodecMap = new BsonTypeCodecMap(bsonTypeClassMap, registry)

  override def getEncoderClass: Class[MyDocument] =
    implicitly[ClassTag[MyDocument]].runtimeClass.asInstanceOf[Class[MyDocument]]

  override def withUuidRepresentation(newUuidRepresentation: UuidRepresentation): Codec[MyDocument] =
    new MyDocumentCodec(registry, valueTransformer, bsonTypeClassMap, newUuidRepresentation, idGenerator)

  override def encode(writer: BsonWriter, document: MyDocument, encoderContext: EncoderContext): Unit = {
    writer.writeStartDocument()

    if (encoderContext.isEncodingCollectibleDocument && document.contains(idFieldName)) {
      writer.writeName(idFieldName)
      ContainerValueReader.write(writer, encoderContext, document.get(idFieldName), registry)
    }

    document.fields
      .filterNot(_._1 == idFieldName && encoderContext.isEncodingCollectibleDocument)
      .foreach { case (key, value) =>
        writer.writeName(key)
        ContainerValueReader.write(writer, encoderContext, Option(value), registry)
      }

    writer.writeEndDocument()
  }

  override def decode(reader: BsonReader, decoderContext: DecoderContext): MyDocument = {
    @tailrec
    def go(fields: Map[String, Any]): MyDocument =
      if (reader.readBsonType() != BsonType.END_OF_DOCUMENT) {
        val key = reader.readName
        val value =
          ContainerValueReader.readDocumentField(reader, decoderContext, bsonTypeCodecMap, uuidRepresentation, registry, valueTransformer)
        go(fields + (key -> value))
      } else {
        MyDocument(fields)
      }

    reader.readStartDocument()
    val result = go(Map.empty)
    reader.readEndDocument()
    result
  }

  override def generateIdIfAbsentFromDocument(document: MyDocument): MyDocument =
    document.add(idFieldName, idGenerator.generate())

  override def documentHasId(document: MyDocument): Boolean =
    document.contains(idFieldName)

  override def getDocumentId(document: MyDocument): BsonValue =
    document.get[Any](idFieldName) match {
      case None                => throw new IllegalStateException(s"The document does not contain an $idFieldName")
      case Some(id: BsonValue) => id
      case Some(id) =>
        val idHoldingDocument = new BsonDocument
        val writer            = new BsonDocumentWriter(idHoldingDocument)
        writer.writeStartDocument()
        writer.writeName(idFieldName)
        ContainerValueReader.write(writer, EncoderContext.builder.build, Option(id), registry)
        writer.writeEndDocument()
        idHoldingDocument.get(idFieldName)
    }
}

object MyDocumentCodecProvider extends CodecProvider {
  private[mongo4cats] val DefaultCodec: Codec[MyDocument] = new MyDocumentCodec(
    CodecRegistries.fromProviders(
      new ValueCodecProvider,
      new BsonValueCodecProvider,
      new Jsr310CodecProvider,
      MyDocumentCodecProvider,
      IterableCodecProvider,
      OptionCodecProvider,
      MapCodecProvider
    ),
    valueTransformer = (objectToTransform: Any) => objectToTransform.asInstanceOf[AnyRef],
    bsonTypeClassMap = new BsonTypeClassMap(),
    uuidRepresentation = UuidRepresentation.STANDARD,
    idGenerator = new ObjectIdGenerator
  )

  override def get[T](clazz: Class[T], registry: CodecRegistry): Codec[T] =
    if (classOf[MyDocument].isAssignableFrom(clazz))
      new MyDocumentCodec(
        registry,
        valueTransformer = (objectToTransform: Any) => objectToTransform.asInstanceOf[AnyRef],
        bsonTypeClassMap = new BsonTypeClassMap(),
        uuidRepresentation = UuidRepresentation.UNSPECIFIED,
        idGenerator = new ObjectIdGenerator
      ).asInstanceOf[Codec[T]]
    else null
}
