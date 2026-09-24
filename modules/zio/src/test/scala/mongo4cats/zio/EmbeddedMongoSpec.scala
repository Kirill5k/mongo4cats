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

import mongo4cats.test.FreePort
import mongo4cats.zio.embedded.EmbeddedMongo
import zio.{durationInt, Promise, Ref, Scope, Task, ZIO}
import zio.test._
import zio.test.TestAspect.{sequential, timeout, withLiveClock}

import java.net.{ConnectException, InetSocketAddress, Socket}

object EmbeddedMongoSpec extends ZIOSpecDefault with EmbeddedMongo {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Embedded Mongo lifecycle")(
    test("releases each invocation and its body resources before reusing the same port") {
      ZIO.scoped[Any] {
        for {
          port      <- ZIO.succeed(FreePort.next())
          finalized <- Ref.make(List.empty[Boolean])
          first     <- withRunningEmbeddedMongo(port)(bodyResource(port, finalized) *> isListening(port))
          afterFirst <- isListening(port)
          second     <- withRunningEmbeddedMongo(port)(bodyResource(port, finalized) *> isListening(port))
          afterSecond <- isListening(port)
          releases    <- finalized.get
        } yield assertTrue(first, !afterFirst, second, !afterSecond, releases == List(true, true))
      }
    },
    test("releases body resources and the process when the body fails") {
      val error = new IllegalStateException("body failed")
      ZIO.scoped[Any] {
        for {
          port      <- ZIO.succeed(FreePort.next())
          finalized <- Ref.make(List.empty[Boolean])
          result <- withRunningEmbeddedMongo(port)(bodyResource(port, finalized) *> ZIO.fail(error)).either
          listening <- isListening(port)
          releases  <- finalized.get
        } yield assertTrue(result == Left(error), !listening, releases == List(true))
      }
    },
    test("releases body resources and the process when the body is interrupted") {
      ZIO.scoped[Any] {
        for {
          port      <- ZIO.succeed(FreePort.next())
          finalized <- Ref.make(List.empty[Boolean])
          started   <- Promise.make[Nothing, Unit]
          fiber <- withRunningEmbeddedMongo(port)(bodyResource(port, finalized) *> started.succeed(()) *> ZIO.never).fork
          _         <- started.await
          exit      <- fiber.interrupt
          listening <- isListening(port)
          releases  <- finalized.get
        } yield assertTrue(exit.isInterrupted, !listening, releases == List(true))
      }
    }
  ) @@ sequential @@ withLiveClock @@ timeout(2.minutes)

  private def bodyResource(port: Int, finalized: Ref[List[Boolean]]): ZIO[Scope, Nothing, Unit] =
    ZIO.acquireRelease(ZIO.unit)(_ => isListening(port).orDie.flatMap(listening => finalized.update(_ :+ listening)))

  private def isListening(port: Int): Task[Boolean] = ZIO.attemptBlocking {
    val socket = new Socket()
    try {
      socket.connect(new InetSocketAddress("localhost", port), 1000)
      true
    } catch {
      case _: ConnectException => false
    } finally socket.close()
  }
}
