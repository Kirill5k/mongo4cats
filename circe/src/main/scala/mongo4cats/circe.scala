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

package mongo4cats

import com.mongodb.MongoClientException
import io.circe.parser.{decode => circeDecode}
import io.circe.{Decoder, Encoder}
import mongo4cats.database.{MongoCodecProvider, MongoCollectionF, MongoDatabaseF}
import org.bson.codecs.{Codec, DecoderContext, DocumentCodec, EncoderContext}
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistry}
import org.bson.{BsonReader, BsonWriter, Document}

import scala.reflect.ClassTag

object circe extends JsonCodecs {

  final case class MongoJsonParsingException(jsonString: String, message: String) extends MongoClientException(message)

  implicit def circeCodecProvider[T: Encoder: Decoder: ClassTag]: MongoCodecProvider[T] =
    new MongoCodecProvider[T] {
      implicit val classT: Class[T]   = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
      override def get: CodecProvider = circeBasedCodecProvider[T]
    }

  implicit final class MongoDatabaseFOps[F[_]](private val db: MongoDatabaseF[F]) extends AnyVal {
    def getCollectionWithCirceCodecs[T: ClassTag: Encoder: Decoder](name: String): F[MongoCollectionF[T]] = {
      implicit val classT: Class[T] = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
      val codecs: CodecRegistry     = fromRegistries(fromProviders(circeBasedCodecProvider[T]), MongoDatabaseF.DefaultCodecRegistry)
      db.getCollectionWithCodecRegistry[T](name, codecs)
    }
  }

  private def circeBasedCodecProvider[T](implicit enc: Encoder[T], dec: Decoder[T], classT: Class[T]): CodecProvider =
    new CodecProvider {
      override def get[Y](classY: Class[Y], registry: CodecRegistry): Codec[Y] =
        if (classY == classT) {
          new Codec[Y] {
            private val documentCodec: Codec[Document] = new DocumentCodec(registry).asInstanceOf[Codec[Document]]

            override def encode(writer: BsonWriter, t: Y, encoderContext: EncoderContext): Unit = {
              val document = Document.parse(enc(t.asInstanceOf[T]).noSpaces)
              documentCodec.encode(writer, document, encoderContext)
            }

            override def getEncoderClass: Class[Y] = classY

            override def decode(reader: BsonReader, decoderContext: DecoderContext): Y = {
              val documentJson = documentCodec.decode(reader, decoderContext).toJson()
              circeDecode[T](documentJson).fold(e => throw MongoJsonParsingException(documentJson, e.getMessage), _.asInstanceOf[Y])
            }
          }
        } else {
          null // scalastyle:ignore
        }
    }

}
