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

package mongo4cats.database

import cats.effect.Async
import com.mongodb.reactivestreams.client.{MongoDatabase => JMongoDatabase}
import com.mongodb.{ReadConcern, ReadPreference, WriteConcern}
import fs2.Stream
import mongo4cats.client.ClientSession
import mongo4cats.collection.MongoCollection
import mongo4cats.helpers._
import org.bson.{BsonDocument, Document}
import org.bson.conversions.Bson

trait MongoDatabase {
  def name: String

  def readPreference: ReadPreference
  def withReadPreference(readPreference: ReadPreference): MongoDatabase

  def writeConcern: WriteConcern
  def withWriteConcern(writeConcert: WriteConcern): MongoDatabase

  def readConcern: ReadConcern
  def witReadConcern(readConcern: ReadConcern): MongoDatabase

  def listCollectionNames[F[_]: Async](
      session: ClientSession = ClientSession.void
  ): Stream[F, String]

  def listCollections[F[_]: Async](
      session: ClientSession = ClientSession.void
  ): Stream[F, Document]

  def createCollection[F[_]: Async](
      name: String,
      options: CreateCollectionOptions = CreateCollectionOptions()
  ): F[Unit]

  def getCollection[F[_]: Async](name: String): F[MongoCollection]

  def runCommand[F[_]: Async](
      command: Bson,
      readPreference: ReadPreference = ReadPreference.primary,
      session: ClientSession = ClientSession.void
  ): F[Document]

  def drop[F[_]: Async](clientSession: ClientSession = ClientSession.void): F[Unit]
}

object MongoDatabase {
  def apply(database: JMongoDatabase): MongoDatabase = new MongoDatabase {
    def name: String =
      database.getName

    def readPreference: ReadPreference =
      database.getReadPreference
    def withReadPreference(readPreference: ReadPreference): MongoDatabase =
      MongoDatabase(database.withReadPreference(readPreference))

    def writeConcern: WriteConcern =
      database.getWriteConcern
    def withWriteConcern(writeConcert: WriteConcern): MongoDatabase =
      MongoDatabase(database.withWriteConcern(writeConcern))

    def readConcern: ReadConcern =
      database.getReadConcern
    def witReadConcern(readConcern: ReadConcern): MongoDatabase =
      MongoDatabase(database.withReadConcern(readConcern))

    def listCollectionNames[F[_]: Async](
        session: ClientSession = ClientSession.void
    ): Stream[F, String] =
      database.listCollectionNames(session.session).stream[F]

    def listCollections[F[_]: Async](
        session: ClientSession = ClientSession.void
    ): Stream[F, Document] =
      database.listCollections(session.session).stream[F]

    def createCollection[F[_]: Async](
        name: String,
        options: CreateCollectionOptions = CreateCollectionOptions()
    ): F[Unit] =
      database.createCollection(name, options).asyncVoid[F]

    def getCollection[F[_]: Async](name: String): F[MongoCollection] =
      Async[F].delay(MongoCollection(database.getCollection(name, classOf[BsonDocument])))

    def runCommand[F[_]: Async](
        command: Bson,
        readPreference: ReadPreference = ReadPreference.primary,
        session: ClientSession = ClientSession.void
    ): F[Document] =
      database.runCommand(session.session, command, readPreference).asyncSingle[F]

    def drop[F[_]: Async](session: ClientSession = ClientSession.void): F[Unit] =
      database.drop(session.session).asyncVoid[F]
  }
}
