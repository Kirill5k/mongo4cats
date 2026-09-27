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

package mongo4cats.zio

import mongo4cats.client.TransactionFixture
import mongo4cats.models.client.{ClientSessionOptions, TransactionOptions, TransactionRetryPolicy}
import zio.{Scope, ZIO}
import zio.test._

object ZNativeTransactionsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Native managed ZIO transactions")(
    test("run both overloads on a public session without importing transaction syntax") {
      val fixture                 = new TransactionFixture
      val session: ZClientSession = new ZClientSessionLive(fixture.session)
      val options                 = TransactionOptions()
      for {
        first  <- session.withTransaction(ZIO.succeed(17))
        second <- session.withTransaction(options, TransactionRetryPolicy.none)(ZIO.succeed(42))
      } yield assertTrue(
        first == 17,
        second == 42,
        fixture.events == List("start", "commit", "start", "commit"),
        fixture.startOptions.last eq options
      )
    },
    test("run both native client overloads through the public client type without transaction syntax") {
      val fixture              = new TransactionFixture
      val client: ZMongoClient = new ZMongoClientLive(fixture.client)
      val options              = TransactionOptions()
      val sessionOptions       = ClientSessionOptions(causallyConsistent = false)
      for {
        first  <- client.transact(_ => ZIO.succeed(17))
        second <- client.transact(options, TransactionRetryPolicy.none, sessionOptions)(_ => ZIO.succeed(42))
      } yield assertTrue(
        first == 17,
        second == 42,
        fixture.events == List("startSession", "start", "commit", "close", "startSession", "start", "commit", "close"),
        fixture.startOptions.last eq options,
        fixture.sessionOptions.last eq sessionOptions
      )
    }
  )
}
