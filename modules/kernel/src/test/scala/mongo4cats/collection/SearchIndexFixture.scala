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

package mongo4cats.collection

import com.mongodb.client.model.{SearchIndexModel => JSearchIndexModel}
import com.mongodb.reactivestreams.client.{ListSearchIndexesPublisher, MongoCollection => JMongoCollection}
import mongo4cats.AsScala
import mongo4cats.bson.Document
import mongo4cats.models.collection.{SearchIndexModel, SearchIndexType}
import org.bson.{BsonValue, Document => JDocument}
import org.bson.conversions.Bson
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicReference}

object SearchIndexFixture extends AsScala {
  final case class Metadata(name: String, status: String)
  final case class ForwardedIndex(definition: Bson, name: String, indexType: BsonValue)

  val definition: Document       = Document.parse("""{"mappings":{"dynamic":true}}""")
  val vectorDefinition: Document =
    Document.parse("""{"fields":[{"type":"vector","path":"embedding","numDimensions":3,"similarity":"cosine"}]}""")
  val indexes: List[SearchIndexModel] = List(
    SearchIndexModel(definition, Some("search")),
    SearchIndexModel(vectorDefinition, Some("vectors"), SearchIndexType.vectorSearch)
  )
  val names: List[String]       = List("search", "vectors")
  val metadata: List[JDocument] = List(
    JDocument.parse("""{"name":"search","status":"READY","queryable":true,"latestDefinition":{"mappings":{"dynamic":true}}}"""),
    JDocument.parse("""{"name":"vectors","status":"BUILDING","queryable":false}""")
  )
  val typedMetadata: List[Metadata] = List(Metadata("search", "READY"), Metadata("vectors", "BUILDING"))

  def forwardedIndexes(models: Seq[SearchIndexModel]): List[ForwardedIndex] =
    models.toList.map(model => ForwardedIndex(model.definition, model.name.orNull, model.indexType.toBsonValue))

  def normalizeArguments(args: Array[AnyRef]): List[AnyRef] =
    args.toList.map {
      case models: java.util.List[_] =>
        asScala(models).map {
          case model: JSearchIndexModel => ForwardedIndex(model.getDefinition, model.getName, model.getType.toBsonValue)
          case other                    => throw new IllegalArgumentException(s"Unexpected search index model: $other")
        }.toList
      case other => other
    }

  def collection(onCall: (String, Array[AnyRef]) => AnyRef): JMongoCollection[Document] = {
    val handler = new InvocationHandler {
      override def invoke(instance: Any, method: Method, args: Array[AnyRef]): AnyRef =
        onCall(method.getName, Option(args).getOrElse(Array.empty[AnyRef]))
    }
    Proxy
      .newProxyInstance(classOf[JMongoCollection[_]].getClassLoader, Array[Class[_]](classOf[JMongoCollection[_]]), handler)
      .asInstanceOf[JMongoCollection[Document]]
  }

  def listing[A](source: Publisher[A], onName: String => Unit): ListSearchIndexesPublisher[A] = {
    val handler = new InvocationHandler {
      override def invoke(instance: Any, method: Method, args: Array[AnyRef]): AnyRef =
        method.getName match {
          case "name" =>
            onName(args(0).asInstanceOf[String])
            instance.asInstanceOf[AnyRef]
          case "subscribe" =>
            source.subscribe(args(0).asInstanceOf[Subscriber[_ >: A]])
            null
          case other => throw new UnsupportedOperationException(s"Unexpected list search indexes call: $other")
        }
    }
    Proxy
      .newProxyInstance(
        classOf[ListSearchIndexesPublisher[_]].getClassLoader,
        Array[Class[_]](classOf[ListSearchIndexesPublisher[_]]),
        handler
      )
      .asInstanceOf[ListSearchIndexesPublisher[A]]
  }

  def publisher[A](values: List[A], error: Option[Throwable] = None): Publisher[A] =
    new Publisher[A] {
      override def subscribe(subscriber: Subscriber[_ >: A]): Unit =
        subscriber.onSubscribe(new Subscription {
          private var remaining = values
          private var stopped   = false

          override def request(n: Long): Unit = {
            var outstanding = n
            while (outstanding > 0L && remaining.nonEmpty && !stopped) {
              val value = remaining.head
              remaining = remaining.tail
              outstanding -= 1L
              subscriber.onNext(value)
            }
            if (remaining.isEmpty && !stopped) {
              stopped = true
              error match {
                case Some(cause) => subscriber.onError(cause)
                case None        => subscriber.onComplete()
              }
            }
          }

          override def cancel(): Unit = stopped = true
        })
    }

  final class ControlledPublisher extends Publisher[AnyRef] {
    val requested   = new CompletableFuture[Unit]()
    val cancelCalls = new AtomicInteger()

    private val subscriber = new AtomicReference[Subscriber[_ >: AnyRef]]()
    private val completed  = new AtomicBoolean(false)

    override def subscribe(value: Subscriber[_ >: AnyRef]): Unit = {
      subscriber.set(value)
      value.onSubscribe(new Subscription {
        override def request(n: Long): Unit = {
          requested.complete(())
          ()
        }

        override def cancel(): Unit = {
          cancelCalls.incrementAndGet()
          completed.set(true)
        }
      })
    }

    def finish(): Unit =
      if (subscriber.get() != null && completed.compareAndSet(false, true)) subscriber.get().onComplete()
  }
}
