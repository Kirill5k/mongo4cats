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

import cats.~>
import cats.arrow.FunctionK
import cats.effect.Async
import com.mongodb.reactivestreams.client.{MongoDatabase => JMongoDatabase}
import com.mongodb.{ReadConcern, ReadPreference, WriteConcern}
import fs2.Stream
import mongo4cats.client.ClientSession
import mongo4cats.collection.MongoCollection
import mongo4cats.helpers._
import org.bson.{BsonDocument, Document}
import org.bson.conversions.Bson

trait MongoDatabase[F[_]] {
  def name: String

  def readPreference: ReadPreference
  def withReadPreference(readPreference: ReadPreference): MongoDatabase[F]

  def writeConcern: WriteConcern
  def withWriteConcern(writeConcert: WriteConcern): MongoDatabase[F]

  def readConcern: ReadConcern
  def witReadConcern(readConcern: ReadConcern): MongoDatabase[F]

  def listCollectionNames(
      clientSession: Option[ClientSession[F]] = None
  ): Stream[F, String]

  def listCollections(clientSession: Option[ClientSession[F]] = None): Stream[F, Document]

  def createCollection(
      name: String,
      options: CreateCollectionOptions = CreateCollectionOptions()
  ): F[Unit]

  def getCollection(name: String): F[MongoCollection[F]]

  def runCommand(
      command: Bson,
      readPreference: ReadPreference = ReadPreference.primary,
      clientSession: Option[ClientSession[F]] = None
  ): F[Document]

  def drop(clientSession: Option[ClientSession[F]] = None): F[Unit]

  def mapK[G[_]](f: F ~> G): MongoDatabase[G]
  def asK[G[_]: Async]: MongoDatabase[G]
}

object MongoDatabase {
  def apply[F[_]: Async](database: JMongoDatabase): MongoDatabase[F] =
    TransformedMongoDatabase[F, F](database, FunctionK.id)

  final private case class TransformedMongoDatabase[F[_]: Async, G[_]](
      database: JMongoDatabase,
      transform: F ~> G
  ) extends MongoDatabase[G] {
    def name: String =
      database.getName

    def readPreference =
      database.getReadPreference
    def withReadPreference(readPreference: ReadPreference) =
      copy(database = database.withReadPreference(readPreference))

    def writeConcern =
      database.getWriteConcern
    def withWriteConcern(writeConcert: WriteConcern) =
      copy(database = database.withWriteConcern(writeConcern))

    def readConcern =
      database.getReadConcern
    def witReadConcern(readConcern: ReadConcern) =
      copy(database = database.withReadConcern(readConcern))

    def listCollectionNames(
        clientSession: Option[ClientSession[G]] = None
    ) = clientSession match {
      case Some(session) =>
        database.listCollectionNames(session.session).stream[F].translate(transform)
      case None =>
        database.listCollectionNames.stream[F].translate(transform)
    }

    def listCollections(
        clientSession: Option[ClientSession[G]] = None
    ) = clientSession match {
      case Some(session) =>
        database.listCollections(session.session).stream[F].translate(transform)
      case None =>
        database.listCollections.stream[F].translate(transform)
    }

    def createCollection(
        name: String,
        options: CreateCollectionOptions = CreateCollectionOptions()
    ) = transform {
      database.createCollection(name, options).asyncVoid[F]
    }

    def getCollection(name: String) = transform {
      Async[F].delay {
        MongoCollection[F](database.getCollection(name, classOf[BsonDocument]))
          .mapK(transform)
      }
    }

    def runCommand(
        command: Bson,
        readPreference: ReadPreference = ReadPreference.primary,
        clientSession: Option[ClientSession[G]] = None
    ) = transform {
      clientSession match {
        case Some(session) =>
          database.runCommand(session.session, command, readPreference).asyncSingle[F]
        case None =>
          database.runCommand(command, readPreference).asyncSingle[F]
      }
    }

    def drop(clientSession: Option[ClientSession[G]] = None) = transform {
      clientSession match {
        case Some(session) =>
          database.drop(session.session).asyncVoid[F]
        case None =>
          database.drop.asyncVoid[F]
      }
    }

    def mapK[H[_]](f: G ~> H) =
      copy(transform = transform andThen f)

    def asK[H[_]: Async] =
      TransformedMongoDatabase[H, H](database, FunctionK.id)
  }
}
