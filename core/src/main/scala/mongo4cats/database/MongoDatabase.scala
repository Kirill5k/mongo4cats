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

import cats.Monad
import cats.effect.Async
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.mongodb.reactivestreams.client.{MongoDatabase => JMongoDatabase}
import com.mongodb.{ReadConcern, ReadPreference, WriteConcern}
import mongo4cats.bson.Document
import mongo4cats.client.ClientSession
import mongo4cats.codecs.{CodecRegistry, MongoCodecProvider}
import mongo4cats.collection.MongoCollection
import mongo4cats.helpers._
import org.bson.conversions.Bson

import scala.reflect.ClassTag
import scala.util.Try

abstract class MongoDatabase[F[_]] {
  def name: String

  def readPreference: ReadPreference
  def withReadPreference(readPreference: ReadPreference): MongoDatabase[F]

  def writeConcern: WriteConcern
  def withWriteConcern(writeConcert: WriteConcern): MongoDatabase[F]

  def readConcern: ReadConcern
  def witReadConcern(readConcern: ReadConcern): MongoDatabase[F]

  def codecs: CodecRegistry
  def withAddedCodec(codecRegistry: CodecRegistry): MongoDatabase[F]
  def withAddedCodec[T: ClassTag](implicit cp: MongoCodecProvider[T]): MongoDatabase[F] =
    Try(codecs.get(clazz[T])).fold(_ => withAddedCodec(CodecRegistry.from(cp.get)), _ => this)

  def listCollectionNames: F[Iterable[String]]
  def listCollectionNames(session: ClientSession[F]): F[Iterable[String]]

  def listCollections: F[Iterable[Document]]
  def listCollections(session: ClientSession[F]): F[Iterable[Document]]

  def createCollection(name: String, options: CreateCollectionOptions): F[Unit]
  def createCollection(name: String): F[Unit] = createCollection(name, CreateCollectionOptions())

  def getCollection[T: ClassTag](name: String, codecRegistry: CodecRegistry): F[MongoCollection[F, T]]
  def getCollection(name: String): F[MongoCollection[F, Document]] = getCollection[Document](name, CodecRegistry.Default)
  def getCollectionWithCodec[T: ClassTag](name: String)(implicit cp: MongoCodecProvider[T]): F[MongoCollection[F, T]] =
    getCollection[T](name, CodecRegistry.mergeWithDefault(CodecRegistry.from(cp.get)))

  /** Executes command in the context of the current database.
    *
    * @param command
    *   the command to be run
    * @param readPreference
    *   the ReadPreference to be used when executing the command
    * @since 1.7
    */
  def runCommand(command: Bson, readPreference: ReadPreference): F[Document]
  def runCommand(command: Bson): F[Document]                            = runCommand(command, ReadPreference.primary)
  def runCommand(session: ClientSession[F], command: Bson): F[Document] = runCommand(session, command, ReadPreference.primary)
  def runCommand(session: ClientSession[F], command: Bson, readPreference: ReadPreference): F[Document]

  /** Drops this database. [[https://docs.mongodb.com/manual/reference/method/db.dropDatabase/]]
    */
  def drop: F[Unit]

  /** Drops this database.
    *
    * @param clientSession
    *   the client session with which to associate this operation [[https://docs.mongodb.com/manual/reference/method/db.dropDatabase/]]
    * @since 1.7
    */
  def drop(clientSession: ClientSession[F]): F[Unit]

  def underlying: JMongoDatabase
}

final private class LiveMongoDatabase[F[_]](
    val underlying: JMongoDatabase
)(implicit
    val F: Async[F]
) extends MongoDatabase[F] {

  def name: String = underlying.getName

  def readPreference: ReadPreference = underlying.getReadPreference
  def withReadPreference(readPreference: ReadPreference): MongoDatabase[F] =
    new LiveMongoDatabase[F](underlying.withReadPreference(readPreference))

  def writeConcern: WriteConcern = underlying.getWriteConcern
  def withWriteConcern(writeConcert: WriteConcern): MongoDatabase[F] =
    new LiveMongoDatabase[F](underlying.withWriteConcern(writeConcert))

  def readConcern: ReadConcern = underlying.getReadConcern
  def witReadConcern(readConcern: ReadConcern): MongoDatabase[F] =
    new LiveMongoDatabase[F](underlying.withReadConcern(readConcern))

  def codecs: CodecRegistry = underlying.getCodecRegistry
  def withAddedCodec(codecRegistry: CodecRegistry): MongoDatabase[F] =
    new LiveMongoDatabase[F](underlying.withCodecRegistry(CodecRegistry.from(codecs, codecRegistry)))

  def listCollectionNames: F[Iterable[String]]                       = underlying.listCollectionNames().asyncIterable[F]
  def listCollectionNames(cs: ClientSession[F]): F[Iterable[String]] = underlying.listCollectionNames(cs.underlying).asyncIterable[F]

  def listCollections: F[Iterable[Document]] = underlying.listCollections.asyncIterable[F].map(_.map(Document.fromNative))
  def listCollections(cs: ClientSession[F]): F[Iterable[Document]] =
    underlying.listCollections(cs.underlying).asyncIterable[F].map(_.map(Document.fromNative))

  def getCollection[T: ClassTag](name: String, codecRegistry: CodecRegistry): F[MongoCollection[F, T]] =
    F.delay {
      underlying
        .getCollection[T](name, clazz[T])
        .withCodecRegistry(codecRegistry)
        .withDocumentClass[T](clazz[T])
    }.flatMap(MongoCollection.make[F, T])

  def createCollection(name: String, options: CreateCollectionOptions): F[Unit] =
    underlying.createCollection(name, options).asyncVoid[F]

  def runCommand(cs: ClientSession[F], command: Bson, readPreference: ReadPreference): F[Document] =
    underlying.runCommand(cs.underlying, command, readPreference).asyncSingle[F].map(Document.fromNative)

  def runCommand(command: Bson, readPreference: ReadPreference): F[Document] =
    underlying.runCommand(command, readPreference).asyncSingle[F].map(Document.fromNative)

  def drop: F[Unit]                       = underlying.drop().asyncVoid[F]
  def drop(cs: ClientSession[F]): F[Unit] = underlying.drop(cs.underlying).asyncVoid[F]
}

object MongoDatabase {

  private[mongo4cats] def make[F[_]: Async](database: JMongoDatabase): F[MongoDatabase[F]] =
    Monad[F].pure(new LiveMongoDatabase[F](database).withAddedCodec(CodecRegistry.Default))
}
