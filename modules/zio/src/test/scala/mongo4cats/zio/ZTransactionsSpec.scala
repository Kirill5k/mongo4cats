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

package mongo4cats.zio

import com.mongodb.{MongoException, ReadConcern}
import mongo4cats.client.TransactionFixture
import mongo4cats.models.client.{
  ClientBulkWriteOptions,
  ClientSessionOptions,
  ClientWriteCommand,
  TransactionOptions,
  TransactionRetryPolicy
}
import zio.{Cause, Promise, RIO, Scope, Task, ZIO, ZLayer}
import zio.test._

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._

object ZTransactionsSpec extends ZIOSpecDefault {
  private val transientLabel = MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL
  private val unknownLabel   = MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL
  private val noRetries      = TransactionRetryPolicy.none

  final case class Value(value: Int)

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Managed ZIO transactions")(
    test("defer the callback and driver and repeat a saved effect without closing an existing session") {
      val fixture = new TransactionFixture
      val session = new ZClientSessionLive(fixture.session)
      val calls   = new AtomicInteger()
      val effect  = session.withTransaction {
        calls.incrementAndGet()
        fixture.body()
        ZIO.succeed(42)
      }
      val before          = fixture.events
      val callbacksBefore = calls.get()
      for {
        first  <- effect
        second <- effect
      } yield assertTrue(
        before.isEmpty,
        callbacksBefore == 0,
        first == 42,
        second == 42,
        calls.get() == 2,
        fixture.events == List("start", "body", "commit", "start", "body", "commit")
      )
    },
    test("transact forwards options, creates a session per execution, and retains callback environments") {
      val fixture                 = new TransactionFixture
      val client: ZMongoClient    = new ZMongoClientLive(fixture.client)
      val options                 = TransactionOptions.builder.readConcern(ReadConcern.SNAPSHOT).build()
      val sessionOptions          = ClientSessionOptions(causallyConsistent = false)
      val effect: RIO[Value, Int] = client.transactR(options, noRetries, sessionOptions) { session =>
        ZIO.attempt {
          require(session.underlying eq fixture.session)
          fixture.body()
        } *> ZIO.serviceWith[Value](_.value)
      }
      val before = fixture.events
      (for {
        first  <- effect
        second <- effect
      } yield assertTrue(
        before.isEmpty,
        first == 42,
        second == 42,
        fixture.events == List("startSession", "start", "body", "commit", "close", "startSession", "start", "body", "commit", "close"),
        fixture.startOptions == List(options, options),
        fixture.sessionOptions == List(sessionOptions, sessionOptions)
      )).provide(ZLayer.succeed(Value(42)))
    },
    test("retain the environment on an existing session") {
      val fixture                 = new TransactionFixture
      val session: ZClientSession = new ZClientSessionLive(fixture.session)
      (for {
        first  <- session.withTransactionR(ZIO.serviceWith[Value](_.value))
        second <- session.withTransactionR(retryPolicy = noRetries)(ZIO.serviceWith[Value](_.value))
      } yield (first, second))
        .provide(ZLayer.succeed(Value(17)))
        .map(value => assertTrue(value == ((17, 17)), fixture.events == List("start", "commit", "start", "commit")))
    },
    test("retain callback environments with the default client adapter") {
      val fixture              = new TransactionFixture
      val client: ZMongoClient = new ZMongoClientLive(fixture.client)
      client
        .transactR(_ => ZIO.serviceWith[Value](_.value))
        .provide(ZLayer.succeed(Value(42)))
        .map(value => assertTrue(value == 42, fixture.events == List("startSession", "start", "commit", "close")))
    },
    test("defer the client environment adapter and delegate to the native implementation") {
      val fixture                = new TransactionFixture
      val live                   = new ZMongoClientLive(fixture.client)
      val session                = new ZClientSessionLive(fixture.session)
      val calls                  = new AtomicInteger()
      val expectedOptions        = TransactionOptions()
      val expectedSessionOptions = ClientSessionOptions(causallyConsistent = false)
      val client: ZMongoClient   = new ZMongoClient {
        val underlying                                                                    = fixture.client
        def getDatabase(name: String)                                                     = live.getDatabase(name)
        def listDatabaseNames                                                             = live.listDatabaseNames
        def listDatabaseNames(session: ZClientSession)                                    = live.listDatabaseNames(session)
        def listDatabases                                                                 = live.listDatabases
        def listDatabases(session: ZClientSession)                                        = live.listDatabases(session)
        def bulkWrite(commands: Seq[ClientWriteCommand], options: ClientBulkWriteOptions) = live.bulkWrite(commands, options)
        def bulkWrite(session: ZClientSession, commands: Seq[ClientWriteCommand], options: ClientBulkWriteOptions) =
          live.bulkWrite(session, commands, options)
        def startSession(options: ClientSessionOptions) = live.startSession(options)
        def transact[A](
            options: TransactionOptions,
            retryPolicy: TransactionRetryPolicy,
            sessionOptions: ClientSessionOptions
        )(body: ZClientSession => Task[A]): Task[A] =
          ZIO.attempt {
            require(options eq expectedOptions)
            require(retryPolicy == noRetries)
            require(sessionOptions eq expectedSessionOptions)
            calls.incrementAndGet()
          } *> body(session)
      }
      val effect = client.transactR(expectedOptions, noRetries, expectedSessionOptions)(_ => ZIO.serviceWith[Value](_.value))
      val before = calls.get()
      effect
        .provide(ZLayer.succeed(Value(42)))
        .map(value =>
          assertTrue(
            before == 0,
            value == 42,
            calls.get() == 1,
            fixture.events.isEmpty
          )
        )
    },
    test("defer the environment adapter and delegate to a custom session's native method") {
      val fixture                 = new TransactionFixture
      val live                    = new ZClientSessionLive(fixture.session)
      val calls                   = new AtomicInteger()
      val expectedOptions         = TransactionOptions()
      val session: ZClientSession = new mongo4cats.client.ClientSession[Task] {
        val underlying                                                = fixture.session
        def startTransaction(options: TransactionOptions): Task[Unit] = live.startTransaction(options)
        def abortTransaction: Task[Unit]                              = live.abortTransaction
        def commitTransaction: Task[Unit]                             = live.commitTransaction
        def withTransaction[A](receivedOptions: TransactionOptions, retryPolicy: TransactionRetryPolicy)(body: => Task[A]): Task[A] =
          ZIO.attempt {
            require(receivedOptions eq expectedOptions)
            require(retryPolicy == noRetries)
            calls.incrementAndGet()
          } *> body
      }
      val effect = session.withTransactionR(expectedOptions, noRetries)(ZIO.serviceWith[Value](_.value))
      val before = calls.get()
      effect
        .provide(ZLayer.succeed(Value(42)))
        .map(value =>
          assertTrue(
            before == 0,
            value == 42,
            calls.get() == 1,
            fixture.events.isEmpty
          )
        )
    },
    test("capture synchronous callback exceptions, abort, and retain the original error") {
      val fixture                            = new TransactionFixture
      val error                              = new IllegalStateException("callback construction")
      val body: ZClientSession => Task[Unit] = _ => {
        fixture.body()
        throw error
      }
      val effect = new ZMongoClientLive(fixture.client).transact(body)
      val before = fixture.events
      effect.either.map(result =>
        assertTrue(
          before.isEmpty,
          result.left.exists(_ eq error),
          fixture.events == List("startSession", "start", "body", "abort", "close")
        )
      )
    },
    test("preserve body errors and retain a rollback error as suppressed without retrying") {
      val fixture = new TransactionFixture
      val error   = labeled(transientLabel)
      val cleanup = new IllegalStateException("rollback failed")
      fixture.abortOutcomes = List(Left(cleanup))
      val body: ZClientSession => Task[Unit] = _ => ZIO.attempt(fixture.body()) *> ZIO.fail(error)
      new ZMongoClientLive(fixture.client)
        .transact(body)
        .exit
        .map(exit =>
          assertTrue(
            exit.causeOption.exists(_.failureOption.exists(_ eq error)),
            exit.causeOption.exists(_.defects.isEmpty),
            error.getSuppressed.toList == List(cleanup),
            fixture.events == List("startSession", "start", "body", "abort", "close")
          )
        )
    },
    test("preserve defects and composite causes while aborting") {
      val fixture = new TransactionFixture
      val first   = new IllegalStateException("failure")
      val defect  = new IllegalStateException("defect")
      val cause   = Cause.fail(first) ++ Cause.die(defect)
      new ZClientSessionLive(fixture.session)
        .withTransaction[Unit](ZIO.failCause(cause))
        .exit
        .map(exit =>
          assertTrue(
            exit.causeOption.exists(_.failures == List(first)),
            exit.causeOption.exists(_.defects == List(defect)),
            fixture.events == List("start", "abort")
          )
        )
    },
    test("preserve a body failure when session closing also fails") {
      val fixture = new TransactionFixture
      val error   = new IllegalStateException("body failed")
      val cleanup = new IllegalStateException("session close failed")
      fixture.closeOutcomes = List(Left(cleanup))
      new ZMongoClientLive(fixture.client)
        .transact(_ => ZIO.fail(error))
        .either
        .map(result =>
          assertTrue(
            result.left.exists(_ eq error),
            error.getSuppressed.toList == List(cleanup),
            fixture.events == List("startSession", "start", "abort", "close")
          )
        )
    },
    test("surface session close failures after an otherwise successful transaction") {
      val fixture = new TransactionFixture
      val cleanup = new IllegalStateException("session close failed")
      fixture.closeOutcomes = List(Left(cleanup))
      new ZMongoClientLive(fixture.client)
        .transact(_ => ZIO.succeed(42))
        .exit
        .map(exit =>
          assertTrue(
            exit.causeOption.exists(_.defects.exists(_ eq cleanup)),
            fixture.events == List("startSession", "start", "commit", "close")
          )
        )
    },
    test("abort and close on interruption and preserve interruption when abort fails") {
      val fixture = new TransactionFixture
      val cleanup = new IllegalStateException("rollback after cancellation")
      fixture.abortOutcomes = List(Left(cleanup))
      for {
        entered <- Promise.make[Nothing, Unit]
        fiber   <- new ZMongoClientLive(fixture.client).transact { _ =>
          ZIO.attempt(fixture.body()) *> entered.succeed(()) *> ZIO.never
        }.fork
        _    <- entered.await
        exit <- fiber.interrupt
      } yield assertTrue(
        exit.isInterrupted,
        exit.causeOption.exists(_.failures.isEmpty),
        exit.causeOption.exists(_.defects.isEmpty),
        fixture.events == List("startSession", "start", "body", "abort", "close")
      )
    },
    test("leave an existing active transaction untouched") {
      val fixture = new TransactionFixture
      fixture.active = true
      new ZClientSessionLive(fixture.session)
        .withTransaction(ZIO.attempt(fixture.body()))
        .either
        .map(result =>
          assertTrue(
            result.left.exists(_.isInstanceOf[IllegalStateException]),
            fixture.events.isEmpty,
            fixture.active
          )
        )
    },
    test("do not abort when startup fails and still close an owned session") {
      val fixture = new TransactionFixture
      val error   = labeled(transientLabel)
      fixture.startOutcomes = List(Left(error))
      new ZMongoClientLive(fixture.client)
        .transact(_ => ZIO.attempt(fixture.body()))
        .either
        .map(result =>
          assertTrue(
            result.left.exists(_ eq error),
            fixture.events == List("startSession", "start", "close")
          )
        )
    },
    test("capture synchronous and asynchronous session acquisition errors without invoking the callback") {
      ZIO
        .foreach(List(true, false)) { synchronous =>
          val fixture = new TransactionFixture
          val error   = new IllegalStateException("session acquisition")
          if (synchronous) fixture.startSessionThrows = Some(error)
          else fixture.startSessionOutcomes = List(Left(error))
          val effect = new ZMongoClientLive(fixture.client).transact(_ => ZIO.attempt(fixture.body()))
          val before = fixture.events
          effect.either.map(result => before.isEmpty && result.left.exists(_ eq error) && fixture.events == List("startSession"))
        }
        .map(results => assertTrue(!results.contains(false)))
    },
    test("defer manual commit and abort driver calls and capture synchronous errors on every execution") {
      ZIO
        .foreach(List(true, false)) { commit =>
          val fixture = new TransactionFixture
          val error   = new IllegalStateException("driver invocation")
          if (commit) fixture.commitThrows = Some(error) else fixture.abortThrows = Some(error)
          val session = new ZClientSessionLive(fixture.session)
          val effect  = if (commit) session.commitTransaction else session.abortTransaction
          val before  = fixture.events
          for {
            first  <- effect.either
            second <- effect.either
          } yield before.isEmpty && first.left.exists(_ eq error) && second.left.exists(_ eq error) &&
            fixture.events == List.fill(2)(if (commit) "commit" else "abort")
        }
        .map(results => assertTrue(!results.contains(false)))
    },
    test("do not commit after the callback has ended the transaction") {
      val fixture = new TransactionFixture
      val session = new ZClientSessionLive(fixture.session)
      session
        .withTransaction(session.abortTransaction.as(42))
        .map(value =>
          assertTrue(
            value == 42,
            fixture.events == List("start", "abort")
          )
        )
    },
    test("retry a transient body failure from the start using the same owned session") {
      val fixture = new TransactionFixture
      val calls   = new AtomicInteger()
      val error   = labeled(transientLabel)
      for {
        entered <- Promise.make[Nothing, Unit]
        fiber   <- new ZMongoClientLive(fixture.client).transact { _ =>
          ZIO.attempt(fixture.body()) *> ZIO.suspendSucceed {
            if (calls.incrementAndGet() == 1) entered.succeed(()) *> ZIO.fail(error) else ZIO.succeed(42)
          }
        }.fork
        _      <- entered.await
        _      <- TestClock.adjust(zio.Duration.fromScala(10.millis))
        result <- fiber.join
      } yield assertTrue(
        result == 42,
        calls.get() == 2,
        fixture.events == List("startSession", "start", "body", "abort", "start", "body", "commit", "close")
      )
    },
    test("retry uncertain commits without repeating the callback, including errors bearing both labels") {
      val fixture = new TransactionFixture
      fixture.commitOutcomes = List(Left(labeled(unknownLabel, transientLabel)), Right(()))
      for {
        fiber <- new ZClientSessionLive(fixture.session).withTransaction(ZIO.attempt { fixture.body(); 42 }).fork
        _     <- TestClock.adjust(zio.Duration.fromScala(10.millis))
        value <- fiber.join
      } yield assertTrue(value == 42, fixture.events == List("start", "body", "commit", "commit"))
    },
    test("retry a transient commit failure by restarting the transaction without aborting") {
      val fixture = new TransactionFixture
      fixture.commitOutcomes = List(Left(labeled(transientLabel)), Right(()))
      for {
        fiber <- new ZClientSessionLive(fixture.session).withTransaction(ZIO.attempt { fixture.body(); 42 }).fork
        _     <- TestClock.adjust(zio.Duration.fromScala(10.millis))
        value <- fiber.join
      } yield assertTrue(value == 42, fixture.events == List("start", "body", "commit", "start", "body", "commit"))
    },
    test("do not retry ordinary errors or uncertain-commit labels from a body") {
      ZIO
        .foreach(List(new IllegalStateException("ordinary"), labeled(unknownLabel))) { error =>
          val fixture = new TransactionFixture
          new ZClientSessionLive(fixture.session)
            .withTransaction(ZIO.fail(error))
            .either
            .map(result => result.left.exists(_ eq error) && fixture.events == List("start", "abort"))
        }
        .map(results => assertTrue(!results.contains(false)))
    },
    test("do not abort terminal commit failures and preserve their identity") {
      val fixture = new TransactionFixture
      val error   = new IllegalStateException("commit failed")
      fixture.commitOutcomes = List(Left(error))
      new ZMongoClientLive(fixture.client)
        .transact(_ => ZIO.succeed(42))
        .either
        .map(result =>
          assertTrue(
            result.left.exists(_ eq error),
            fixture.events == List("startSession", "start", "commit", "close")
          )
        )
    },
    test("preserve a synchronous transient commit error if the driver still has an active transaction") {
      val fixture = new TransactionFixture
      val error   = labeled(transientLabel)
      fixture.commitThrows = Some(error)
      new ZMongoClientLive(fixture.client)
        .transact(_ => ZIO.succeed(42))
        .either
        .map(result =>
          assertTrue(
            result.left.exists(_ eq error),
            fixture.events == List("startSession", "start", "commit", "close")
          )
        )
    },
    test("disable both transaction and commit retries") {
      ZIO
        .foreach(List(false, true)) { duringCommit =>
          val fixture = new TransactionFixture
          val error   = labeled(if (duringCommit) unknownLabel else transientLabel)
          if (duringCommit) fixture.commitOutcomes = List(Left(error))
          new ZClientSessionLive(fixture.session)
            .withTransaction(retryPolicy = noRetries) {
              if (duringCommit) ZIO.unit else ZIO.fail(error)
            }
            .either
            .map(result => result.left.exists(_ eq error) && fixture.events == List("start", if (duringCommit) "commit" else "abort"))
        }
        .map(results => assertTrue(!results.contains(false)))
    },
    test("bound retries by elapsed time, preserving the final error and resetting budgets on reexecution") {
      val fixture = new TransactionFixture
      val calls   = new AtomicInteger()
      val error   = labeled(transientLabel)
      val policy  = TransactionRetryPolicy(maxDuration = 25.millis, initialDelay = 10.millis, maxDelay = 10.millis)
      val effect  = new ZClientSessionLive(fixture.session).withTransaction(retryPolicy = policy) {
        ZIO.attempt(calls.incrementAndGet()) *> ZIO.fail(error)
      }
      for {
        firstFiber  <- effect.either.fork
        _           <- TestClock.adjust(zio.Duration.fromScala(25.millis))
        first       <- firstFiber.join
        firstCalls  <- ZIO.succeed(calls.get())
        secondFiber <- effect.either.fork
        _           <- TestClock.adjust(zio.Duration.fromScala(25.millis))
        second      <- secondFiber.join
      } yield assertTrue(
        first.left.exists(_ eq error),
        second.left.exists(_ eq error),
        firstCalls == 3,
        calls.get() == 6,
        fixture.events.count(_ == "abort") == 6
      )
    },
    test("allow the first successful body to exceed the retry budget") {
      val fixture = new TransactionFixture
      val policy  = TransactionRetryPolicy(maxDuration = 1.millis)
      for {
        fiber <- new ZClientSessionLive(fixture.session)
          .withTransaction(retryPolicy = policy) {
            ZIO.sleep(zio.Duration.fromScala(1.second)).as(42)
          }
          .fork
        _     <- TestClock.adjust(zio.Duration.fromScala(1.second))
        value <- fiber.join
      } yield assertTrue(value == 42, fixture.events == List("start", "commit"))
    },
    test("share one retry budget across repeated bodies and uncertain commits") {
      val fixture = new TransactionFixture
      val calls   = new AtomicInteger()
      val error   = labeled(unknownLabel)
      val policy  = TransactionRetryPolicy(maxDuration = 15.millis, initialDelay = 10.millis, maxDelay = 20.millis)
      fixture.commitOutcomes = List(Left(error))
      for {
        fiber <- new ZClientSessionLive(fixture.session)
          .withTransaction(retryPolicy = policy) {
            ZIO.attempt(fixture.body()) *> ZIO.suspendSucceed {
              if (calls.incrementAndGet() == 1) ZIO.fail(labeled(transientLabel)) else ZIO.succeed(42)
            }
          }
          .either
          .fork
        _      <- TestClock.adjust(zio.Duration.fromScala(15.millis))
        result <- fiber.join
      } yield assertTrue(
        result.left.exists(_ eq error),
        calls.get() == 2,
        fixture.events == List("start", "body", "abort", "start", "body", "commit")
      )
    },
    test("stop uncertain-commit retries at the budget without aborting or repeating the body") {
      val fixture = new TransactionFixture
      val error   = labeled(unknownLabel)
      val policy  = TransactionRetryPolicy(maxDuration = 25.millis, initialDelay = 10.millis, maxDelay = 10.millis)
      fixture.commitOutcomes = List.fill(4)(Left(error))
      for {
        fiber  <- new ZClientSessionLive(fixture.session).withTransaction(retryPolicy = policy)(ZIO.attempt(fixture.body())).either.fork
        _      <- TestClock.adjust(zio.Duration.fromScala(25.millis))
        result <- fiber.join
      } yield assertTrue(result.left.exists(_ eq error), fixture.events == List("start", "body", "commit", "commit", "commit"))
    },
    test("stop retries when interrupted during backoff") {
      val fixture = new TransactionFixture
      val policy  = TransactionRetryPolicy(initialDelay = 1.second, maxDelay = 1.second)
      for {
        failed <- Promise.make[Nothing, Unit]
        fiber  <- new ZMongoClientLive(fixture.client)
          .transact(retryPolicy = policy) { _ =>
            failed.succeed(()) *> ZIO.fail(labeled(transientLabel))
          }
          .fork
        _    <- failed.await
        _    <- TestClock.adjust(zio.Duration.fromScala(100.millis))
        exit <- fiber.interrupt
      } yield assertTrue(exit.isInterrupted, fixture.events == List("startSession", "start", "abort", "close"))
    },
    test("mask an in-flight commit, then deliver interruption without aborting") {
      val fixture = new TransactionFixture
      val commit  = new TransactionFixture.ControlledPublisher[Void]
      fixture.commitPublisher = Some(commit)
      for {
        fiber  <- new ZMongoClientLive(fixture.client).transact(_ => ZIO.succeed(42)).fork
        _      <- await(commit.requested)
        _      <- fiber.interruptFork
        before <- fiber.poll
        _      <- ZIO.succeed(commit.complete())
        exit   <- fiber.await
      } yield assertTrue(
        before.isEmpty,
        exit.isInterrupted,
        !commit.cancelled,
        fixture.events == List("startSession", "start", "commit", "close")
      )
    },
    test("wait for rollback before closing an interrupted transaction's session") {
      val fixture = new TransactionFixture
      val abort   = new TransactionFixture.ControlledPublisher[Void]
      fixture.abortPublisher = Some(abort)
      for {
        entered <- Promise.make[Nothing, Unit]
        fiber   <- new ZMongoClientLive(fixture.client).transact { _ =>
          entered.succeed(()) *> ZIO.never
        }.fork
        _            <- entered.await
        interrupter  <- fiber.interrupt.fork
        _            <- await(abort.requested)
        before       <- interrupter.poll
        beforeEvents <- ZIO.succeed(fixture.events)
        _            <- ZIO.succeed(abort.complete())
        exit         <- interrupter.join
      } yield assertTrue(
        before.isEmpty,
        beforeEvents == List("startSession", "start", "abort"),
        exit.isInterrupted,
        !abort.cancelled,
        fixture.events == List("startSession", "start", "abort", "close")
      )
    }
  ) @@ TestAspect.timeout(zio.Duration.fromScala(10.seconds))

  private def labeled(labels: String*): MongoException = {
    val error = new MongoException("scripted transaction failure")
    labels.foreach(error.addLabel)
    error
  }

  private def await(condition: => Boolean): Task[Unit] =
    ZIO.suspendSucceed(if (condition) ZIO.unit else ZIO.yieldNow *> await(condition))
}
