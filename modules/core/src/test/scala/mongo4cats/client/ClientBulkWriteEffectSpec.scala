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

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.mongodb.client.model.bulk.ClientBulkWriteResult
import mongo4cats.bson.Document
import mongo4cats.bson.syntax._
import mongo4cats.models.client.{ClientBulkWriteOptions, ClientWriteCommand}
import mongo4cats.models.collection.MongoNamespace
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import java.util.Collections
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

class ClientBulkWriteEffectSpec extends AsyncWordSpec with Matchers {
  private val namespace = MongoNamespace("db", "items")
  private val commands = List(ClientWriteCommand.InsertOne(namespace, Document("_id" := 1)))
  private val options = ClientBulkWriteOptions(ordered = false, verboseResults = true)
  private val session = new LiveClientSession[IO](ClientSessionStub(_ => ()))
  private val writers: List[(String, Boolean, ClientBulkWriteOptions, (MongoClient[IO], Seq[ClientWriteCommand]) => IO[ClientBulkWriteResult])] = List(
    ("default options", false, ClientBulkWriteOptions(), (client, writes) => client.bulkWrite(writes)),
    ("explicit options", false, options, (client, writes) => client.bulkWrite(writes, options)),
    ("session with default options", true, ClientBulkWriteOptions(), (client, writes) => client.bulkWrite(session, writes)),
    ("session with explicit options", true, options, (client, writes) => client.bulkWrite(session, writes, options))
  )

  writers.foreach { case (name, hasSession, expectedOptions, write) =>
    s"Client bulk writes with $name" should {
      "defer the driver call and repeat it on every execution" in {
        val calls = new AtomicInteger()
        val client = new LiveMongoClient[IO](ClientBulkWriteFixture.client { _ =>
          calls.incrementAndGet()
          ClientBulkWriteFixture.succeed(ClientBulkWriteFixture.result)
        })
        val program = write(client, commands)
        calls.get() mustBe 0
        (for {
          first <- program
          afterFirst <- IO(calls.get())
          second <- program
        } yield {
          afterFirst mustBe 1
          calls.get() mustBe 2
          first must be theSameInstanceAs ClientBulkWriteFixture.result
          second must be theSameInstanceAs first
        }).unsafeToFuture()
      }

      "forward converted models, options and session" in {
        val observed = new AtomicReference[Array[AnyRef]]()
        val client = new LiveMongoClient[IO](ClientBulkWriteFixture.client { args =>
          observed.set(args)
          ClientBulkWriteFixture.succeed(ClientBulkWriteFixture.result)
        })
        write(client, commands).map { result =>
          val args = observed.get()
          val offset = if (hasSession) 1 else 0
          result must be theSameInstanceAs ClientBulkWriteFixture.result
          args.length mustBe offset + 2
          args(offset).toString mustBe Collections.singletonList(commands.head.writeModel).toString
          args(offset + 1).toString mustBe expectedOptions.toString
          if (hasSession) args(0) must be theSameInstanceAs session.underlying
          else succeed
        }.unsafeToFuture()
      }

      "capture synchronous driver failures inside the effect" in {
        val calls = new AtomicInteger()
        val error = new IllegalStateException("driver invocation failed")
        val client = new LiveMongoClient[IO](ClientBulkWriteFixture.client { _ =>
          calls.incrementAndGet()
          throw error
        })
        val program = write(client, commands)
        calls.get() mustBe 0
        program.attempt.map { result =>
          result mustBe Left(error)
          calls.get() mustBe 1
        }.unsafeToFuture()
      }

      "capture invalid command conversion without calling the driver" in {
        val calls = new AtomicInteger()
        val client = new LiveMongoClient[IO](ClientBulkWriteFixture.client { _ =>
          calls.incrementAndGet()
          ClientBulkWriteFixture.succeed(ClientBulkWriteFixture.result)
        })
        val program = write(client, List(ClientWriteCommand.InsertOne[Document](namespace, null)))
        program.attempt.map { result =>
          result.swap.toOption.get mustBe a[IllegalArgumentException]
          calls.get() mustBe 0
        }.unsafeToFuture()
      }

      "preserve publisher exceptions including cause, concern errors and partial results" in {
        val error = ClientBulkWriteFixture.failure
        val client = new LiveMongoClient[IO](ClientBulkWriteFixture.client(_ => ClientBulkWriteFixture.fail(error)))
        write(client, commands).attempt.map { result =>
          result.swap.toOption.get must be theSameInstanceAs error
          error.getCause.getCode mustBe 91
          error.getWriteConcernErrors.get(0).getCode mustBe 64
          error.getWriteErrors.get(1).getCode mustBe 11000
          error.getPartialResult.get() must be theSameInstanceAs ClientBulkWriteFixture.result
        }.unsafeToFuture()
      }
    }
  }
}
