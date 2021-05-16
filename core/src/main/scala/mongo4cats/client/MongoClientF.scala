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
import mongo4cats.database.MongoDatabaseF
import com.mongodb.{MongoClientSettings, ServerAddress}
import com.mongodb.reactivestreams.client.{MongoClient, MongoClients}

import scala.jdk.CollectionConverters._

trait MongoClientF[F[_]] {
  def getDatabase(name: String): F[MongoDatabaseF[F]]
}

final private class LiveMongoClientF[F[_]](
    private val client: MongoClient
)(implicit
    val F: Async[F]
) extends MongoClientF[F] {

  def getDatabase(name: String): F[MongoDatabaseF[F]] =
    F.delay(client.getDatabase(name)).flatMap(MongoDatabaseF.make[F])
}

object MongoClientF {
  def fromServerAddress[F[_]: Async](serverAddresses: ServerAddress*): Resource[F, MongoClientF[F]] = {
    val settings = MongoClientSettings
      .builder()
      .applyToClusterSettings { builder =>
        val _ = builder.hosts(serverAddresses.toList.asJava)
      }
      .build()
    clientResource(MongoClients.create(settings))
  }

  def fromConnectionString[F[_]: Async](connectionString: String): Resource[F, MongoClientF[F]] =
    clientResource[F](MongoClients.create(connectionString))

  private def clientResource[F[_]: Async](client: => MongoClient): Resource[F, MongoClientF[F]] =
    Resource.fromAutoCloseable(Sync[F].delay(client)).map(c => new LiveMongoClientF[F](c))
}
