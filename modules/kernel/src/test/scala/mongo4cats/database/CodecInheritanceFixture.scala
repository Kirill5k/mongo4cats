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

package mongo4cats.database

import com.mongodb.MongoNamespace
import com.mongodb.reactivestreams.client.{MongoCollection => JMongoCollection, MongoDatabase => JMongoDatabase}
import mongo4cats.codecs.{CodecRegistry, MongoCodecProvider}
import org.bson.{BsonReader, BsonWriter}
import org.bson.codecs.{Codec, DecoderContext, EncoderContext}
import org.bson.codecs.configuration.{CodecProvider, CodecRegistries}

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import scala.reflect.ClassTag

/** Immutable driver handles for codec lookup tests, without a MongoDB client or server. */
object CodecInheritanceFixture {
  final case class Marker(value: String)
  final case class OtherMarker(value: String)

  def codec[T: ClassTag]: Codec[T] = new Codec[T] {
    override def getEncoderClass: Class[T] = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    override def encode(writer: BsonWriter, value: T, context: EncoderContext): Unit =
      throw new UnsupportedOperationException("Codec fixture only supports lookup")
    override def decode(reader: BsonReader, context: DecoderContext): T =
      throw new UnsupportedOperationException("Codec fixture only supports lookup")
  }

  def registry(codecs: Codec[_]*): CodecRegistry = CodecRegistries.fromCodecs(codecs: _*)

  def provider[T](codec: Codec[T]): MongoCodecProvider[T] = new MongoCodecProvider[T] {
    override def get: CodecProvider = new CodecProvider {
      override def get[A](clazz: Class[A], registry: CodecRegistry): Codec[A] =
        if (clazz == codec.getEncoderClass) codec.asInstanceOf[Codec[A]] else null
    }
  }

  def database(registry: CodecRegistry = CodecRegistry.Default): JMongoDatabase =
    proxy(classOf[JMongoDatabase]) { (method, args) =>
      method match {
        case "getName"           => "codec-inheritance"
        case "getCodecRegistry"  => registry
        case "withCodecRegistry" => database(args(0).asInstanceOf[CodecRegistry])
        case "getCollection"     =>
          val documentClass = if (args.length == 2) args(1).asInstanceOf[Class[_]] else classOf[org.bson.Document]
          collection(args(0).asInstanceOf[String], documentClass, registry)
        case _ => throw new UnsupportedOperationException(s"Unexpected database call: $method")
      }
    }

  private def collection(name: String, documentClass: Class[_], registry: CodecRegistry): JMongoCollection[AnyRef] =
    proxy(classOf[JMongoCollection[AnyRef]]) { (method, args) =>
      method match {
        case "getNamespace"      => new MongoNamespace("codec-inheritance", name)
        case "getDocumentClass"  => documentClass
        case "getCodecRegistry"  => registry
        case "withCodecRegistry" => collection(name, documentClass, args(0).asInstanceOf[CodecRegistry])
        case "withDocumentClass" => collection(name, args(0).asInstanceOf[Class[_]], registry)
        case _                   => throw new UnsupportedOperationException(s"Unexpected collection call: $method")
      }
    }

  private def proxy[T](interface: Class[T])(handle: (String, Array[AnyRef]) => AnyRef): T = {
    val handler = new InvocationHandler {
      override def invoke(instance: Any, method: Method, args: Array[AnyRef]): AnyRef = method.getName match {
        case "toString" => s"CodecInheritanceFixture(${interface.getSimpleName})"
        case "hashCode" => Int.box(System.identityHashCode(instance.asInstanceOf[AnyRef]))
        case "equals"   => Boolean.box(instance.asInstanceOf[AnyRef] eq args(0))
        case _          => handle(method.getName, args)
      }
    }
    interface.cast(Proxy.newProxyInstance(interface.getClassLoader, Array[Class[_]](interface), handler))
  }
}
