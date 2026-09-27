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

package mongo4cats.client

import com.mongodb.reactivestreams.client.{ClientSession => JClientSession, MongoClient => JMongoClient}
import mongo4cats.models.client.{ClientSessionOptions, TransactionOptions}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import java.lang.reflect.{InvocationHandler, Method, Proxy}

/** Scripted driver shared by the Cats Effect and ZIO managed transaction tests. */
final class TransactionFixture {
  @volatile var active: Boolean                                     = false
  @volatile var startOutcomes: List[Either[Throwable, Unit]]        = Nil
  @volatile var startSessionOutcomes: List[Either[Throwable, Unit]] = Nil
  @volatile var commitOutcomes: List[Either[Throwable, Unit]]       = Nil
  @volatile var abortOutcomes: List[Either[Throwable, Unit]]        = Nil
  @volatile var closeOutcomes: List[Either[Throwable, Unit]]        = Nil
  @volatile var commitPublisher: Option[Publisher[Void]]            = None
  @volatile var abortPublisher: Option[Publisher[Void]]             = None
  @volatile var commitThrows: Option[Throwable]                     = None
  @volatile var abortThrows: Option[Throwable]                      = None
  @volatile var startSessionThrows: Option[Throwable]               = None

  private var recordedEvents: List[String]                       = Nil
  private var recordedStartOptions: List[TransactionOptions]     = Nil
  private var recordedSessionOptions: List[ClientSessionOptions] = Nil

  def events: List[String]                         = synchronized(recordedEvents.reverse)
  def startOptions: List[TransactionOptions]       = synchronized(recordedStartOptions.reverse)
  def transactionOptions: List[TransactionOptions] = startOptions
  def sessionOptions: List[ClientSessionOptions]   = synchronized(recordedSessionOptions.reverse)
  def body(): Unit                                 = record("body")

  val session: JClientSession = proxy(classOf[JClientSession]) { (name, arguments) =>
    name match {
      case "startTransaction" =>
        record("start")
        if (active) throw new IllegalStateException("Transaction already in progress")
        synchronized {
          recordedStartOptions = arguments.headOption.map(_.asInstanceOf[TransactionOptions]).getOrElse(TransactionOptions()) ::
            recordedStartOptions
        }
        consumeStart().fold(throw _, _ => active = true)
        null
      case "hasActiveTransaction"  => Boolean.box(active)
      case "getTransactionOptions" => startOptions.lastOption.getOrElse(TransactionOptions())
      case "getOptions"            => ClientSessionOptions.builder.build()
      case "isCausallyConsistent"  => Boolean.box(true)
      case "commitTransaction"     =>
        record("commit")
        commitThrows.foreach(throw _)
        val source = commitPublisher.getOrElse(TransactionFixture.result(consumeCommit()))
        terminal(source)(_ => active = false)
      case "abortTransaction" =>
        record("abort")
        abortThrows.foreach(throw _)
        val source = abortPublisher.getOrElse(TransactionFixture.result(consumeAbort()))
        terminal(source) {
          case Right(_) => active = false
          case Left(_)  => ()
        }
      case "close" =>
        record("close")
        consumeClose().fold(throw _, identity)
        null
      case other => throw new UnsupportedOperationException(s"Unexpected session call: $other")
    }
  }

  val client: JMongoClient = proxy(classOf[JMongoClient]) { (name, arguments) =>
    name match {
      case "startSession" =>
        record("startSession")
        startSessionThrows.foreach(throw _)
        synchronized {
          recordedSessionOptions = arguments.headOption
            .map(_.asInstanceOf[ClientSessionOptions])
            .getOrElse(ClientSessionOptions.builder.build()) :: recordedSessionOptions
        }
        consumeStartSession() match {
          case Right(_)    => TransactionFixture.single(session)
          case Left(error) => TransactionFixture.failure[JClientSession](error)
        }
      case "close" =>
        record("clientClose")
        null
      case other => throw new UnsupportedOperationException(s"Unexpected client call: $other")
    }
  }

  private def record(event: String): Unit = synchronized {
    recordedEvents = event :: recordedEvents
  }

  private def consumeStart(): Either[Throwable, Unit] = synchronized {
    val result = startOutcomes.headOption.getOrElse(Right(()))
    startOutcomes = startOutcomes.drop(1)
    result
  }

  private def consumeStartSession(): Either[Throwable, Unit] = synchronized {
    val result = startSessionOutcomes.headOption.getOrElse(Right(()))
    startSessionOutcomes = startSessionOutcomes.drop(1)
    result
  }

