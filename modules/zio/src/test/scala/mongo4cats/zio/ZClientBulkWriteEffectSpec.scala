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

import com.mongodb.client.model.bulk.{ClientBulkWriteResult, ClientNamespacedWriteModel}
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.client.{ClientBulkWriteFixture, ClientSessionStub}
import mongo4cats.models.client.{ClientBulkWriteOptions, ClientWriteCommand}
import mongo4cats.models.collection.MongoNamespace
import zio.{durationInt, Scope, Task, ZIO}
import zio.test._

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

object ZClientBulkWriteEffectSpec extends ZIOSpecDefault {
  private val namespace = MongoNamespace("db", "items")
  private val commands = List(ClientWriteCommand.InsertOne(namespace, Document("_id" := 1)))
  private val options = ClientBulkWriteOptions(ordered = false, verboseResults = true)
  private val session = new ZClientSessionLive(ClientSessionStub(_ => ()))
  private val writers: List[(String, Boolean, ClientBulkWriteOptions, (ZMongoClient, Seq[ClientWriteCommand]) => Task[ClientBulkWriteResult])] = List(
    ("default options", false, ClientBulkWriteOptions(), (client, writes) => client.bulkWrite(writes)),
    ("explicit options", false, options, (client, writes) => client.bulkWrite(writes, options)),
    ("session with default options", true, ClientBulkWriteOptions(), (client, writes) => client.bulkWrite(session, writes)),
    ("session with explicit options", true, options, (client, writes) => client.bulkWrite(session, writes, options))
  )

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Client bulk write effects")(
    writers.map { case (name, hasSession, expectedOptions, write) =>
      suite(name)(
        test("defer the driver call and repeat it on every execution") {
          val calls = new AtomicInteger()
          val client = new ZMongoClientLive(ClientBulkWriteFixture.client { _ =>
            calls.incrementAndGet()
            ClientBulkWriteFixture.succeed(ClientBulkWriteFixture.result)
          })
          val effect = write(client, commands)
          val before = calls.get()
          for {
            first <- effect
            afterFirst <- ZIO.succeed(calls.get())
            second <- effect
          } yield assertTrue(before == 0, afterFirst == 1, calls.get() == 2, first eq ClientBulkWriteFixture.result, second eq first)
        },
        test("forward the converted models, options and session") {
          val observed = new AtomicReference[Array[AnyRef]]()
          val client = new ZMongoClientLive(ClientBulkWriteFixture.client { args =>
            observed.set(args)
            ClientBulkWriteFixture.succeed(ClientBulkWriteFixture.result)
          })
          write(client, commands).map { result =>
            val args = observed.get()
            val offset = if (hasSession) 1 else 0
            val receivedModels = args(offset).asInstanceOf[java.util.List[ClientNamespacedWriteModel]]
            assertTrue(
              result eq ClientBulkWriteFixture.result,
              args.length == offset + 2,
              receivedModels.size() == 1,
              receivedModels.get(0).toString == commands.head.writeModel.toString,
              args(offset + 1).toString == expectedOptions.toString,
              !hasSession || (args(0) eq session.underlying)
            )
          }
        },
        test("capture synchronous driver errors without changing the exception") {
          val calls = new AtomicInteger()
          val error = new IllegalStateException("driver invocation failed")
          val client = new ZMongoClientLive(ClientBulkWriteFixture.client { _ =>
            calls.incrementAndGet()
            throw error
          })
          val effect = write(client, commands)
          val before = calls.get()
          effect.either.map(result => assertTrue(before == 0, result == Left(error), calls.get() == 1))
        },
        test("capture model validation failures inside the effect") {
          val calls = new AtomicInteger()
          val client = new ZMongoClientLive(ClientBulkWriteFixture.client { _ =>
            calls.incrementAndGet()
            ClientBulkWriteFixture.succeed(ClientBulkWriteFixture.result)
          })
          val invalid = List(ClientWriteCommand.InsertOne[Document](namespace, null))
          val effect = write(client, invalid)
          effect.either.map(result => assertTrue(result.left.exists(_.isInstanceOf[IllegalArgumentException]), calls.get() == 0))
        },
        test("preserve publisher bulk errors including cause, write concern errors and partial results") {
          val error = ClientBulkWriteFixture.failure
          val client = new ZMongoClientLive(ClientBulkWriteFixture.client(_ => ClientBulkWriteFixture.fail(error)))
          write(client, commands).either.map { result =>
            assertTrue(
              result == Left(error),
              error.getCause.getCode == 91,
              error.getWriteConcernErrors.get(0).getCode == 64,
              error.getWriteErrors.get(1).getCode == 11000,
              error.getPartialResult.get() eq ClientBulkWriteFixture.result
            )
          }
        }
      )
    }
  ) @@ TestAspect.timeout(10.seconds)
}
