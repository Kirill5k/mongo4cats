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

import scala.concurrent.duration._

/** Bounds retries of a managed transaction and its commit using one elapsed-time budget.
  *
  * The budget limits new retry attempts; it does not interrupt a running callback or driver operation. Retry delays double up to
  * `maxDelay`. Set `maxDuration` to zero, or use [[TransactionRetryPolicy.none]], to disable retries.
  */
final case class TransactionRetryPolicy(
    maxDuration: FiniteDuration = 2.minutes,
    initialDelay: FiniteDuration = 10.millis,
    maxDelay: FiniteDuration = 1.second
) {
  require(maxDuration >= Duration.Zero, "maxDuration must be non-negative")
  require(initialDelay > Duration.Zero, "initialDelay must be positive")
  require(maxDelay >= initialDelay, "maxDelay must be at least initialDelay")

  private[mongo4cats] def nextDelay(current: FiniteDuration): FiniteDuration =
    if (current >= maxDelay || current >= maxDelay - current) maxDelay else current + current
}

object TransactionRetryPolicy {
  val default: TransactionRetryPolicy = TransactionRetryPolicy()
  val none: TransactionRetryPolicy    = TransactionRetryPolicy(maxDuration = Duration.Zero)
}
