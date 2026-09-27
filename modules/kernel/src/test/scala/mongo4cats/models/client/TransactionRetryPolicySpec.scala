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

package mongo4cats.models.client

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.duration._

class TransactionRetryPolicySpec extends AnyWordSpec with Matchers {
  "TransactionRetryPolicy" should {
    "provide a bounded default and explicitly disabled retries" in {
      TransactionRetryPolicy.default.maxDuration mustBe 2.minutes
      TransactionRetryPolicy.none.maxDuration mustBe Duration.Zero
    }

    "reject negative budgets, non-positive delays, and decreasing delay bounds" in {
      intercept[IllegalArgumentException](TransactionRetryPolicy(maxDuration = Duration.Zero - 1.second))
      intercept[IllegalArgumentException](TransactionRetryPolicy(initialDelay = Duration.Zero))
      intercept[IllegalArgumentException](TransactionRetryPolicy(initialDelay = Duration.Zero - 1.millis))
      intercept[IllegalArgumentException](TransactionRetryPolicy(initialDelay = 2.seconds, maxDelay = 1.second))
    }

    "cap increasing delays even when doubling would overflow" in {
      val policy = TransactionRetryPolicy(initialDelay = 10.millis, maxDelay = 25.millis)
      policy.nextDelay(10.millis) mustBe 20.millis
      policy.nextDelay(20.millis) mustBe 25.millis
      policy.nextDelay(25.millis) mustBe 25.millis

      val maximum = Long.MaxValue.nanos
      TransactionRetryPolicy(maxDelay = maximum).nextDelay((Long.MaxValue - 1L).nanos) mustBe maximum
    }
  }
}
