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
import com.mongodb.reactivestreams.client.{ClientSession => JClientSession}
import mongo4cats.helpers._

import scala.util.Try

abstract class ClientSession[F[_]] {
  private[client] def session: JClientSession

  def hasActiveTransaction: Boolean
  def transactionOptions: TransactionOptions

  def startTransaction(options: TransactionOptions): F[Unit]
  def startTransaction: F[Unit] = startTransaction(TransactionOptions())

  def abortTransaction: F[Unit]
  def commitTransaction: F[Unit]
}

final private class LiveClientSession[F[_]](
    private[client] val session: JClientSession
)(implicit F: Async[F])
    extends ClientSession[F] {

  override def hasActiveTransaction: Boolean          = session.hasActiveTransaction
  override def transactionOptions: TransactionOptions = session.getTransactionOptions

  override def startTransaction(options: TransactionOptions): F[Unit] =
    F.fromTry(Try(session.startTransaction(options)))

  override def commitTransaction: F[Unit] =
    session.commitTransaction().asyncVoid[F]

  override def abortTransaction: F[Unit] =
    session.abortTransaction().asyncVoid[F]
}

object ClientSession {}
