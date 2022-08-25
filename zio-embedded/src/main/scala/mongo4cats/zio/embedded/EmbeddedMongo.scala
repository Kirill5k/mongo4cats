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

package mongo4cats.zio.embedded

import de.flapdoodle.embed.mongo.config.{ImmutableMongodConfig, MongodConfig, Net}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.{MongodProcess, MongodStarter}
import de.flapdoodle.embed.process.runtime.Network
import zio._

object EmbeddedMongo {

  private val defaultStarter = ZIO.attempt(MongodStarter.getDefaultInstance)

  def start(config: MongodConfig): ZIO[Scope, Nothing, MongodProcess] =
    defaultStarter.orDie.flatMap(s => attemptStart(config, s))

  private def attemptStart(
      config: MongodConfig,
      starter: MongodStarter,
      maxAttempts: Int = 10,
      attempt: Int = 0,
      lastError: Option[Throwable] = None
  ): ZIO[Scope, Nothing, MongodProcess] =
    if (attempt >= maxAttempts) {
      ZIO.die(lastError.getOrElse(new RuntimeException("Failed to start embedded mongo too many times")))
    } else {
      ZIO
        .acquireRelease(ZIO.attemptBlocking(starter.prepare(config)))(ex => ZIO.attemptBlocking(ex.stop()).orDie)
        .flatMap(ex => ZIO.acquireRelease(ZIO.attemptBlocking(ex.start()))(p => ZIO.attemptBlocking(p.stop()).orDie))
        .catchAll { e =>
          attemptStart(config, starter, maxAttempts, attempt + 1, Some(e))
        }
    }
}

trait EmbeddedMongo {
  protected val mongoHost: String             = "localhost"
  protected val mongoPort: Int                = 27017
  protected val mongoUsername: Option[String] = None
  protected val mongoPassword: Option[String] = None

  def withRunningEmbeddedMongo[R, E, A](test: => ZIO[R, E, A]): ZIO[R with Scope, E, A] =
    runMongo(mongoHost, mongoPort, mongoUsername, mongoPassword)(test)

  def withRunningEmbeddedMongo[R, E, A](host: String, port: Int)(test: => ZIO[R, E, A]): ZIO[R with Scope, E, A] =
    runMongo(host, port, None, None)(test)

  def withRunningEmbeddedMongo[R, E, A](host: String, port: Int, username: String, password: String)(
      test: => ZIO[R, E, A]
  ): ZIO[R with Scope, E, A] =
    runMongo(host, port, Some(username), Some(password))(test)

  private def runMongo[R, E, A](host: String, port: Int, username: Option[String], password: Option[String])(
      test: => ZIO[R, E, A]
  ): ZIO[R with Scope, E, A] =
    EmbeddedMongo
      .start(
        MongodConfig
          .builder()
          .withUsername(username)
          .withPassword(password)
          .version(Version.Main.PRODUCTION)
          .net(new Net(host, port, Network.localhostIsIPv6))
          .build
      ) *> test

  implicit final private class BuilderSyntax(private val builder: ImmutableMongodConfig.Builder) {
    def withUsername(username: Option[String]): ImmutableMongodConfig.Builder = username.fold(builder)(builder.userName)
    def withPassword(password: Option[String]): ImmutableMongodConfig.Builder = password.fold(builder)(builder.password)
  }
}
