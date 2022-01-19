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
import cats.arrow.FunctionK
import cats.effect.Async
import cats.implicits._
import com.mongodb.reactivestreams.client.{ClientSession => JClientSession}
import mongo4cats.helpers._

trait ClientSession[F[_]] {
  def session: JClientSession
  def hasActiveTransaction: F[Boolean]
  def transactionOptions: F[Option[TransactionOptions]]
  def startTransaction(
      options: TransactionOptions = TransactionOptions()
  ): F[Unit]
  def abortTransaction: F[Unit]
  def commitTransaction: F[Unit]

  def mapK[G[_]](f: F ~> G): ClientSession[G]
  def asK[G[_]: Async]: ClientSession[G]
}

object ClientSession {
  def apply[F[_]: Async](session: JClientSession): ClientSession[F] =
    TransformedClientSession[F, F](session, FunctionK.id)

  final private case class TransformedClientSession[F[_]: Async, G[_]](
      session: JClientSession,
      transform: F ~> G
  ) extends ClientSession[G] {
    def hasActiveTransaction = transform {
      Async[F].delay(session.hasActiveTransaction)
    }

    def transactionOptions = transform {
      Async[F].delay {
        session.hasActiveTransaction.guard[Option].as(session.getTransactionOptions)
      }
    }

    def startTransaction(
        options: TransactionOptions = TransactionOptions()
    ) = transform {
      Async[F].delay(session.startTransaction(options))
    }

    def abortTransaction = transform {
      session.abortTransaction().asyncVoid[F]
    }

    def commitTransaction = transform {
      session.commitTransaction().asyncVoid[F]
    }

    def mapK[H[_]](f: G ~> H) =
      copy(transform = transform andThen f)

    def asK[H[_]: Async] =
      new TransformedClientSession(session, FunctionK.id)
  }
}
