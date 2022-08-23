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

import com.mongodb.reactivestreams.client.{MongoDatabase => JMongoDatabase}
import com.mongodb.{ReadConcern, ReadPreference, WriteConcern}
import mongo4cats.Clazz
import mongo4cats.bson.Document
import mongo4cats.client.ClientSession
import mongo4cats.codecs.{CodecRegistry, MongoCodecProvider}
import mongo4cats.collection.GenericMongoCollection
import mongo4cats.database.models.CreateCollectionOptions
import org.bson.conversions.Bson

import scala.reflect.ClassTag
import scala.util.Try

abstract class GenericMongoDatabase[F[_], S[_]] {
  def underlying: JMongoDatabase

  def name: String                   = underlying.getName
  def readPreference: ReadPreference = underlying.getReadPreference
  def writeConcern: WriteConcern     = underlying.getWriteConcern
  def readConcern: ReadConcern       = underlying.getReadConcern
  def codecs: CodecRegistry          = underlying.getCodecRegistry

  def withReadPreference(readPreference: ReadPreference): GenericMongoDatabase[F, S]
  def withWriteConcern(writeConcert: WriteConcern): GenericMongoDatabase[F, S]
  def witReadConcern(readConcern: ReadConcern): GenericMongoDatabase[F, S]
  def withAddedCodec(codecRegistry: CodecRegistry): GenericMongoDatabase[F, S]
  def withAddedCodec[T: ClassTag](implicit cp: MongoCodecProvider[T]): GenericMongoDatabase[F, S] =
    Try(codecs.get(Clazz.tag[T])).fold(_ => withAddedCodec(CodecRegistry.from(cp.get)), _ => this)

  def listCollectionNames: F[Iterable[String]]
  def listCollectionNames(session: ClientSession[F]): F[Iterable[String]]

  def listCollections: F[Iterable[Document]]
  def listCollections(session: ClientSession[F]): F[Iterable[Document]]

  def createCollection(name: String, options: CreateCollectionOptions): F[Unit]
  def createCollection(name: String): F[Unit] = createCollection(name, CreateCollectionOptions())

  def getCollection[T: ClassTag](name: String, codecRegistry: CodecRegistry): F[GenericMongoCollection[F, T, S]]
  def getCollection(name: String): F[GenericMongoCollection[F, Document, S]] = getCollection[Document](name, CodecRegistry.Default)
  def getCollectionWithCodec[T: ClassTag](name: String)(implicit cp: MongoCodecProvider[T]): F[GenericMongoCollection[F, T, S]] =
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
}
