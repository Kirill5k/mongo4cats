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

package mongo4cats.embedded

import cats.effect.{Async, Resource}
import cats.syntax.apply._
import cats.syntax.applicativeError._
import de.flapdoodle.embed.mongo.config.{ImmutableMongodConfig, MongodConfig, Net}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.{MongodProcess, MongodStarter}
import de.flapdoodle.embed.process.runtime.Network

import scala.concurrent.duration._

object EmbeddedMongo {

  private lazy val defaultStarter: MongodStarter = MongodStarter.getDefaultInstance

  def start[F[_]](
      config: MongodConfig,
      starter: MongodStarter = defaultStarter,
      maxAttempts: Int = 10,
      attempt: Int = 0,
      lastError: Option[Throwable] = None
  )(implicit F: Async[F]): Resource[F, MongodProcess] =
    if (attempt >= maxAttempts) {
      val error = lastError.getOrElse(new RuntimeException("Failed to start embedded mongo too many times"))
      Resource.eval(error.raiseError[F, MongodProcess])
    } else
      Resource
        .make(F.delay(starter.prepare(config)))(ex => F.delay(ex.stop()))
        .flatMap(ex => Resource.make(F.delay(ex.start()))(p => F.delay(p.stop())))
        .handleErrorWith[MongodProcess, Throwable] { e =>
          Resource.eval(F.sleep(attempt.seconds)) *> start[F](config, starter, maxAttempts, attempt + 1, Some(e))
        }
}

trait EmbeddedMongo {
  protected val mongoHost = "localhost"
  protected val mongoPort = 27017

  def withRunningEmbeddedMongo[F[_]: Async, A](test: => F[A]): F[A] =
    runMongo(mongoHost, mongoPort, None, None)(test)

  def withRunningEmbeddedMongo[F[_]: Async, A](host: String, port: Int)(test: => F[A]): F[A] =
    runMongo(host, port, None, None)(test)

  def withRunningEmbeddedMongo[F[_]: Async, A](host: String, port: Int, username: String, password: String)(test: => F[A]): F[A] =
    runMongo(host, port, Some(username), Some(password))(test)

  private def runMongo[F[_]: Async, A](host: String, port: Int, username: Option[String], password: Option[String])(test: => F[A]): F[A] =
    EmbeddedMongo
      .start[F] {
        MongodConfig
          .builder()
          .withUsername(username)
          .withPassword(password)
          .version(Version.Main.PRODUCTION)
          .net(new Net(host, port, Network.localhostIsIPv6))
          .build
      }
      .use(_ => test)

  implicit final class BuilderSyntax(private val builder: ImmutableMongodConfig.Builder) {
    def withUsername(username: Option[String]): ImmutableMongodConfig.Builder = username.fold(builder)(builder.userName)
    def withPassword(password: Option[String]): ImmutableMongodConfig.Builder = password.fold(builder)(builder.password)
  }
}
