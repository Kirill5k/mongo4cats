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
import cats.syntax.functor._
import com.mongodb.reactivestreams.client.{ClientSession => JClientSession, MongoClient => JMongoClient, MongoClients}
import mongo4cats.AsJava
import mongo4cats.bson.Document
import mongo4cats.database.MongoDatabase
import mongo4cats.syntax._
import mongo4cats.models.client.{
  ClientSessionOptions,
  ConnectionString,
  MongoClientSettings,
  MongoConnection,
  MongoDriverInformation,
  ServerAddress,
  TransactionOptions
}
import org.bson.UuidRepresentation

import scala.util.Try

final private class LiveClientSession[F[_]](
    val underlying: JClientSession
)(implicit
    F: Async[F]
) extends ClientSession[F] {
  def startTransaction(options: TransactionOptions): F[Unit] = F.fromTry(Try(underlying.startTransaction(options)))
  def commitTransaction: F[Unit]                             = underlying.commitTransaction().asyncVoid[F]
  def abortTransaction: F[Unit]                              = underlying.abortTransaction().asyncVoid[F]
  def close: F[Unit]                                         = F.blocking(underlying.close())
}

final private class LiveMongoClient[F[_]](
    val underlying: JMongoClient
)(implicit
    F: Async[F]
) extends MongoClient[F] {
  def getDatabase(name: String): F[MongoDatabase[F]] =
    F.delay(underlying.getDatabase(name)).flatMap(MongoDatabase.make[F])

  def listDatabaseNames: F[Iterable[String]] =
    underlying.listDatabaseNames().asyncIterable[F]

  def listDatabases: F[Iterable[Document]] =
    underlying.listDatabases().asyncIterable[F].map(_.map(Document.fromJava))

  def listDatabases(cs: ClientSession[F]): F[Iterable[Document]] =
    underlying.listDatabases(cs.underlying).asyncIterable[F].map(_.map(Document.fromJava))

  def startSession(options: ClientSessionOptions): F[ClientSession[F]] =
    underlying.startSession(options).asyncSingle[F].unNone.map(new LiveClientSession(_))
}

object MongoClient extends AsJava {

  def fromConnection[F[_]: Async](connection: MongoConnection): Resource[F, MongoClient[F]] =
    fromConnectionString(connection.toString)

  def fromConnectionString[F[_]: Async](connectionString: String): Resource[F, MongoClient[F]] =
    clientResource[F](
      MongoClients.create(
        MongoClientSettings
          .builder()
          .uuidRepresentation(UuidRepresentation.STANDARD)
          .applyConnectionString(ConnectionString(connectionString))
          .build()
      )
    )

  def fromServerAddress[F[_]: Async](serverAddress: ServerAddress, serverAddresses: ServerAddress*): Resource[F, MongoClient[F]] =
    create {
      MongoClientSettings
        .builder()
        .uuidRepresentation(UuidRepresentation.STANDARD)
        .applyToClusterSettings { builder =>
          val _ = builder.hosts(asJava(serverAddress :: serverAddresses.toList))
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
