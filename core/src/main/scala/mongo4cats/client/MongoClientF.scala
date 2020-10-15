/*
 * Copyright 2020 Mongo DB client wrapper for Cats Effect & FS2
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

import cats.effect.{Concurrent, Resource, Sync}
import cats.implicits._
import mongo4cats.database.MongoDatabaseF
import org.mongodb.scala.{MongoClient, MongoClientSettings, ServerAddress}

import scala.jdk.CollectionConverters._

final class MongoClientF[F[_]: Concurrent] private (
    private val client: MongoClient
) {

  def getDatabase(name: String): F[MongoDatabaseF[F]] =
    Sync[F]
      .delay(client.getDatabase(name))
      .flatMap(MongoDatabaseF.make[F])
}

object MongoClientF {
  def fromServerAddress[F[_]: Concurrent](serverAddresses: ServerAddress*): Resource[F, MongoClientF[F]] = {
    val settings = MongoClientSettings
      .builder()
      .applyToClusterSettings { builder =>
        val _ = builder.hosts(serverAddresses.toList.asJava)
      }
      .build()
    clientResource(MongoClient(settings))
  }

  def fromConnectionString[F[_]: Concurrent](connectionString: String): Resource[F, MongoClientF[F]] =
    clientResource(MongoClient(connectionString))

  private def clientResource[F[_]: Concurrent](client: => MongoClient): Resource[F, MongoClientF[F]] =
    Resource.fromAutoCloseable(Sync[F].delay(client)).map(c => new MongoClientF[F](c))
}
