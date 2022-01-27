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
import cats.syntax.flatMap._
import com.mongodb.connection.ClusterDescription
import com.mongodb.reactivestreams.client.{MongoClient => JMongoClient, MongoClients}
import mongo4cats.bson.Document
import mongo4cats.database.MongoDatabase
import mongo4cats.helpers._

import scala.jdk.CollectionConverters._

abstract class MongoClient[F[_]] {
  def clusterDescription: ClusterDescription
  def getDatabase(name: String): F[MongoDatabase[F]]
  def listDatabaseNames: F[Iterable[String]]
  def listDatabases: F[Iterable[Document]]
  def listDatabases(session: ClientSession[F]): F[Iterable[Document]]
  def startSession(options: ClientSessionOptions): F[ClientSession[F]]
  def startSession: F[ClientSession[F]] = startSession(ClientSessionOptions.apply())
  def underlying: JMongoClient
}

final private class LiveMongoClient[F[_]](
    val underlying: JMongoClient
)(implicit
    val F: Async[F]
) extends MongoClient[F] {
  def clusterDescription: ClusterDescription = underlying.getClusterDescription

  def getDatabase(name: String): F[MongoDatabase[F]] =
    F.delay(underlying.getDatabase(name)).flatMap(MongoDatabase.make[F])

  def listDatabaseNames: F[Iterable[String]] =
    underlying.listDatabaseNames().asyncIterable[F]

  def listDatabases: F[Iterable[Document]] =
    underlying.listDatabases().asyncIterable[F]

  def listDatabases(cs: ClientSession[F]): F[Iterable[Document]] =
    underlying.listDatabases(cs.underlying).asyncIterable[F]

  def startSession(options: ClientSessionOptions): F[ClientSession[F]] =
    underlying.startSession(options).asyncSingle[F].flatMap(ClientSession.make[F])
}

object MongoClient {

  def fromConnectionString[F[_]: Async](connectionString: String): Resource[F, MongoClient[F]] =
    clientResource[F](MongoClients.create(connectionString))

  def fromServerAddress[F[_]: Async](serverAddresses: ServerAddress*): Resource[F, MongoClient[F]] =
    create {
      MongoClientSettings.builder
        .applyToClusterSettings { builder =>
          val _ = builder.hosts(serverAddresses.toList.asJava)
        }
        .build()
    }

  def create[F[_]: Async](settings: MongoClientSettings): Resource[F, MongoClient[F]] =
    create(settings, null)

  def create[F[_]: Async](settings: MongoClientSettings, driver: MongoDriverInformation): Resource[F, MongoClient[F]] =
    clientResource(MongoClients.create(settings, driver))

  private def clientResource[F[_]: Async](client: => JMongoClient): Resource[F, MongoClient[F]] =
    Resource.fromAutoCloseable(Sync[F].delay(client)).map(c => new LiveMongoClient[F](c))
}
