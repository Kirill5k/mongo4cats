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

package mongo4cats

import cats.effect.{Async, IO, Resource}
import cats.implicits._
import de.flapdoodle.embed.mongo.{MongodExecutable, MongodProcess, MongodStarter}
import de.flapdoodle.embed.mongo.config.{MongodConfig, Net}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network
import org.slf4j.LoggerFactory

import scala.concurrent.duration._

object EmbeddedMongo {
  private val starter = MongodStarter.getDefaultInstance
  private val logger  = LoggerFactory.getLogger("EmbeddedMongo")

  def prepare[F[_]: Async](config: MongodConfig, maxAttempts: Int = 5, attempt: Int = 0): F[MongodExecutable] =
    if (attempt >= maxAttempts)
      Async[F].raiseError(new RuntimeException("tried to prepare executable far too many times"))
    else
      Async[F].delay(starter.prepare(config)).handleErrorWith { e =>
        Async[F].delay(logger.error(e.getMessage, e)) *>
          Async[F].sleep(attempt.seconds) *>
          prepare[F](config, maxAttempts, attempt + 1)
      }

  implicit final class MongodExecutableOps(private val ex: MongodExecutable) extends AnyVal {
    def startWithRetry[F[_]: Async](maxAttempts: Int = 5, attempt: Int = 0): F[MongodProcess] =
      if (attempt >= maxAttempts)
        Async[F].raiseError(new RuntimeException("tried to start executable far too many times"))
      else
        Async[F].delay(ex.start()).handleErrorWith { e =>
          Async[F].delay(logger.error(e.getMessage, e)) *>
            Async[F].sleep(attempt.seconds) *>
            startWithRetry(maxAttempts, attempt + 1)
        }
  }
}

trait EmbeddedMongo {
  import EmbeddedMongo._

  protected val mongoHost = "localhost"
  protected val mongoPort = 12345

  def withRunningEmbeddedMongo[A](test: => IO[A]): IO[A] = {
    val mongodConfig = MongodConfig
      .builder()
      .version(Version.Main.PRODUCTION)
      .net(new Net(mongoHost, mongoPort, Network.localhostIsIPv6))
      .build

    Resource
      .make(EmbeddedMongo.prepare[IO](mongodConfig))(ex => IO(ex.stop()))
      .flatMap(ex => Resource.make(ex.startWithRetry[IO]())(pr => IO(pr.stop())))
      .use(_ => test)
      .timeout(5.minutes)
  }
}
