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

import com.mongodb.client.MongoClients
import de.flapdoodle.embed.mongo.commands.MongodArguments
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.transitions.{Mongod, RunningMongodProcess}
import de.flapdoodle.reverse.transitions.Start
import de.flapdoodle.reverse.{Listener, StateID, TransitionWalker}
import org.bson.Document
import zio.{Scope, ZIO}

trait EmbeddedMongo {
  protected val mongoPort: Int                = 27017
  protected val mongoUsername: Option[String] = None
  protected val mongoPassword: Option[String] = None

  def withRunningEmbeddedMongo[R, E, A](test: => ZIO[R, E, A]): ZIO[R with Scope, E, A] =
    EmbeddedMongo.start(mongoPort, mongoUsername, mongoPassword) *> test

  def withRunningEmbeddedMongo[R, E, A](
      mongoUsername: String,
      mongoPassword: String
  )(
      test: => ZIO[R, E, A]
  ): ZIO[R with Scope, E, A] =
    EmbeddedMongo.start(mongoPort, Some(mongoUsername), Some(mongoPassword)) *> test

  def withRunningEmbeddedMongo[R, E, A](
      mongoPort: Int
  )(
      test: => ZIO[R, E, A]
  ): ZIO[R with Scope, E, A] =
    EmbeddedMongo.start(mongoPort, mongoUsername, mongoPassword) *> test

  def withRunningEmbeddedMongo[R, E, A](
      mongoPort: Int,
      mongoUsername: String,
      mongoPassword: String
  )(
      test: => ZIO[R, E, A]
  ): ZIO[R with Scope, E, A] =
    EmbeddedMongo.start(mongoPort, Some(mongoUsername), Some(mongoPassword)) *> test
}

object EmbeddedMongo {

  def start(
      port: Int,
      username: Option[String],
      password: Option[String]
  ): ZIO[Scope, Nothing, Unit] =
    ZIO.acquireRelease(ZIO.attemptBlocking(startMongod(port, username, password)))(p => ZIO.attempt(p.close()).orDie).unit.orDie

  private def startMongod(
      port: Int,
      username: Option[String],
      password: Option[String]
  ): TransitionWalker.ReachedState[RunningMongodProcess] = {
    val withAuth = username.isDefined && password.isDefined
    val listener = if (withAuth) Some(insertUserListener(username.get, password.get)) else None
    Mongod
      .builder()
      .net(Start.to(classOf[Net]).initializedWith(Net.defaults().withPort(port)))
      .mongodArguments(Start.to(classOf[MongodArguments]).initializedWith(MongodArguments.defaults().withAuth(withAuth)))
      .build()
      .start(Version.Main.V5_0, listener.toList: _*)
  }

  private def insertUserListener(username: String, password: String): Listener =
    Listener
      .typedBuilder()
      .onStateReached[RunningMongodProcess](
        StateID.of(classOf[RunningMongodProcess]),
        { runningProcess =>
          val createUser = new Document("createUser", username)
            .append("pwd", password)
            .append("roles", java.util.Arrays.asList("userAdminAnyDatabase", "dbAdminAnyDatabase", "readWriteAnyDatabase"))

          val address = runningProcess.getServerAddress
          val client  = MongoClients.create(s"mongodb://${address.getHost}:${address.getPort}")
          try {
            val db = client.getDatabase("admin")
            db.runCommand(createUser)
            ()
          } finally client.close()
        }
      )
      .build()
}
