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

import cats.effect.Async
import cats.effect.syntax.all._
import cats.syntax.all._
import mongo4cats.models.client.{TransactionOptions, TransactionRetryPolicy}

import scala.concurrent.duration.FiniteDuration

private[client] object ManagedTransaction {
  def run[F[_], A](session: ClientSession[F], options: TransactionOptions, policy: TransactionRetryPolicy)(
      body: => F[A]
  )(implicit F: Async[F]): F[A] = F.defer {
    F.monotonic.flatMap { started =>
      F.uncancelable { poll =>
        def abort: F[Unit] = F.defer {
          if (session.hasActiveTransaction) session.abortTransaction else F.unit
        }

        def retry(error: Throwable, delay: FiniteDuration)(next: FiniteDuration => F[A]): F[A] =
          F.monotonic.flatMap { now =>
            val remaining = policy.maxDuration - (now - started)
            if (remaining <= FiniteDuration(0, java.util.concurrent.TimeUnit.NANOSECONDS)) F.raiseError[A](error)
            else
              poll(F.sleep(delay.min(remaining))) *> F.monotonic.flatMap { after =>
                if (after - started >= policy.maxDuration) F.raiseError[A](error)
                else F.defer(next(policy.nextDelay(delay)))
              }
          }

        def commit(value: A, transactionDelay: FiniteDuration, commitDelay: FiniteDuration): F[A] =
          F.defer(session.commitTransaction).attempt.flatMap {
            case Right(_)                                           => F.pure(value)
            case Left(error) if TransactionRetry.retryCommit(error) =>
              // The driver marks the session inactive even after an uncertain commit. Do not inspect active state here.
              retry(error, commitDelay)(nextDelay => commit(value, transactionDelay, nextDelay))
            case Left(error) if TransactionRetry.retryTransactionAfterCommit(error) =>
              F.delay(session.hasActiveTransaction).flatMap {
                // A custom session can throw before submitting commit. Preserve that failure if restarting is unsafe.
                case true  => F.raiseError[A](error)
                case false => retry(error, transactionDelay)(transaction)
              }
            case Left(error) => F.raiseError[A](error)
          }

        def transaction(delay: FiniteDuration): F[A] = {
          val start = F.defer {
            if (session.hasActiveTransaction) F.raiseError[Unit](new IllegalStateException("Transaction already in progress"))
            else session.startTransaction(options)
          }
          start *> poll(F.defer(body)).onCancel(abort).attempt.flatMap {
            case Right(value) =>
              F.delay(session.hasActiveTransaction).flatMap {
                case true  => commit(value, delay, policy.initialDelay)
                case false => F.pure(value)
              }
            case Left(error) =>
              abort.attempt.flatMap {
                case Left(cleanupError) => F.delay(TransactionRetry.suppress(error, cleanupError)) *> F.raiseError[A](error)
                case Right(_) if TransactionRetry.retryTransaction(error) => retry(error, delay)(transaction)
                case Right(_)                                             => F.raiseError[A](error)
              }
          }
        }

        transaction(policy.initialDelay)
      }
    }
  }
}
