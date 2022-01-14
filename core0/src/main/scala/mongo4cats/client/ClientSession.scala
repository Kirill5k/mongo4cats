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
import cats.implicits._
import com.mongodb.reactivestreams.client.{ClientSession => JClientSession}
import mongo4cats.helpers._

final case class ClientSession(session: JClientSession) {
  def hasActiveTransaction[F[_]: Async]: F[Boolean] =
    Async[F].delay(session.hasActiveTransaction)

  def transactionOptions[F[_]: Async]: F[Option[TransactionOptions]] =
    hasActiveTransaction[F] flatMap { x =>
      Async[F].delay(x.guard[Option].as(session.getTransactionOptions))
    }

  def startTransaction[F[_]: Async](
      options: TransactionOptions = TransactionOptions()
  ): F[Unit] =
    Async[F].delay(session.startTransaction(options))

  def abortTransaction[F[_]: Async]: F[Unit] =
    session.abortTransaction().asyncVoid[F]

  def commitTransaction[F[_]: Async]: F[Unit] =
    session.commitTransaction().asyncVoid[F]
}

object ClientSession {
  val void: ClientSession = ClientSession(null)
}
