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
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.mongodb.reactivestreams.client.{MongoDatabase => JMongoDatabase}
import com.mongodb.{ReadConcern, ReadPreference, WriteConcern}
import mongo4cats.{AsJava, Clazz}
import mongo4cats.bson.Document
import mongo4cats.client.ClientSession
import mongo4cats.codecs.{CodecRegistry, MongoCodecProvider}
import mongo4cats.collection.{MongoCollection, Queries}
import mongo4cats.models.database.CreateCollectionOptions
import mongo4cats.operations.Aggregate
import mongo4cats.syntax._
import org.bson.conversions.Bson

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

final private class LiveMongoDatabase[F[_]](
    val underlying: JMongoDatabase
)(implicit
    val F: Async[F]
) extends MongoDatabase[F] with AsJava {
  def withTimeout(timeout: FiniteDuration): MongoDatabase[F] =
    new LiveMongoDatabase[F](underlying.withTimeout(timeout.length, timeout.unit))

  def withReadPreference(readPreference: ReadPreference): MongoDatabase[F] =
    new LiveMongoDatabase[F](underlying.withReadPreference(readPreference))

  def withWriteConcern(writeConcert: WriteConcern): MongoDatabase[F] =
    new LiveMongoDatabase[F](underlying.withWriteConcern(writeConcert))

  def withReadConcern(readConcern: ReadConcern): MongoDatabase[F] =
    new LiveMongoDatabase[F](underlying.withReadConcern(readConcern))

  def withAddedCodec(codecRegistry: CodecRegistry): MongoDatabase[F] =
    new LiveMongoDatabase[F](underlying.withCodecRegistry(CodecRegistry.from(codecs, codecRegistry)))

  def listCollectionNames: F[Iterable[String]] =
    F.defer(underlying.listCollectionNames().asyncIterable[F])
  def listCollectionNames(cs: ClientSession[F]): F[Iterable[String]] =
    F.defer(underlying.listCollectionNames(cs.underlying).asyncIterable[F])

  def listCollections: F[Iterable[Document]] =
    F.defer(underlying.listCollections().asyncIterableF[F, Document](Document.fromJava))
  def listCollections(cs: ClientSession[F]): F[Iterable[Document]] =
    F.defer(underlying.listCollections(cs.underlying).asyncIterableF[F, Document](Document.fromJava))

  def watch(pipeline: Seq[Bson]): Queries.Watch[F, Document] =
    Queries.watch(
      underlying
        .withCodecRegistry(CodecRegistry.withDocumentDecoder(underlying.getCodecRegistry))
        .watch(asJava(pipeline), Clazz.tag[Document])
    )

  def watch(pipeline: Aggregate): Queries.Watch[F, Document] =
    Queries.watch(
      underlying
        .withCodecRegistry(CodecRegistry.withDocumentDecoder(underlying.getCodecRegistry))
        .watch(pipeline.toBson, Clazz.tag[Document])
    )

  def watch(cs: ClientSession[F], pipeline: Seq[Bson]): Queries.Watch[F, Document] =
    Queries.watch(
      underlying
        .withCodecRegistry(CodecRegistry.withDocumentDecoder(underlying.getCodecRegistry))
        .watch(cs.underlying, asJava(pipeline), Clazz.tag[Document])
    )

  def watch(cs: ClientSession[F], pipeline: Aggregate): Queries.Watch[F, Document] =
    Queries.watch(
      underlying
        .withCodecRegistry(CodecRegistry.withDocumentDecoder(underlying.getCodecRegistry))
        .watch(cs.underlying, pipeline.toBson, Clazz.tag[Document])
    )

  override def getCollection(name: String): F[MongoCollection[F, Document]] =
    F.defer(super.getCollection(name))

  override def getCollectionWithCodec[T: ClassTag](name: String)(implicit cp: MongoCodecProvider[T]): F[MongoCollection[F, T]] =
    F.defer(super.getCollectionWithCodec[T](name))

  def getCollection[T: ClassTag](name: String, codecRegistry: CodecRegistry): F[MongoCollection[F, T]] =
    F.delay {
      underlying
        .getCollection[T](name, Clazz.tag[T])
        .withCodecRegistry(CodecRegistry.merge(codecRegistry, codecs))
        .withDocumentClass[T](Clazz.tag[T])
    }.flatMap(MongoCollection.make[F, T])

  def createCollection(name: String, options: CreateCollectionOptions): F[Unit] =
    F.defer(underlying.createCollection(name, options).asyncVoid[F])

  def createCollection(cs: ClientSession[F], name: String, options: CreateCollectionOptions): F[Unit] =
    F.defer(underlying.createCollection(cs.underlying, name, options).asyncVoid[F])

  def runCommand(cs: ClientSession[F], command: Bson, readPreference: ReadPreference): F[Document] =
    F.defer(underlying.runCommand(cs.underlying, command, readPreference).asyncSingle[F].unNone.map(Document.fromJava))

  def runCommand(command: Bson, readPreference: ReadPreference): F[Document] =
    F.defer(underlying.runCommand(command, readPreference).asyncSingle[F].unNone.map(Document.fromJava))

  def drop: F[Unit] =
    F.defer(underlying.drop().asyncVoid[F])
  def drop(cs: ClientSession[F]): F[Unit] =
    F.defer(underlying.drop(cs.underlying).asyncVoid[F])
}

object MongoDatabase {
  private[mongo4cats] def make[F[_]: Async](database: JMongoDatabase): F[MongoDatabase[F]] =
    Async[F].delay(new LiveMongoDatabase[F](database).withAddedCodec(CodecRegistry.Default))
}
