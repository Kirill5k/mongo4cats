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

package mongo4cats.queries

import com.mongodb.reactivestreams.client.{AggregatePublisher, ChangeStreamPublisher, DistinctPublisher, FindPublisher}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/** Models the driver's mutable fluent publishers without requiring a MongoDB server. */
final class MutableQueryPublisher {
  import MutableQueryPublisher.Snapshot

  val created    = new AtomicInteger(0)
  val configured = new AtomicInteger(0)

  private val observations = new ConcurrentLinkedQueue[Snapshot]()

  def snapshots: List[Snapshot] = {
    val result   = List.newBuilder[Snapshot]
    val iterator = observations.iterator()
    while (iterator.hasNext) result += iterator.next()
    result.result()
  }

  def find(): FindPublisher[String]           = publisher(classOf[FindPublisher[String]])
  def distinct(): DistinctPublisher[String]   = publisher(classOf[DistinctPublisher[String]])
  def aggregate(): AggregatePublisher[String] = publisher(classOf[AggregatePublisher[String]])
  def watch(): ChangeStreamPublisher[String]  = publisher(classOf[ChangeStreamPublisher[String]], empty = true)

  private def publisher[P](interface: Class[P], empty: Boolean = false): P = {
    val id      = created.incrementAndGet()
    val handler = new InvocationHandler {
      private var options = Map.empty[String, Any]

      private def result(terminal: String): Publisher[AnyRef] = new Publisher[AnyRef] {
        override def subscribe(subscriber: Subscriber[_ >: AnyRef]): Unit = {
          val snapshot = handlerSnapshot(terminal)
          observations.add(snapshot)
          val all                  = List("one", "two", "three").take(snapshot.options.get("limit").fold(3)(_.asInstanceOf[Int]))
          val values: List[AnyRef] = terminal match {
            case "explain"      => List(new org.bson.Document("ok", java.lang.Boolean.TRUE))
            case "toCollection" => Nil
            case _ if empty     => Nil
            case "first"        => all.take(1)
            case _              => all
          }
          subscriber.onSubscribe(new Subscription {
            private var remaining = values
            private var demand    = 0L
            private var draining  = false
            private var stopped   = false

            override def request(n: Long): Unit = synchronized {
              if (!stopped && n > 0L) {
                demand = if (Long.MaxValue - demand < n) Long.MaxValue else demand + n
                if (!draining) {
                  draining = true
                  try {
                    while (!stopped && remaining.nonEmpty && demand > 0L) {
                      val next = remaining.head
                      remaining = remaining.tail
                      demand -= 1L
                      subscriber.onNext(next)
                    }
                    if (!stopped && remaining.isEmpty) {
                      stopped = true
                      subscriber.onComplete()
                    }
                  } finally draining = false
                }
              }
            }

            override def cancel(): Unit = synchronized { stopped = true }
          })
        }
      }

      private def handlerSnapshot(terminal: String): Snapshot = synchronized {
        Snapshot(id, options, terminal)
      }

      override def invoke(proxy: Any, method: Method, args: Array[AnyRef]): AnyRef = synchronized {
        method.getName match {
          case "subscribe" =>
            result("all").subscribe(args(0).asInstanceOf[Subscriber[AnyRef]])
            null
          case "first" | "explain" | "toCollection" => result(method.getName)
          case "toString"                           => s"MutableQueryPublisher($id)"
          case "hashCode"                           => Int.box(id)
          case "equals"                             => Boolean.box(proxy.asInstanceOf[AnyRef] eq args(0))
          case name                                 =>
            configured.incrementAndGet()
            options = options.updated(name, args(0))
            proxy.asInstanceOf[AnyRef]
        }
      }
    }
    interface.cast(Proxy.newProxyInstance(interface.getClassLoader, Array[Class[_]](interface), handler))
  }
}

object MutableQueryPublisher {
  final case class Snapshot(id: Int, options: Map[String, Any], terminal: String)
}
