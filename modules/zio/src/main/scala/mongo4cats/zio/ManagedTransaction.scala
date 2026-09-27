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

import mongo4cats.client.TransactionRetry
import mongo4cats.models.client.{ClientSessionOptions, TransactionOptions, TransactionRetryPolicy}
import zio.{Cause, Clock, Duration, Exit, RIO, Scope, Task, UIO, ZIO}

import scala.concurrent.duration.FiniteDuration

private[zio] object ManagedTransaction {

  def transact[A](
      client: ZMongoClient,
      options: TransactionOptions,
      retryPolicy: TransactionRetryPolicy,
      sessionOptions: ClientSessionOptions
  )(body: ZClientSession => Task[A]): Task[A] =
    ZIO.uninterruptibleMask { restore =>
      Scope.make.flatMap { scope =>
        val program = scope.extend[Any](ZIO.attempt(client.startSession(sessionOptions)).flatten).flatMap { session =>
          ZIO.attempt(session.withTransaction[A](options, retryPolicy)(ZIO.attempt(body(session)).flatten)).flatten
        }
        restore(program).exit.flatMap { outcome =>
          scope.close(outcome).exit.flatMap {
            case Exit.Success(_)       => outcome
            case Exit.Failure(cleanup) =>
              outcome match {
                case Exit.Success(_)     => ZIO.failCause(cleanup)
                case Exit.Failure(cause) => reportCleanup(cause, cleanup, "Transaction session cleanup failed") *> ZIO.failCause(cause)
              }
          }
        }
      }
    }

  def run[R, A](session: ZClientSession, options: TransactionOptions, policy: TransactionRetryPolicy)(
      body: => RIO[R, A]
  ): RIO[R, A] =
    ZIO.uninterruptibleMask { restore =>
      Clock.nanoTime.flatMap { started =>
        def retry(cause: Cause[Throwable], delay: FiniteDuration)(next: => RIO[R, A]): RIO[R, A] =
          Clock.nanoTime.flatMap { now =>
            val remaining = policy.maxDuration.toNanos - (now - started)
            if (remaining <= 0L) ZIO.failCause(cause)
            else
              restore(ZIO.sleep(Duration.fromNanos(math.min(delay.toNanos, remaining)))) *> Clock.nanoTime.flatMap { after =>
                if (after - started >= policy.maxDuration.toNanos) ZIO.failCause(cause)
                else next
              }
          }

        def commit(value: A, transactionDelay: FiniteDuration, commitDelay: FiniteDuration): RIO[R, A] =
          ZIO.attempt(session.commitTransaction).flatten.exit.flatMap {
            case Exit.Success(_)     => ZIO.succeed(value)
            case Exit.Failure(cause) =>
              retryableFailure(cause) match {
                case Some(error) if TransactionRetry.retryCommit(error) =>
                  retry(cause, commitDelay)(commit(value, transactionDelay, policy.nextDelay(commitDelay)))
                case Some(error) if TransactionRetry.retryTransactionAfterCommit(error) =>
                  ZIO.attempt(session.hasActiveTransaction).flatMap { active =>
                    if (active) ZIO.failCause(cause)
                    else retry(cause, transactionDelay)(attempt(policy.nextDelay(transactionDelay)))
                  }
                case _ => ZIO.failCause(cause)
              }
          }

        def attempt(delay: FiniteDuration): RIO[R, A] =
          ZIO.attempt(session.startTransaction(options)).flatten *>
            restore(ZIO.attempt(body).flatten).exit.flatMap {
              case Exit.Success(value) =>
                ZIO.attempt(session.hasActiveTransaction).flatMap { active =>
                  if (active) commit(value, delay, policy.initialDelay) else ZIO.succeed(value)
                }
              case Exit.Failure(cause) =>
                abort(session).exit.flatMap {
                  case Exit.Failure(cleanup) =>
                    reportCleanup(cause, cleanup, "Transaction rollback failed") *> ZIO.failCause(cause)
                  case Exit.Success(_) =>
                    retryableFailure(cause) match {
                      case Some(error) if TransactionRetry.retryTransaction(error) =>
                        retry(cause, delay)(attempt(policy.nextDelay(delay)))
                      case _ => ZIO.failCause(cause)
                    }
                }
            }

        ZIO.attempt(session.hasActiveTransaction).flatMap { active =>
          if (active) ZIO.fail(new IllegalStateException("A managed transaction cannot start while a transaction is already active"))
          else attempt(policy.initialDelay)
        }
      }
    }

  private def abort(session: ZClientSession): Task[Unit] =
    ZIO.attempt(session.hasActiveTransaction).flatMap { active =>
      if (active) ZIO.attempt(session.abortTransaction).flatten else ZIO.unit
    }

  private def reportCleanup(cause: Cause[Throwable], cleanup: Cause[Throwable], message: String): UIO[Unit] =
    (cause.failures ++ cause.defects).headOption match {
      case Some(primary) =>
        ZIO.succeed {
          (cleanup.failures ++ cleanup.defects).foreach(TransactionRetry.suppress(primary, _))
        }
      case None => ZIO.logErrorCause(message, cleanup)
    }

  private def retryableFailure(cause: Cause[Throwable]): Option[Throwable] =
    if (cause.isInterrupted || cause.defects.nonEmpty) None
    else
      cause.failures match {
        case error :: Nil => Some(error)
        case _            => None
      }
}
