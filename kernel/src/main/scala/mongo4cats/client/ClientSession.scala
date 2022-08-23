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

import cats.syntax.alternative._
import cats.syntax.functor._
import com.mongodb.reactivestreams.client.{ClientSession => JClientSession}
import mongo4cats.client.models.TransactionOptions

abstract private[mongo4cats] class ClientSession[F[_]] {
  def underlying: JClientSession

  /** Returns true if there is an active transaction on this session, and false otherwise
    *
    * @return
    *   true if there is an active transaction on this session
    */
  def hasActiveTransaction: Boolean = underlying.hasActiveTransaction

  /** Gets the transaction options. If session has no active transaction, then None is returned
    *
    * @return
    *   the transaction options
    */
  def transactionOptions: Option[TransactionOptions] =
    hasActiveTransaction.guard[Option].as(underlying.getTransactionOptions)

  /** Start a transaction in the context of this session with the given transaction options. A transaction can not be started if there is
    * already an active transaction on this session.
    *
    * @param options
    *   the options to apply to the transaction
    */
  def startTransaction(options: TransactionOptions): F[Unit]
  def startTransaction: F[Unit] = startTransaction(TransactionOptions())

  /** Abort a transaction in the context of this session. A transaction can only be aborted if one has first been started.
    */
  def abortTransaction: F[Unit]

  /** Commit a transaction in the context of this session. A transaction can only be commmited if one has first been started.
    */
  def commitTransaction: F[Unit]
}
