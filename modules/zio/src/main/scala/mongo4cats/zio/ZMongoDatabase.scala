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

import com.mongodb.{ReadConcern, ReadPreference, WriteConcern}
import com.mongodb.reactivestreams.client.MongoDatabase
import mongo4cats.Clazz
import mongo4cats.bson.Document
import mongo4cats.codecs.CodecRegistry
import mongo4cats.models.database.CreateCollectionOptions
import mongo4cats.zio.syntax._
import org.bson.conversions.Bson
import zio.{Task, UIO, ZIO}

import scala.reflect.ClassTag

final private class ZMongoDatabaseLive(
    val underlying: MongoDatabase
) extends ZMongoDatabase {
  def withReadPreference(readPreference: ReadPreference): ZMongoDatabase =
    new ZMongoDatabaseLive(underlying.withReadPreference(readPreference))

  def withWriteConcern(writeConcert: WriteConcern): ZMongoDatabase =
    new ZMongoDatabaseLive(underlying.withWriteConcern(writeConcert))

  def withReadConcern(readConcern: ReadConcern): ZMongoDatabase =
    new ZMongoDatabaseLive(underlying.withReadConcern(readConcern))

  def withAddedCodec(codecRegistry: CodecRegistry): ZMongoDatabase =
    new ZMongoDatabaseLive(underlying.withCodecRegistry(CodecRegistry.from(codecs, codecRegistry)))

  def listCollectionNames: Task[Iterable[String]] =
    underlying.listCollectionNames().asyncIterable
  def listCollectionNames(session: ZClientSession): Task[Iterable[String]] =
    underlying.listCollectionNames(session.underlying).asyncIterable

  def listCollections: Task[Iterable[Document]] =
    underlying.listCollections().asyncIterableF(Document.fromJava)
  def listCollections(session: ZClientSession): Task[Iterable[Document]] =
    underlying.listCollections(session.underlying).asyncIterableF(Document.fromJava)

  def createCollection(name: String, options: CreateCollectionOptions): Task[Unit] =
    underlying.createCollection(name, options).asyncVoid

  def getCollection[T: ClassTag](name: String, codecRegistry: CodecRegistry): Task[ZMongoCollection[T]] =
    ZIO
      .attempt {
        underlying
          .getCollection[T](name, Clazz.tag[T])
          .withCodecRegistry(codecRegistry)
          .withDocumentClass[T](Clazz.tag[T])
      }
      .flatMap(ZMongoCollection.make)

  def runCommand(command: Bson, readPreference: ReadPreference): Task[Document] =
    underlying.runCommand(command, readPreference).asyncSingle.unNone.map(Document.fromJava)
  def runCommand(session: ZClientSession, command: Bson, readPreference: ReadPreference): Task[Document] =
    underlying.runCommand(session.underlying, command, readPreference).asyncSingle.unNone.map(Document.fromJava)

  def drop: Task[Unit]                                = underlying.drop().asyncVoid
  def drop(clientSession: ZClientSession): Task[Unit] = underlying.drop(clientSession.underlying).asyncVoid
}

object ZMongoDatabase {
  private[zio] def make(database: MongoDatabase): UIO[ZMongoDatabase] =
    ZIO.succeed(new ZMongoDatabaseLive(database).withAddedCodec(CodecRegistry.Default))
}
