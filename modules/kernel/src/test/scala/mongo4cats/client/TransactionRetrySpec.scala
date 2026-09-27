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
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TransactionRetrySpec extends AnyWordSpec with Matchers {
  private val transient = MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL
  private val unknown   = MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL

  private def error(labels: String*): MongoException = {
    val failure = new MongoException("transaction failed")
    labels.foreach(failure.addLabel)
    failure
  }

  "TransactionRetry" should {
    "retry transient transactions and uncertain commits separately" in {
      TransactionRetry.retryTransaction(error(transient)) mustBe true
      TransactionRetry.retryCommit(error(transient)) mustBe false
      TransactionRetry.retryTransactionAfterCommit(error(transient)) mustBe true

      TransactionRetry.retryTransaction(error(unknown)) mustBe false
      TransactionRetry.retryCommit(error(unknown)) mustBe true
      TransactionRetry.retryTransactionAfterCommit(error(unknown)) mustBe false
    }

    "prefer retrying commit when both labels are present" in {
      val failure = error(transient, unknown)
      TransactionRetry.retryCommit(failure) mustBe true
      TransactionRetry.retryTransactionAfterCommit(failure) mustBe false
    }

    "leave unlabeled errors and operation timeouts unchanged" in {
      val timeout = new MongoOperationTimeoutException("timed out")
      timeout.addLabel(transient)
      timeout.addLabel(unknown)

      List(new RuntimeException("failed"), error(), timeout).foreach { failure =>
        TransactionRetry.retryTransaction(failure) mustBe false
        TransactionRetry.retryCommit(failure) mustBe false
        TransactionRetry.retryTransactionAfterCommit(failure) mustBe false
      }
    }

    "stop both commit retry paths for MaxTimeMSExpired" in {
      val failure = new MongoException(50, "maximum commit time exceeded")
      failure.addLabel(transient)
      failure.addLabel(unknown)
      TransactionRetry.retryCommit(failure) mustBe false
      TransactionRetry.retryTransactionAfterCommit(failure) mustBe false
      failure.removeLabel(unknown)
      TransactionRetry.retryTransactionAfterCommit(failure) mustBe false
    }

    "retain cleanup errors without self-suppression" in {
      val original = new RuntimeException("body failed")
      val cleanup  = new RuntimeException("abort failed")
      TransactionRetry.suppress(original, cleanup)
      TransactionRetry.suppress(original, original)
      original.getSuppressed.toList mustBe List(cleanup)
    }
  }
}
