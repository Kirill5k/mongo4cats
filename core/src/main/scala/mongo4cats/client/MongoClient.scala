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

import cats.~>
import cats.arrow.FunctionK
import cats.effect.{Async, Resource}
import cats.implicits._
import fs2.Stream
import com.mongodb.connection.ClusterDescription
import com.mongodb.reactivestreams.client.{MongoClient => JMongoClient, MongoClients}
import mongo4cats.database.MongoDatabase
import mongo4cats.helpers._
import org.bson.Document

import scala.jdk.CollectionConverters._

trait MongoClient[F[_]] {
  def clusterDescription: ClusterDescription
  def getDatabase(name: String): F[MongoDatabase[F]]
  def listDatabaseNames: Stream[F, String]
  def listDatabases(
      clientSession: Option[ClientSession[F]] = None
  ): Stream[F, Document]
  def startSession(
      options: ClientSessionOptions = ClientSessionOptions.apply()
  ): F[ClientSession[F]]

  def mapK[G[_]](f: F ~> G): MongoClient[G]
  def asK[G[_]: Async]: MongoClient[G]
}

object MongoClient {
  final private case class TransformedMongoClient[F[_]: Async, G[_]](
      client: JMongoClient,
      transform: F ~> G
  ) extends MongoClient[G] {
    def clusterDescription =
      client.getClusterDescription

    def getDatabase(name: String) = transform {
      Async[F].delay {
        MongoDatabase[F](client.getDatabase(name)).mapK(transform)
      }
    }

    def listDatabaseNames =
      client.listDatabaseNames().stream[F].translate(transform)

    def listDatabases(clientSession: Option[ClientSession[G]] = None) = clientSession match {
      case Some(session) =>
        client.listDatabases(session.session).stream[F].translate(transform)
      case None =>
        client.listDatabases.stream[F].translate(transform)
    }

    def startSession(options: ClientSessionOptions = ClientSessionOptions.apply()) = transform {
      client.startSession(options).asyncSingle[F].map(ClientSession(_).mapK(transform))
    }

    def mapK[H[_]](f: G ~> H) =
      copy(transform = transform andThen f)

    def asK[H[_]: Async] =
      new TransformedMongoClient[H, H](client, FunctionK.id)
  }

  def fromConnectionString[F[_]: Async](connectionString: String): Resource[F, MongoClient[F]] =
    clientResource[F](MongoClients.create(connectionString))

  def fromServerAddress[F[_]: Async](
      serverAddresses: ServerAddress*
  ): Resource[F, MongoClient[F]] =
    create {
      MongoClientSettings.builder
        .applyToClusterSettings { builder =>
          val _ = builder.hosts(serverAddresses.toList.asJava)
        }
        .build()
    }

  def create[F[_]: Async](settings: MongoClientSettings): Resource[F, MongoClient[F]] =
    create(settings, null)

  def create[F[_]: Async](
      settings: MongoClientSettings,
      driver: MongoDriverInformation
  ): Resource[F, MongoClient[F]] =
    clientResource[F](MongoClients.create(settings, driver))

  private def clientResource[F[_]: Async](
      client: => JMongoClient
  ): Resource[F, MongoClient[F]] =
    Resource
      .fromAutoCloseable(Async[F].delay(client))
      .map(x => new TransformedMongoClient[F, F](x, FunctionK.id))
}
