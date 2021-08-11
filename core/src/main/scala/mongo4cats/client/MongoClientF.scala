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
import mongo4cats.database.MongoDatabaseF
import com.mongodb.reactivestreams.client.{MongoClient, MongoClients}
import mongo4cats.bson.Document
import mongo4cats.helpers._

import scala.jdk.CollectionConverters._

trait MongoClientF[F[_]] {
  def getDatabase(name: String): F[MongoDatabaseF[F]]
  def listDatabaseNames: F[Iterable[String]]
  def listDatabases: F[Iterable[Document]]
}

final private class LiveMongoClientF[F[_]](
    private val client: MongoClient
)(implicit
    val F: Async[F]
) extends MongoClientF[F] {

  def getDatabase(name: String): F[MongoDatabaseF[F]] =
    F.delay(client.getDatabase(name)).flatMap(MongoDatabaseF.make[F])

  def listDatabaseNames: F[Iterable[String]] =
    client.listDatabaseNames().asyncIterable[F]

  def listDatabases: F[Iterable[Document]] =
    client.listDatabases().asyncIterable[F]
}

object MongoClientF {

  def fromConnectionString[F[_]: Async](connectionString: String): Resource[F, MongoClientF[F]] =
    clientResource[F](MongoClients.create(connectionString))

  def fromServerAddress[F[_]: Async](serverAddresses: ServerAddress*): Resource[F, MongoClientF[F]] =
    create {
      MongoClientSettings.builder
        .applyToClusterSettings { builder =>
          val _ = builder.hosts(serverAddresses.toList.asJava)
        }
        .build()
    }

  def create[F[_]: Async](settings: MongoClientSettings): Resource[F, MongoClientF[F]] =
    create(settings, null)

  def create[F[_]: Async](settings: MongoClientSettings, driver: MongoDriverInformation): Resource[F, MongoClientF[F]] =
    clientResource(MongoClients.create(settings, driver))

  private def clientResource[F[_]: Async](client: => MongoClient): Resource[F, MongoClientF[F]] =
    Resource.fromAutoCloseable(Sync[F].delay(client)).map(c => new LiveMongoClientF[F](c))
}
