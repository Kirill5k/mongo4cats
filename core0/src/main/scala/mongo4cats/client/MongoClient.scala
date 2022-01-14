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

import cats.effect.{Async, Resource, Sync}
import cats.implicits._
import fs2.Stream
import com.mongodb.connection.ClusterDescription
import com.mongodb.reactivestreams.client.{MongoClient => JMongoClient, MongoClients}
import mongo4cats.database.MongoDatabase
import mongo4cats.helpers._
import org.bson.Document

import scala.jdk.CollectionConverters._

trait MongoClient {
  def clusterDescription: ClusterDescription
  def getDatabase[F[_]: Async](name: String): F[MongoDatabase]
  def listDatabaseNames[F[_]: Async]: Stream[F, String]
  def listDatabases[F[_]: Async](
      session: ClientSession = ClientSession.void
  ): Stream[F, Document]
  def startSession[F[_]: Async](
      options: ClientSessionOptions = ClientSessionOptions.apply()
  ): F[ClientSession]
}

object MongoClient {
  def apply(client: JMongoClient): MongoClient = new MongoClient {
    def clusterDescription: ClusterDescription =
      client.getClusterDescription
    def getDatabase[F[_]: Async](name: String): F[MongoDatabase] =
      Async[F].delay(MongoDatabase(client.getDatabase(name)))
    def listDatabaseNames[F[_]: Async]: Stream[F, String] =
      client.listDatabaseNames().stream[F]
    def listDatabases[F[_]: Async](
        session: ClientSession = ClientSession.void
    ): Stream[F, Document] =
      client.listDatabases(session.session).stream[F]
    def startSession[F[_]: Async](
        options: ClientSessionOptions = ClientSessionOptions.apply()
    ): F[ClientSession] =
      client.startSession(options).asyncSingle[F].map(ClientSession(_))
  }

  def fromConnectionString[F[_]: Async](connectionString: String): Resource[F, MongoClient] =
    clientResource[F](MongoClients.create(connectionString))

  def fromServerAddress[F[_]: Async](
      serverAddresses: ServerAddress*
  ): Resource[F, MongoClient] =
    create {
      MongoClientSettings.builder
        .applyToClusterSettings { builder =>
          val _ = builder.hosts(serverAddresses.toList.asJava)
        }
        .build()
    }

  def create[F[_]: Async](settings: MongoClientSettings): Resource[F, MongoClient] =
    create(settings, null)

  def create[F[_]: Async](
      settings: MongoClientSettings,
      driver: MongoDriverInformation
  ): Resource[F, MongoClient] =
    clientResource[F](MongoClients.create(settings, driver))

  private def clientResource[F[_]: Sync](
      client: => JMongoClient
  ): Resource[F, MongoClient] =
    Resource.fromAutoCloseable(Sync[F].delay(client)).map(MongoClient(_))
}