  private def consumeCommit(): Either[Throwable, Unit] = synchronized {
    val result = commitOutcomes.headOption.getOrElse(Right(()))
    commitOutcomes = commitOutcomes.drop(1)
    result
  }

  private def consumeAbort(): Either[Throwable, Unit] = synchronized {
    val result = abortOutcomes.headOption.getOrElse(Right(()))
    abortOutcomes = abortOutcomes.drop(1)
    result
  }

  private def consumeClose(): Either[Throwable, Unit] = synchronized {
    val result = closeOutcomes.headOption.getOrElse(Right(()))
    closeOutcomes = closeOutcomes.drop(1)
    result
  }

  private def terminal(source: Publisher[Void])(onResult: Either[Throwable, Unit] => Unit): Publisher[Void] =
    new Publisher[Void] {
      override def subscribe(subscriber: Subscriber[_ >: Void]): Unit = source.subscribe(new Subscriber[Void] {
        override def onSubscribe(subscription: Subscription): Unit = subscriber.onSubscribe(subscription)
        override def onNext(value: Void): Unit                     = subscriber.onNext(value)
        override def onError(error: Throwable): Unit               = {
          onResult(Left(error))
          subscriber.onError(error)
        }
        override def onComplete(): Unit = {
          onResult(Right(()))
          subscriber.onComplete()
        }
      })
    }

  private def proxy[A](interface: Class[A])(onCall: (String, Array[AnyRef]) => AnyRef): A = {
    val handler = new InvocationHandler {
      override def invoke(instance: Any, method: Method, arguments: Array[AnyRef]): AnyRef =
        onCall(method.getName, Option(arguments).getOrElse(Array.empty[AnyRef]))
    }
    interface.cast(Proxy.newProxyInstance(interface.getClassLoader, Array[Class[_]](interface), handler))
  }
}

object TransactionFixture {
  def result(outcome: Either[Throwable, Unit]): Publisher[Void] = publisher[Void](outcome.map(_ => None))
  def single[A](value: A): Publisher[A]                         = publisher(Right(Some(value)))
  def failure[A](error: Throwable): Publisher[A]                = publisher(Left(error))

  private def publisher[A](outcome: Either[Throwable, Option[A]]): Publisher[A] = new Publisher[A] {
    override def subscribe(subscriber: Subscriber[_ >: A]): Unit = subscriber.onSubscribe(new Subscription {
      private var completed               = false
      override def request(n: Long): Unit = synchronized {
        if (!completed) {
          completed = true
          outcome match {
            case Left(error)  => subscriber.onError(error)
            case Right(value) =>
              value.foreach(subscriber.onNext)
              subscriber.onComplete()
          }
        }
      }
      override def cancel(): Unit = synchronized { completed = true }
    })
  }

  /** A publisher whose terminal result can be delivered after an effect test observes subscription demand. */
  final class ControlledPublisher[A] extends Publisher[A] {
    @volatile var requested: Boolean                 = false
    @volatile var cancelled: Boolean                 = false
    @volatile var onRequest: () => Unit              = () => ()
    private var receiver: Option[Subscriber[_ >: A]] = None
    private var completed                            = false

    def publisher: Publisher[A] = this
    def subscribed: Boolean     = synchronized(receiver.isDefined)
    def canceled: Boolean       = cancelled

    override def subscribe(subscriber: Subscriber[_ >: A]): Unit = {
      synchronized {
        require(receiver.isEmpty, "Controlled publisher supports one subscription")
        receiver = Some(subscriber)
      }
      subscriber.onSubscribe(new Subscription {
        override def request(n: Long): Unit = {
          requested = true
          onRequest()
        }
        override def cancel(): Unit = cancelled = true
      })
    }

    def complete(): Unit             = finish(Right(None))
    def succeed(): Unit              = complete()
    def succeed(value: A): Unit      = finish(Right(Some(value)))
    def fail(error: Throwable): Unit = finish(Left(error))

    private def finish(outcome: Either[Throwable, Option[A]]): Unit = {
      val subscriber = synchronized {
        require(requested && !completed, "Controlled publisher must have outstanding demand")
        completed = true
        receiver.get
      }
      if (!cancelled) outcome match {
        case Left(error)  => subscriber.onError(error)
        case Right(value) =>
          value.foreach(subscriber.onNext)
          subscriber.onComplete()
      }
    }
  }
}
