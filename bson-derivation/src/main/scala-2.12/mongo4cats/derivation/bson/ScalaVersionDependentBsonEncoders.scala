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

import mongo4cats.derivation.bson.BsonEncoder.instanceFromJavaEncoder
import org.bson._
import org.bson.codecs.EncoderContext

import scala.collection.Iterable

trait ScalaVersionDependentBsonEncoders {

  implicit final def encodeIterable[L[_] <: Iterable[_], A](implicit encA: BsonEncoder[A]): BsonEncoder[L[A]] =
    instanceFromJavaEncoder(new JavaEncoder[L[A]] {
      override def encode(writer: BsonWriter, value: L[A], encoderContext: EncoderContext): Unit = {
        writer.writeStartArray()
        value.iterator.foreach(a => encA.useJavaEncoderFirst(writer, a.asInstanceOf[A], encoderContext))
        writer.writeEndArray()
      }
    })
}
