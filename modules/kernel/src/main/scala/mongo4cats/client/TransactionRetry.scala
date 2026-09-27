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

import com.mongodb.{MongoException, MongoOperationTimeoutException}

private[mongo4cats] object TransactionRetry {
  def retryTransaction(error: Throwable): Boolean = error match {
    case _: MongoOperationTimeoutException => false
    case mongo: MongoException             => mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)
    case _                                 => false
  }

  def retryCommit(error: Throwable): Boolean = error match {
    case _: MongoOperationTimeoutException => false
    case mongo: MongoException             =>
      mongo.getCode != 50 && mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)
    case _ => false
  }

  def retryTransactionAfterCommit(error: Throwable): Boolean = error match {
    case mongo: MongoException =>
      mongo.getCode != 50 &&
      !mongo.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL) &&
      retryTransaction(mongo)
    case _ => false
  }

  def suppress(primary: Throwable, secondary: Throwable): Unit =
    if (primary ne secondary) primary.addSuppressed(secondary)
}
