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

package mongo4cats.zio

import com.mongodb.reactivestreams.client.{ClientSession, MongoClient, MongoClients}
import mongo4cats.AsJava
import mongo4cats.bson.Document
import mongo4cats.models.client._
import mongo4cats.zio.syntax._
import zio.{Scope, Task, ZIO}

final private class ZClientSessionLive(
    val underlying: ClientSession
) extends ZClientSession {
  def startTransaction(options: TransactionOptions): Task[Unit] = ZIO.attempt(underlying.startTransaction(options)).unit
  def abortTransaction: Task[Unit]                              = ZIO.attempt(underlying.abortTransaction()).unit
  def commitTransaction: Task[Unit]                             = ZIO.attempt(underlying.commitTransaction()).unit
}

final private class ZMongoClientLive(
    val underlying: MongoClient
) extends ZMongoClient {
  def getDatabase(name: String): Task[ZMongoDatabase] =
    ZIO.attempt(underlying.getDatabase(name)).flatMap(ZMongoDatabase.make)

  def listDatabaseNames: Task[Iterable[String]] =
    underlying.listDatabaseNames().asyncIterable

  def listDatabases: Task[Iterable[Document]] =
    underlying.listDatabases().asyncIterableF(Document.fromJava)

  def listDatabases(session: ZClientSession): Task[Iterable[Document]] =
    underlying.listDatabases(session.underlying).asyncIterableF(Document.fromJava)

  def startSession(options: ClientSessionOptions): Task[ZClientSession] =
    underlying.startSession(options).asyncSingle.unNone.map(new ZClientSessionLive(_))
}

object ZMongoClient extends AsJava {
  def fromConnection(connection: MongoConnection): ZIO[Scope, Throwable, ZMongoClient] =
    fromConnectionString(connection.toString)

  def fromConnectionString(connectionString: String): ZIO[Scope, Throwable, ZMongoClient] =
    mkClient(MongoClients.create(connectionString))

  def fromServerAddress(serverAddresses: ServerAddress*): ZIO[Scope, Throwable, ZMongoClient] =
    create {
      MongoClientSettings.builder
        .applyToClusterSettings { builder =>
          val _ = builder.hosts(asJava(serverAddresses.toList))
        }
        .build()
    }

  def create(settings: MongoClientSettings): ZIO[Scope, Throwable, ZMongoClient] =
    create(settings, null)

  def create(settings: MongoClientSettings, driver: MongoDriverInformation): ZIO[Scope, Throwable, ZMongoClient] =
    mkClient(MongoClients.create(settings, driver))

  private def mkClient(client: => MongoClient): ZIO[Scope, Throwable, ZMongoClient] =
    ZIO.fromAutoCloseable(ZIO.attempt(client)).map(new ZMongoClientLive(_))
}
