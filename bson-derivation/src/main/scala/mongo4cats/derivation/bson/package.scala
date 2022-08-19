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

package mongo4cats.derivation

import mongo4cats.codecs.{CodecRegistry, MongoCodecProvider}
import mongo4cats.derivation.bson.tag.@@
import org.bson.codecs.{BsonValueCodec, Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.CodecProvider
import org.bson.{BsonArray, BsonDocument, BsonElement, BsonReader, BsonValue, BsonWriter}

import java.util
import scala.reflect.ClassTag

package object bson {

  type Fast[A] = A @@ BsonValue

  // Copied From `shapeless.tag`.
  object tag {
    def apply[U] = Tagger.asInstanceOf[Tagger[U]]

    trait Tagged[U] extends Any

    type @@[+T, U] = T with Tagged[U]

    class Tagger[U] {
      def apply[T](t: T): T @@ U = t.asInstanceOf[T @@ U]
    }

    private object Tagger extends Tagger[Nothing]
  }

  implicit def toFast[A](a: A): Fast[A] =
    tag[BsonValue].apply[A](a)

  implicit def bsonClassTag[A](implicit ctA: ClassTag[A]): ClassTag[Fast[A]] =
    new ClassTag[Fast[A]] {
      override val runtimeClass: Class[_] = ctA.runtimeClass
    }

  private[bson] val bsonValueCodecSingleton: Codec[BsonValue] = new BsonValueCodec()

  implicit def bsonMongoCodecProvider[A](implicit
      ctA: ClassTag[A],
      encA: BsonEncoder[A],
      decA: BsonDecoder[A]
  ): MongoCodecProvider[Fast[A]] = {
    val classA: Class[A] =
      ctA.runtimeClass.asInstanceOf[Class[A]]

    val javaCodecSingleton: Codec[A] =
      new Codec[A] {
        override def encode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit =
          encA.useJavaEncoderFirst(writer, a, encoderContext)

        override def getEncoderClass: Class[A] = classA

        override def decode(reader: BsonReader, decoderContext: DecoderContext): A =
          decA(bsonValueCodecSingleton.decode(reader, decoderContext)) match {
            case Right(a) => a
            case Left(ex) => throw ex
          }
      }

    new MongoCodecProvider[Fast[A]] {
      override val get: CodecProvider =
        new CodecProvider {
          override def get[T](classT: Class[T], registry: CodecRegistry): Codec[T] =
            if (classT == classA || classA.isAssignableFrom(classT)) javaCodecSingleton.asInstanceOf[Codec[T]]
            else null
        }
    }
  }

  implicit class BsonValueOps(val bsonValue: BsonValue) extends AnyVal {

    def deepDropNullValues: BsonValue =
      deepDropNullValuesCreatingBsonValuesOnlyIfModified._1

    /** Drop the entries with a null value if this is an object or array. Return true if the BsonValue had been modified. */
    private[derivation] def deepDropNullValuesCreatingBsonValuesOnlyIfModified: (BsonValue, Boolean) =
      bsonValue match {
        case doc: BsonDocument =>
          val newElements = new util.ArrayList[BsonElement](doc.size())
          var modified    = false
          doc
            .entrySet()
            .forEach { entry =>
              val entryBsonValue: BsonValue = entry.getValue
              if (entryBsonValue == null || entryBsonValue.isNull) {
                modified = true
              } else {
                val (newValue, valueModified) = entryBsonValue.deepDropNullValuesCreatingBsonValuesOnlyIfModified
                modified = modified || valueModified
                newElements.add(new BsonElement(entry.getKey, newValue))
                ()
              }
            }

          (if (modified) new BsonDocument(newElements) else doc, modified)

        case array: BsonArray =>
          val newValues = new util.ArrayList[BsonValue](array.size())
          var modified  = false
          array
            .forEach { arrayItem =>
              if (arrayItem == null || arrayItem.isNull) {
                modified = true
              } else {
                val (newValue, valueModified) = arrayItem.deepDropNullValuesCreatingBsonValuesOnlyIfModified
                modified = modified || valueModified
                newValues.add(newValue)
                ()
              }
            }

          (if (modified) new BsonArray(newValues) else array, modified)

        case other => (other, false)
      }
  }

}
