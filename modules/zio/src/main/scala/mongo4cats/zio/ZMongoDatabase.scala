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
import mongo4cats.{AsJava, Clazz}
import mongo4cats.bson.Document
import mongo4cats.codecs.{CodecRegistry, MongoCodecProvider}
import mongo4cats.models.database.CreateCollectionOptions
import mongo4cats.operations.Aggregate
import mongo4cats.zio.syntax._
import org.bson.conversions.Bson
import zio.{Task, ZIO}

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

final private class ZMongoDatabaseLive(
    val underlying: MongoDatabase
) extends ZMongoDatabase with AsJava {
  def withTimeout(timeout: FiniteDuration): ZMongoDatabase =
    new ZMongoDatabaseLive(underlying.withTimeout(timeout.length, timeout.unit))

  def withReadPreference(readPreference: ReadPreference): ZMongoDatabase =
    new ZMongoDatabaseLive(underlying.withReadPreference(readPreference))

  def withWriteConcern(writeConcert: WriteConcern): ZMongoDatabase =
    new ZMongoDatabaseLive(underlying.withWriteConcern(writeConcert))

  def withReadConcern(readConcern: ReadConcern): ZMongoDatabase =
    new ZMongoDatabaseLive(underlying.withReadConcern(readConcern))

  def withAddedCodec(codecRegistry: CodecRegistry): ZMongoDatabase =
    new ZMongoDatabaseLive(underlying.withCodecRegistry(CodecRegistry.from(codecs, codecRegistry)))

  def listCollectionNames: Task[Iterable[String]] =
    ZIO.attempt(underlying.listCollectionNames()).flatMap(_.asyncIterable)
  def listCollectionNames(session: ZClientSession): Task[Iterable[String]] =
    ZIO.attempt(underlying.listCollectionNames(session.underlying)).flatMap(_.asyncIterable)

  def listCollections: Task[Iterable[Document]] =
    ZIO.attempt(underlying.listCollections()).flatMap(_.asyncIterableF(Document.fromJava))
  def listCollections(session: ZClientSession): Task[Iterable[Document]] =
    ZIO.attempt(underlying.listCollections(session.underlying)).flatMap(_.asyncIterableF(Document.fromJava))

  def watch(pipeline: Seq[Bson]): Queries.Watch[Document] =
    Queries.watch(
      underlying
        .withCodecRegistry(CodecRegistry.withDocumentDecoder(underlying.getCodecRegistry))
        .watch(asJava(pipeline), Clazz.tag[Document])
    )

  def watch(pipeline: Aggregate): Queries.Watch[Document] =
    Queries.watch(
      underlying
        .withCodecRegistry(CodecRegistry.withDocumentDecoder(underlying.getCodecRegistry))
        .watch(pipeline.toBson, Clazz.tag[Document])
    )

  def watch(session: ZClientSession, pipeline: Seq[Bson]): Queries.Watch[Document] =
    Queries.watch(
      underlying
        .withCodecRegistry(CodecRegistry.withDocumentDecoder(underlying.getCodecRegistry))
        .watch(session.underlying, asJava(pipeline), Clazz.tag[Document])
    )

  def watch(session: ZClientSession, pipeline: Aggregate): Queries.Watch[Document] =
    Queries.watch(
      underlying
        .withCodecRegistry(CodecRegistry.withDocumentDecoder(underlying.getCodecRegistry))
        .watch(session.underlying, pipeline.toBson, Clazz.tag[Document])
    )

  def createCollection(name: String, options: CreateCollectionOptions): Task[Unit] =
    ZIO.attempt(underlying.createCollection(name, options)).flatMap(_.asyncVoid)

  def createCollection(session: ZClientSession, name: String, options: CreateCollectionOptions): Task[Unit] =
    ZIO.attempt(underlying.createCollection(session.underlying, name, options)).flatMap(_.asyncVoid)

  override def getCollection(name: String): Task[ZMongoCollection[Document]] =
    ZIO.attempt(super.getCollection(name)).flatten

  override def getCollectionWithCodec[T: ClassTag](name: String)(implicit cp: MongoCodecProvider[T]): Task[ZMongoCollection[T]] =
    ZIO.attempt(super.getCollectionWithCodec[T](name)).flatten

  def getCollection[T: ClassTag](name: String, codecRegistry: CodecRegistry): Task[ZMongoCollection[T]] =
    ZIO
      .attempt {
        underlying
          .getCollection[T](name, Clazz.tag[T])
          .withCodecRegistry(CodecRegistry.merge(codecRegistry, codecs))
          .withDocumentClass[T](Clazz.tag[T])
      }
      .flatMap(ZMongoCollection.make)

  def runCommand(command: Bson, readPreference: ReadPreference): Task[Document] =
    ZIO.attempt(underlying.runCommand(command, readPreference)).flatMap(_.asyncSingle.unNone).map(Document.fromJava)
  def runCommand(session: ZClientSession, command: Bson, readPreference: ReadPreference): Task[Document] =
    ZIO.attempt(underlying.runCommand(session.underlying, command, readPreference)).flatMap(_.asyncSingle.unNone).map(Document.fromJava)

  def drop: Task[Unit] =
    ZIO.attempt(underlying.drop()).flatMap(_.asyncVoid)
  def drop(clientSession: ZClientSession): Task[Unit] =
    ZIO.attempt(underlying.drop(clientSession.underlying)).flatMap(_.asyncVoid)
}

object ZMongoDatabase {
  private[zio] def make(database: MongoDatabase): Task[ZMongoDatabase] =
    ZIO.attempt(new ZMongoDatabaseLive(database).withAddedCodec(CodecRegistry.Default))
}
