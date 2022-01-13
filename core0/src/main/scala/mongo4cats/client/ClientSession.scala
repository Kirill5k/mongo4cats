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

import cats.~>
import cats.effect.Async
import cats.implicits._
import com.mongodb.reactivestreams.client.{ClientSession => JClientSession}
import mongo4cats.helpers._

trait ClientSession[F[_]] { self =>
  def hasActiveTransaction: F[Boolean]
  def transactionOptions: F[Option[TransactionOptions]]
  def startTransaction(options: TransactionOptions): F[Unit]
  def startTransaction: F[Unit] = startTransaction(TransactionOptions())

  def abortTransaction: F[Unit]
  def commitTransaction: F[Unit]

  def mapK[G[_]](f: F ~> G): ClientSession[G] = new ClientSession[G] {
    def hasActiveTransaction: G[Boolean] =
      f(self.hasActiveTransaction)
    def transactionOptions: G[Option[TransactionOptions]] =
      f(self.transactionOptions)
    def startTransaction(options: TransactionOptions): G[Unit] =
      f(self.startTransaction(options))
    def abortTransaction: G[Unit] =
      f(self.abortTransaction)
    def commitTransaction: G[Unit] =
      f(self.commitTransaction)
  }
}

object ClientSession {
  def apply[F[_]](session: JClientSession)(implicit F: Async[F]) = new ClientSession[F] {
    def hasActiveTransaction: F[Boolean] =
      F.delay(session.hasActiveTransaction)

    def transactionOptions: F[Option[TransactionOptions]] =
      hasActiveTransaction flatMap { x =>
        F.delay(x.guard[Option].as(session.getTransactionOptions))
      }

    def startTransaction(options: TransactionOptions): F[Unit] =
      F.delay(session.startTransaction(options))

    def abortTransaction: F[Unit] =
      session.abortTransaction().asyncVoid[F]
    def commitTransaction: F[Unit] =
      session.commitTransaction().asyncVoid[F]
  }
}
