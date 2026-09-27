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

import cats.effect.{Deferred, IO}
import cats.effect.testkit.TestControl
import cats.effect.unsafe.implicits.global
import com.mongodb.{MongoException, MongoOperationTimeoutException, ReadConcern}
import mongo4cats.models.client.{ClientSessionOptions, TransactionOptions, TransactionRetryPolicy}
import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future
import scala.concurrent.duration._

class TransactionsSpec extends AsyncWordSpec with Matchers {
  private def session(fixture: TransactionFixture): ClientSession[IO] = new LiveClientSession[IO](fixture.session)
  private def client(fixture: TransactionFixture): MongoClient[IO]    = new LiveMongoClient[IO](fixture.client)
  private def run(test: IO[Assertion]): Future[Assertion]             = TestControl.executeEmbed(test).unsafeToFuture()
  private def labeled(labels: String*): MongoException                = {
    val error = new MongoException(91, "retryable")
    labels.foreach(error.addLabel)
    error
  }
  private def transient: MongoException          = labeled(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
  private def uncertain: MongoException          = labeled(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)
  private def await(check: => Boolean): IO[Unit] = IO.defer {
    if (check) IO.unit else IO.cede *> await(check)
  }

  "Managed transactions" should {
    "be lazy, return the result, and start fresh on each execution" in {
      val fixture = new TransactionFixture
      val program = session(fixture).withTransaction { fixture.body(); IO.pure(42) }
      fixture.events mustBe empty
      run(for {
        first  <- program
        second <- program
      } yield {
        first mustBe 42
        second mustBe 42
        fixture.events mustBe List("start", "body", "commit", "start", "body", "commit")
      })
    }

    "forward options, reuse one owned session across retries, and close it" in {
      val fixture = new TransactionFixture
      fixture.commitOutcomes = List(Left(transient), Right(()))
      val options        = TransactionOptions.builder.readConcern(ReadConcern.SNAPSHOT).build()
      val sessionOptions = ClientSessionOptions(causallyConsistent = false)
      val program        = client(fixture).transact(options = options, sessionOptions = sessionOptions) { current =>
        IO { fixture.body(); current.underlying must be theSameInstanceAs fixture.session }.as(42)
      }
      fixture.events mustBe empty
      run(program.map { value =>
        value mustBe 42
        fixture.events mustBe List("startSession", "start", "body", "commit", "start", "body", "commit", "close")
        fixture.transactionOptions mustBe List(options, options)
        fixture.sessionOptions mustBe List(sessionOptions)
      })
    }

    "capture callback construction failures and preserve their identity after abort and close" in {
      val fixture                             = new TransactionFixture
      val error                               = new IllegalArgumentException("callback")
      val body: ClientSession[IO] => IO[Unit] = _ => { fixture.body(); throw error }
      val program                             = client(fixture).transact(body)
      fixture.events mustBe empty
      run(program.attempt.map { result =>
        result mustBe Left(error)
        fixture.events mustBe List("startSession", "start", "body", "abort", "close")
      })
    }

    "not abort when startup fails" in {
      val fixture = new TransactionFixture
      val error   = transient
      fixture.startOutcomes = List(Left(error))
      run(client(fixture).transact(_ => IO(fixture.body())).attempt.map { result =>
        result mustBe Left(error)
        fixture.events mustBe List("startSession", "start", "close")
      })
    }

    "reject an existing transaction without changing or aborting it" in {
      val fixture = new TransactionFixture
      fixture.active = true
      run(session(fixture).withTransaction(IO(fixture.body())).attempt.map { result =>
        result.left.toOption.get mustBe a[IllegalStateException]
        fixture.active mustBe true
        fixture.events mustBe empty
      })
    }

    "suppress abort failures on the original error and stop retrying" in {
      val fixture = new TransactionFixture
      val error   = transient
      val cleanup = new IllegalStateException("abort")
      fixture.abortOutcomes = List(Left(cleanup))
      run(session(fixture).withTransaction(IO.raiseError[Unit](error)).attempt.map { result =>
        result mustBe Left(error)
        error.getSuppressed.toList mustBe List(cleanup)
        fixture.events mustBe List("start", "abort")
      })
    }

    "capture synchronous abort failures and avoid self-suppression" in {
      val fixture = new TransactionFixture
      val error   = transient
      fixture.abortThrows = Some(error)
      run(session(fixture).withTransaction(IO.raiseError[Unit](error)).attempt.map { result =>
        result mustBe Left(error)
        error.getSuppressed mustBe empty
        fixture.events mustBe List("start", "abort")
      })
    }

    "rerun a transient body only after rollback" in {
      val fixture = new TransactionFixture
      val program = session(fixture).withTransaction {
        IO(fixture.body()) *> IO.defer {
          if (fixture.events.count(_ == "body") == 1) IO.raiseError[Int](transient) else IO.pure(42)
        }
      }
      run(program.map { value =>
        value mustBe 42
        fixture.events mustBe List("start", "body", "abort", "start", "body", "commit")
      })
    }

    "retry uncertain commits on an inactive session without replaying the callback" in {
      val fixture = new TransactionFixture
      fixture.commitOutcomes = List(Left(uncertain), Left(uncertain), Right(()))
      run(session(fixture).withTransaction(IO(fixture.body()).as(42)).map { value =>
        value mustBe 42
        fixture.events mustBe List("start", "body", "commit", "commit", "commit")
        fixture.active mustBe false
      })
    }

    "prioritize uncertain commit over the transient label" in {
      val fixture = new TransactionFixture
      fixture.commitOutcomes = List(
        Left(labeled(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL, MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)),
        Right(())
      )
      run(session(fixture).withTransaction(IO(fixture.body())).map { _ =>
        fixture.events mustBe List("start", "body", "commit", "commit")
      })
    }

    "preserve ordinary commit errors without attempting an abort" in {
      val fixture = new TransactionFixture
      val error   = new RuntimeException("commit")
      fixture.commitOutcomes = List(Left(error))
      run(session(fixture).withTransaction(IO(fixture.body())).attempt.map { result =>
        result mustBe Left(error)
        fixture.events mustBe List("start", "body", "commit")
      })
    }

    "capture synchronous commit construction failures in the effect" in {
      val fixture = new TransactionFixture
      val error   = new RuntimeException("construct commit")
      fixture.commitThrows = Some(error)
      val program = session(fixture).withTransaction(IO(fixture.body()))
      fixture.events mustBe empty
      run(program.attempt.map { result =>
        result mustBe Left(error)
        fixture.events mustBe List("start", "body", "commit")
      })
    }

    "preserve transient commit construction failures when the session remains active" in {
      val fixture = new TransactionFixture
      val error   = transient
      fixture.commitThrows = Some(error)
      run(session(fixture).withTransaction(IO.unit).attempt.map { result =>
        result mustBe Left(error)
        fixture.events mustBe List("start", "commit")
      })
    }

    "disable both kinds of retry when requested" in {
      val bodyFixture   = new TransactionFixture
      val commitFixture = new TransactionFixture
      val bodyError     = transient
      val commitError   = uncertain
      commitFixture.commitOutcomes = List(Left(commitError))
      run(for {
        body   <- session(bodyFixture).withTransaction(retryPolicy = TransactionRetryPolicy.none)(IO.raiseError[Unit](bodyError)).attempt
        commit <- session(commitFixture).withTransaction(retryPolicy = TransactionRetryPolicy.none)(IO.unit).attempt
      } yield {
        body mustBe Left(bodyError)
        commit mustBe Left(commitError)
        bodyFixture.events mustBe List("start", "abort")
        commitFixture.events mustBe List("start", "commit")
      })
    }

    "share a monotonic deadline between body and commit retries and preserve the last error" in {
      val fixture   = new TransactionFixture
      val lastError = uncertain
      fixture.commitOutcomes = List(Left(lastError))
      val policy = TransactionRetryPolicy(maxDuration = 15.millis)
      run(for {
        before <- IO.monotonic
        result <- session(fixture)
          .withTransaction(retryPolicy = policy) {
            IO(fixture.body()) *> IO.defer {
              if (fixture.events.count(_ == "body") == 1) IO.raiseError[Unit](transient) else IO.unit
            }
          }
          .attempt
        after <- IO.monotonic
      } yield {
        result mustBe Left(lastError)
        after - before mustBe 15.millis
        fixture.events mustBe List("start", "body", "abort", "start", "body", "commit")
      })
    }

    "allocate a new retry budget when a saved effect is rerun" in {
      val fixture = new TransactionFixture
      fixture.commitOutcomes = List(Left(uncertain), Right(()), Left(uncertain), Right(()))
      val program = session(fixture).withTransaction(retryPolicy = TransactionRetryPolicy(maxDuration = 15.millis))(IO(fixture.body()))
      run((program *> IO.sleep(1.hour) *> program).map { _ =>
        fixture.events mustBe List("start", "body", "commit", "commit", "start", "body", "commit", "commit")
      })
    }

    "treat operation timeouts and commit code 50 as terminal even with retry labels" in {
      val errors = List(new MongoOperationTimeoutException("timeout"), new MongoException(50, "max time"))
      errors.foreach { error =>
        error.addLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
        error.addLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)
      }
      run(
        errors
          .foldLeft(IO.unit) { (previous, error) =>
            previous *> IO.defer {
              val fixture = new TransactionFixture
              fixture.commitOutcomes = List(Left(error))
              session(fixture).withTransaction(IO.unit).attempt.map { result =>
                result mustBe Left(error)
                fixture.events mustBe List("start", "commit")
                ()
              }
            }
          }
          .as(succeed)
      )
    }

    "not replay a body merely because it reports an uncertain commit" in {
      val fixture = new TransactionFixture
      val error   = uncertain
      run(session(fixture).withTransaction(IO.raiseError[Unit](error)).attempt.map { result =>
        result mustBe Left(error)
        fixture.events mustBe List("start", "abort")
      })
    }

    "respect callbacks that already commit or abort" in {
      val committed  = new TransactionFixture
      val aborted    = new TransactionFixture
      val committing = session(committed)
      val aborting   = session(aborted)
      run(for {
        first  <- committing.withTransaction(committing.commitTransaction.as(1))
        second <- aborting.withTransaction(aborting.abortTransaction.as(2))
      } yield {
        first mustBe 1
        second mustBe 2
        committed.events mustBe List("start", "commit")
        aborted.events mustBe List("start", "abort")
      })
    }

    "abort a canceled callback before closing the session" in {
      val fixture = new TransactionFixture
      run(for {
        entered <- Deferred[IO, Unit]
        fiber   <- client(fixture).transact(_ => IO(fixture.body()) *> entered.complete(()) *> IO.never[Unit]).start
        _       <- entered.get
        _       <- fiber.cancel
        outcome <- fiber.join
      } yield {
        outcome.isCanceled mustBe true
        fixture.events mustBe List("startSession", "start", "body", "abort", "close")
      })
    }

    "finish a masked commit before honoring cancellation and releasing the session" in {
      val fixture   = new TransactionFixture
      val publisher = new TransactionFixture.ControlledPublisher[Void]
      fixture.commitPublisher = Some(publisher)
      run(for {
        fiber  <- client(fixture).transact(_ => IO(fixture.body())).start
        _      <- await(publisher.requested)
        cancel <- fiber.cancel.start
        _      <- IO.cede
        _      <- IO {
          publisher.cancelled mustBe false
          fixture.events mustBe List("startSession", "start", "body", "commit")
          publisher.succeed()
        }
        _ <- cancel.joinWithNever
        _ <- fiber.join
      } yield fixture.events mustBe List("startSession", "start", "body", "commit", "close"))
    }

    "cancel uncertain-commit retry delays without an abort or callback replay" in {
      val fixture = new TransactionFixture
      fixture.commitOutcomes = List(Left(uncertain))
      run(for {
        fiber  <- client(fixture).transact(_ => IO(fixture.body())).start
        _      <- await(fixture.events.contains("commit"))
        _      <- fiber.cancel
        result <- fiber.join
      } yield {
        result.isCanceled mustBe true
        fixture.events mustBe List("startSession", "start", "body", "commit", "close")
      })
    }

    "cancel transaction retry delays without starting another transaction" in {
      val fixture = new TransactionFixture
      run(for {
        fiber  <- client(fixture).transact(_ => IO(fixture.body()) *> IO.raiseError[Unit](transient)).start
        _      <- await(fixture.events.contains("abort"))
        _      <- fiber.cancel
        result <- fiber.join
      } yield {
        result.isCanceled mustBe true
        fixture.events mustBe List("startSession", "start", "body", "abort", "close")
      })
    }

    "capture session acquisition failures without running the callback or closing an unacquired session" in {
      val fixture = new TransactionFixture
      val error   = new IllegalArgumentException("session")
      fixture.startSessionThrows = Some(error)
      val program = client(fixture).transact(_ => IO(fixture.body()))
      fixture.events mustBe empty
      run(program.attempt.map { result =>
        result mustBe Left(error)
        fixture.events mustBe List("startSession")
      })
    }

    "finish rollback before releasing a canceled session" in {
      val fixture   = new TransactionFixture
      val publisher = new TransactionFixture.ControlledPublisher[Void]
      fixture.abortPublisher = Some(publisher)
      run(for {
        entered <- Deferred[IO, Unit]
        fiber   <- client(fixture).transact(_ => entered.complete(()) *> IO.never[Unit]).start
        _       <- entered.get
        cancel  <- fiber.cancel.start
        _       <- await(publisher.requested)
        _       <- IO {
          publisher.cancelled mustBe false
          fixture.events mustBe List("startSession", "start", "abort")
          publisher.succeed()
        }
        _      <- cancel.joinWithNever
        result <- fiber.join
      } yield {
        result.isCanceled mustBe true
        fixture.events mustBe List("startSession", "start", "abort", "close")
      })
    }

    "surface session close errors after successful transactions" in {
      val fixture = new TransactionFixture
      val error   = new IllegalStateException("close")
      fixture.closeOutcomes = List(Left(error))
      run(client(fixture).transact(_ => IO.unit).attempt.map { result =>
        result mustBe Left(error)
        fixture.events mustBe List("startSession", "start", "commit", "close")
      })
    }

    "preserve a body failure if closing its owned session also fails" in {
      val fixture = new TransactionFixture
      val error   = new IllegalStateException("body")
      fixture.closeOutcomes = List(Left(new IllegalStateException("close")))
      run(client(fixture).transact(_ => IO.raiseError[Unit](error)).attempt.map { result =>
        result mustBe Left(error)
        fixture.events mustBe List("startSession", "start", "abort", "close")
      })
    }

    "reset commit backoff after a body retry" in {
      val fixture = new TransactionFixture
      fixture.commitOutcomes = List(Left(uncertain), Right(()))
      run(for {
        before <- IO.monotonic
        value  <- session(fixture).withTransaction(retryPolicy = TransactionRetryPolicy(maxDuration = 25.millis)) {
          IO(fixture.body()) *> IO.defer {
            if (fixture.events.count(_ == "body") == 1) IO.raiseError[Int](transient) else IO.pure(42)
          }
        }
        after <- IO.monotonic
      } yield {
        value mustBe 42
        after - before mustBe 20.millis
        fixture.events mustBe List("start", "body", "abort", "start", "body", "commit", "commit")
      })
    }
  }

  List[(String, ClientSession[IO] => IO[Unit])](
    "commit" -> (_.commitTransaction),
    "abort"  -> (_.abortTransaction)
  ).foreach { case (name, operation) =>
    s"Manual $name" should {
      "defer publisher creation until each execution" in {
        val fixture = new TransactionFixture
        val program = operation(session(fixture))
        fixture.events mustBe empty
        run((program *> program).map(_ => fixture.events mustBe List(name, name)))
      }
    }
  }
}
