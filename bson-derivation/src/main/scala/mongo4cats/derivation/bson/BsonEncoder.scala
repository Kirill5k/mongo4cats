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

package mongo4cats.derivation.bson

import cats.Contravariant
import mongo4cats.AsJava
import mongo4cats.derivation.bson.BsonEncoder.pipeValue
import org.bson.BsonType._
import org.bson.{BsonArray, BsonDocument, BsonDocumentWriter, BsonJavaScriptWithScope, BsonReader, BsonValue, BsonWriter}
import org.bson.codecs.{BsonValueCodec, Codec, DecoderContext, EncoderContext}

/** A type class that provides a conversion from a value of type `A` to a [[BsonValue]] value. */
trait BsonEncoder[A] extends Serializable with AsJava { self =>

  /** Convert a value to BsonValue. */
  def toBsonValue(a: A): BsonValue

  val fromJavaEncoder: JavaEncoder[A]

  def useJavaEncoderFirst(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit = {
    val javaEncoderA = fromJavaEncoder
    if (javaEncoderA == null) bsonValueCodecSingleton.encode(writer, toBsonValue(a), encoderContext)
    else javaEncoderA.encode(writer, a, encoderContext)
  }

  /** Create a new [[BsonEncoder]] by applying a function to a value of type `B` before encoding as an `A`. */
  final def contramap[B](f: B => A): BsonEncoder[B] = {
    val javaEncoderA = fromJavaEncoder

    new BsonEncoder[B] {
      override def toBsonValue(b: B): BsonValue =
        self.toBsonValue(f(b))

      override val fromJavaEncoder: JavaEncoder[B] =
        if (javaEncoderA == null) null
        else
          new JavaEncoder[B] {
            override def encode(writer: BsonWriter, b: B, encoderContext: EncoderContext): Unit =
              javaEncoderA.encode(writer, f(b), encoderContext)
          }
    }
  }

  /** Create a new [[BsonEncoder]] by applying a function to the output of this one.
    */
  final def mapBsonValue(f: BsonValue => BsonValue): BsonEncoder[A] =
    new BsonEncoder[A] {
      override def toBsonValue(a: A): BsonValue = f(self.toBsonValue(a))

      override val fromJavaEncoder: JavaEncoder[A] =
        new JavaEncoder[A] {
          override def encode(writer: BsonWriter, value: A, encoderContext: EncoderContext): Unit =
            bsonValueCodecSingleton.encode(writer, toBsonValue(value), encoderContext)
        }
    }
}

object BsonEncoder {

  def apply[A](implicit ev: BsonEncoder[A]): BsonEncoder[A] = ev

  def instanceWithBsonValue[A](f: A => BsonValue): BsonEncoder[A] =
    new BsonEncoder[A] {
      override def toBsonValue(a: A): BsonValue = f(a)

      override val fromJavaEncoder: JavaEncoder[A] = null
    }

  def instanceFromJavaCodec[A](codec: Codec[A]): BsonEncoder[A] =
    instanceFromJavaEncoder(new JavaEncoder[A] {
      override def encode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit =
        codec.encode(writer, a, encoderContext)
    })

  def instanceFromJavaEncoder[A](javaEncoder: JavaEncoder[A]): BsonEncoder[A] =
    new BsonEncoder[A] {
      val dummyRoot = "d"

      override def toBsonValue(a: A): BsonValue = {
        val bsonDocument = new BsonDocument(1)
        val writer       = new BsonDocumentWriter(bsonDocument)
        writer.writeStartDocument()
        writer.writeName(dummyRoot)
        javaEncoder.encode(writer, a, EncoderContext.builder().build())
        // writer.writeEndDocument()
        bsonDocument.get(dummyRoot)
      }

      override val fromJavaEncoder: JavaEncoder[A] = javaEncoder
    }

  implicit final val bsonEncoderContravariant: Contravariant[BsonEncoder] =
    new Contravariant[BsonEncoder] {
      final def contramap[A, B](e: BsonEncoder[A])(f: B => A): BsonEncoder[B] = e.contramap(f)
    }

  def pipeDocument(writer: BsonWriter, value: BsonDocument): Unit = {
    writer.writeStartDocument()
    value.entrySet.forEach { cur =>
      writer.writeName(cur.getKey)
      pipeValue(writer, cur.getValue)
    }
    writer.writeEndDocument()
  }

  def pipeArray(writer: BsonWriter, array: BsonArray): Unit = {
    writer.writeStartArray()
    array.forEach(pipeValue(writer, _))
    writer.writeEndArray()
  }

  def pipeJavascriptWithScope(writer: BsonWriter, javaScriptWithScope: BsonJavaScriptWithScope): Unit = {
    writer.writeJavaScriptWithScope(javaScriptWithScope.getCode)
    pipeDocument(writer, javaScriptWithScope.getScope)
  }

  def pipeValue(writer: BsonWriter, value: BsonValue): Unit =
    value.getBsonType match {
      case DOCUMENT              => pipeDocument(writer, value.asDocument())
      case ARRAY                 => pipeArray(writer, value.asArray())
      case DOUBLE                => writer.writeDouble(value.asDouble().getValue())
      case STRING                => writer.writeString(value.asString().getValue())
      case BINARY                => writer.writeBinaryData(value.asBinary())
      case UNDEFINED             => writer.writeUndefined();
      case OBJECT_ID             => writer.writeObjectId(value.asObjectId().getValue())
      case BOOLEAN               => writer.writeBoolean(value.asBoolean().getValue());
      case DATE_TIME             => writer.writeDateTime(value.asDateTime().getValue())
      case NULL                  => writer.writeNull();
      case REGULAR_EXPRESSION    => writer.writeRegularExpression(value.asRegularExpression())
      case JAVASCRIPT            => writer.writeJavaScript(value.asJavaScript().getCode())
      case SYMBOL                => writer.writeSymbol(value.asSymbol().getSymbol())
      case JAVASCRIPT_WITH_SCOPE => pipeJavascriptWithScope(writer, value.asJavaScriptWithScope())
      case INT32                 => writer.writeInt32(value.asInt32().getValue())
      case TIMESTAMP             => writer.writeTimestamp(value.asTimestamp())
      case INT64                 => writer.writeInt64(value.asInt64().getValue())
      case DECIMAL128            => writer.writeDecimal128(value.asDecimal128().getValue())
      case MIN_KEY               => writer.writeMinKey();
      case DB_POINTER            => writer.writeDBPointer(value.asDBPointer())
      case MAX_KEY               => writer.writeMaxKey()
      case _                     => throw new IllegalArgumentException("unhandled BSON type: " + value.getBsonType());
    }
}

trait JavaEncoder[A] extends org.bson.codecs.Encoder[A] {

  override def getEncoderClass: Class[A] = ???
}
