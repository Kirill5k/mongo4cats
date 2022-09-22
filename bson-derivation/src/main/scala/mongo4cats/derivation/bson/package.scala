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

import mongo4cats.codecs.CodecRegistry
import mongo4cats.collection.GenericMongoCollection
import mongo4cats.database.GenericMongoDatabase
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.{BsonValueCodec, Codec, DecoderContext, EncoderContext, ObjectIdCodec}
import org.bson.{AbstractBsonReader, BsonArray, BsonDocument, BsonElement, BsonReader, BsonValue, BsonWriter}

import java.util
import scala.reflect.ClassTag

package object bson {

  private[bson] val bsonValueCodecSingleton: Codec[BsonValue]   = new BsonValueCodec()
  private[bson] val bsonEncoderContextSingleton: EncoderContext = EncoderContext.builder().build()
  private[bson] val bsonDecoderContextSingleton: DecoderContext = DecoderContext.builder().build()
  private val objectIdCodecRegistry                             = CodecRegistries.fromCodecs(new ObjectIdCodec())

  implicit final class FastDbOps[F[_], S[_]](db: GenericMongoDatabase[F, S]) {

    def fastCollection[A](name: String)(implicit
        ctA: ClassTag[A],
        encA: BsonEncoder[A],
        decA: BsonDecoder[A]
    ): F[GenericMongoCollection[F, A, S]] =
      db.getCollection[A](name, bsonMongoCodecRegistry[A])
  }

  def bsonMongoCodecRegistry[A](implicit
      ctA: ClassTag[A],
      encA: BsonEncoder[A],
      decA: BsonDecoder[A]
  ): CodecRegistry = {
    val classA: Class[A] = ctA.runtimeClass.asInstanceOf[Class[A]]

    val javaCodecSingleton: Codec[A] =
      new Codec[A] {
        override def encode(writer: BsonWriter, a: A, encoderContext: EncoderContext): Unit =
          // println("-------------")
          // java.lang.Thread.currentThread.getStackTrace.foreach(println)
          // {
          //  val w = writer.asInstanceOf[BsonWriterDecorator]
          //  val c = classOf[BsonWriterDecorator]
          //  val f = c.getDeclaredField("bsonWriter")
          //  f.setAccessible(true)
          //
          //  val bw     = f.get(w).asInstanceOf[BsonBinaryWriter]
          //  val output = bw.getBsonOutput
          //  println(s"size: ${output.getSize}, pos: ${output.getPosition}, ${bw.getClass}, ${output.getClass}")
          // }
          encA.unsafeBsonEncode(writer, a, encoderContext)

        override def decode(reader: BsonReader, decoderContext: DecoderContext): A =
          decA.unsafeDecode(reader.asInstanceOf[AbstractBsonReader], decoderContext)

        override def getEncoderClass: Class[A] = classA
      }

    CodecRegistry.merge(
      new CodecRegistry {
        override def get[T](classT: Class[T]): Codec[T] =
          if ((classT eq classA) || classA.isAssignableFrom(classT)) javaCodecSingleton.asInstanceOf[Codec[T]] else null

        override def get[T](classT: Class[T], registry: CodecRegistry): Codec[T] =
          if ((classT eq classA) || classA.isAssignableFrom(classT)) javaCodecSingleton.asInstanceOf[Codec[T]] else null
      },
      objectIdCodecRegistry
    )
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
