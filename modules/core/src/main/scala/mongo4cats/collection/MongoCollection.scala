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

package mongo4cats.collection

import cats.Monad
import cats.effect.Async
import cats.syntax.functor._
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.result._
import com.mongodb.reactivestreams.client.{MongoCollection => JMongoCollection}
import com.mongodb.{ReadConcern, ReadPreference, WriteConcern}
import mongo4cats.bson.Document
import mongo4cats.client.ClientSession
import mongo4cats.codecs.CodecRegistry
import mongo4cats.models.collection._
import mongo4cats.syntax._
import mongo4cats.operations.{Aggregate, Filter, Index, Update}
import mongo4cats.{AsJava, Clazz}
import org.bson.conversions.Bson

import scala.reflect.ClassTag

final private class LiveMongoCollection[F[_]: Async, T: ClassTag](
    val underlying: JMongoCollection[T]
) extends MongoCollection[F, T] with AsJava {

  def withReadPreference(readPreference: ReadPreference): MongoCollection[F, T] =
    new LiveMongoCollection[F, T](underlying.withReadPreference(readPreference))

  def withWriteConcern(writeConcert: WriteConcern): MongoCollection[F, T] =
    new LiveMongoCollection[F, T](underlying.withWriteConcern(writeConcert))

  def withReadConcern(readConcern: ReadConcern): MongoCollection[F, T] =
    new LiveMongoCollection[F, T](underlying.withReadConcern(readConcern))

  def withAddedCodec(codecRegistry: CodecRegistry): MongoCollection[F, T] =
    new LiveMongoCollection[F, T](underlying.withCodecRegistry(CodecRegistry.from(codecs, codecRegistry)))

  def as[Y: ClassTag]: MongoCollection[F, Y] =
    new LiveMongoCollection[F, Y](withNewDocumentClass(underlying))

  def aggregate[Y: ClassTag](pipeline: Seq[Bson]): Queries.Aggregate[F, Y] =
    Queries.aggregate(withNewDocumentClass[Y](underlying).aggregate(asJava(pipeline)))

  def aggregate[Y: ClassTag](pipeline: Aggregate): Queries.Aggregate[F, Y] =
    Queries.aggregate(withNewDocumentClass[Y](underlying).aggregate(pipeline.toBson))

  def aggregate[Y: ClassTag](cs: ClientSession[F], pipeline: Aggregate): Queries.Aggregate[F, Y] =
    Queries.aggregate(withNewDocumentClass[Y](underlying).aggregate(cs.underlying, pipeline.toBson))

  def watch(pipeline: Seq[Bson]): Queries.Watch[F, Document] =
    Queries.watch(underlying.watch(asJava(pipeline), Clazz.tag[Document]))

  def watch(pipeline: Aggregate): Queries.Watch[F, Document] =
    Queries.watch(underlying.watch(pipeline.toBson, Clazz.tag[Document]))

  def watch(cs: ClientSession[F], pipeline: Aggregate): Queries.Watch[F, Document] =
    Queries.watch(underlying.watch(cs.underlying, pipeline.toBson, Clazz.tag[Document]))

  def distinct[Y: ClassTag](fieldName: String, filter: Bson): Queries.Distinct[F, Y] =
    Queries.distinct(underlying.distinct(fieldName, filter, Clazz.tag[Y]))

  def distinct[Y: ClassTag](cs: ClientSession[F], fieldName: String, filter: Filter): Queries.Distinct[F, Y] =
    Queries.distinct(underlying.distinct(cs.underlying, fieldName, filter.toBson, Clazz.tag[Y]))

  def find(filter: Bson): Queries.Find[F, T] =
    Queries.find(underlying.find(filter))

  def find(cs: ClientSession[F], filter: Filter): Queries.Find[F, T] =
    Queries.find(underlying.find(cs.underlying, filter.toBson))

  def findOneAndDelete(filter: Bson, options: FindOneAndDeleteOptions): F[Option[T]] =
    underlying.findOneAndDelete(filter, options).asyncSingle[F]

  def findOneAndDelete(cs: ClientSession[F], filter: Filter, options: FindOneAndDeleteOptions): F[Option[T]] =
    underlying.findOneAndDelete(cs.underlying, filter.toBson, options).asyncSingle[F]

  def findOneAndUpdate(filter: Bson, update: Bson, options: FindOneAndUpdateOptions): F[Option[T]] =
    underlying.findOneAndUpdate(filter, update, options).asyncSingle[F]

  def findOneAndUpdate(cs: ClientSession[F], filter: Filter, update: Update, options: FindOneAndUpdateOptions): F[Option[T]] =
    underlying.findOneAndUpdate(cs.underlying, filter.toBson, update.toBson, options).asyncSingle[F]

  def findOneAndReplace(filter: Bson, replacement: T, options: FindOneAndReplaceOptions): F[Option[T]] =
    underlying.findOneAndReplace(filter, replacement, options).asyncSingle[F]

  def findOneAndReplace(cs: ClientSession[F], filter: Filter, replacement: T, options: FindOneAndReplaceOptions): F[Option[T]] =
    underlying.findOneAndReplace(cs.underlying, filter.toBson, replacement, options).asyncSingle[F]

  def dropIndex(name: String, options: DropIndexOptions): F[Unit] = underlying.dropIndex(name, options).asyncVoid[F]
  def dropIndex(cs: ClientSession[F], name: String, options: DropIndexOptions): F[Unit] =
    underlying.dropIndex(cs.underlying, name, options).asyncVoid[F]
  def dropIndex(keys: Bson, options: DropIndexOptions): F[Unit] = underlying.dropIndex(keys, options).asyncVoid[F]
  def dropIndex(cs: ClientSession[F], index: Index, options: DropIndexOptions): F[Unit] =
    underlying.dropIndex(cs.underlying, index.toBson, options).asyncVoid[F]

  def dropIndexes(options: DropIndexOptions): F[Unit]                       = underlying.dropIndexes(options).asyncVoid[F]
  def dropIndexes(cs: ClientSession[F], options: DropIndexOptions): F[Unit] = underlying.dropIndexes(cs.underlying, options).asyncVoid[F]

  def drop: F[Unit]                       = underlying.drop().asyncVoid[F]
  def drop(cs: ClientSession[F]): F[Unit] = underlying.drop(cs.underlying).asyncVoid[F]

  def createIndex(key: Bson, options: IndexOptions): F[String] =
    underlying.createIndex(key, options).asyncSingle[F].unNone
  def createIndex(cs: ClientSession[F], index: Index, options: IndexOptions): F[String] =
    underlying.createIndex(cs.underlying, index.toBson, options).asyncSingle[F].unNone

  def listIndexes: F[Iterable[Document]] =
    underlying.listIndexes().asyncIterable[F].map(_.map(Document.fromJava))
  def listIndexes[Y: ClassTag]: F[Iterable[Y]] =
    underlying.listIndexes(Clazz.tag[Y]).asyncIterable[F]
  def listIndexes(cs: ClientSession[F]): F[Iterable[Document]] =
    underlying.listIndexes(cs.underlying).asyncIterable[F].map(_.map(Document.fromJava))
  def listIndexes[Y: ClassTag](cs: ClientSession[F]): F[Iterable[Y]] =
    underlying.listIndexes(cs.underlying, Clazz.tag[Y]).asyncIterable[F]

  def updateMany(filter: Bson, update: Bson, options: UpdateOptions): F[UpdateResult] =
    underlying.updateMany(filter, update, options).asyncSingle[F].unNone

  def updateMany(filter: Bson, update: Seq[Bson], options: UpdateOptions): F[UpdateResult] =
    underlying.updateMany(filter, asJava(update), options).asyncSingle[F].unNone

  def updateMany(cs: ClientSession[F], filter: Filter, update: Update, options: UpdateOptions): F[UpdateResult] =
    underlying.updateMany(cs.underlying, filter.toBson, update.toBson, options).asyncSingle[F].unNone

  def updateOne(filter: Bson, update: Bson, options: UpdateOptions): F[UpdateResult] =
    underlying.updateOne(filter, update, options).asyncSingle[F].unNone

  def updateOne(filter: Bson, update: Seq[Bson], options: UpdateOptions): F[UpdateResult] =
    underlying.updateOne(filter, asJava(update), options).asyncSingle[F].unNone

  def updateOne(cs: ClientSession[F], filter: Filter, update: Update, options: UpdateOptions): F[UpdateResult] =
    underlying.updateOne(cs.underlying, filter.toBson, update.toBson, options).asyncSingle[F].unNone

  def replaceOne(filter: Bson, replacement: T, options: ReplaceOptions): F[UpdateResult] =
    underlying.replaceOne(filter, replacement, options).asyncSingle[F].unNone

  def replaceOne(cs: ClientSession[F], filter: Filter, replacement: T, options: ReplaceOptions): F[UpdateResult] =
    underlying.replaceOne(cs.underlying, filter.toBson, replacement, options).asyncSingle[F].unNone

  def deleteOne(filter: Bson, options: DeleteOptions): F[DeleteResult] =
    underlying.deleteOne(filter, options).asyncSingle[F].unNone
  def deleteOne(cs: ClientSession[F], filter: Filter, options: DeleteOptions): F[DeleteResult] =
    underlying.deleteOne(cs.underlying, filter.toBson, options).asyncSingle[F].unNone

  def deleteMany(filter: Bson, options: DeleteOptions): F[DeleteResult] =
    underlying.deleteMany(filter, options).asyncSingle[F].unNone
  def deleteMany(cs: ClientSession[F], filter: Filter, options: DeleteOptions): F[DeleteResult] =
    underlying.deleteMany(cs.underlying, filter.toBson, options).asyncSingle[F].unNone

  def insertOne(document: T, options: InsertOneOptions): F[InsertOneResult] =
    underlying.insertOne(document, options).asyncSingle[F].unNone
  def insertOne(cs: ClientSession[F], document: T, options: InsertOneOptions): F[InsertOneResult] =
    underlying.insertOne(cs.underlying, document, options).asyncSingle[F].unNone

  def insertMany(docs: Seq[T], options: InsertManyOptions): F[InsertManyResult] =
    underlying.insertMany(asJava(docs), options).asyncSingle[F].unNone
  def insertMany(cs: ClientSession[F], docs: Seq[T], options: InsertManyOptions): F[InsertManyResult] =
    underlying.insertMany(cs.underlying, asJava(docs), options).asyncSingle[F].unNone

  def count(filter: Bson, options: CountOptions): F[Long] =
    underlying.countDocuments(filter, options).asyncSingle[F].unNone.map(_.longValue())
  def count(cs: ClientSession[F], filter: Filter, options: CountOptions): F[Long] =
    underlying.countDocuments(cs.underlying, filter.toBson, options).asyncSingle[F].unNone.map(_.longValue())

  def bulkWrite[T1 <: T](commands: Seq[WriteCommand[T1]], options: BulkWriteOptions): F[BulkWriteResult] =
    underlying.bulkWrite(asJava(commands.map(_.writeModel)), options).asyncSingle[F].unNone

  def bulkWrite[T1 <: T](cs: ClientSession[F], commands: Seq[WriteCommand[T1]], options: BulkWriteOptions): F[BulkWriteResult] =
    underlying.bulkWrite(cs.underlying, asJava(commands.map(_.writeModel)), options).asyncSingle[F].unNone

  def renameCollection(target: MongoNamespace, options: RenameCollectionOptions): F[Unit] =
    underlying.renameCollection(target.toJava, options).asyncVoid[F]

  def renameCollection(session: ClientSession[F], target: MongoNamespace, options: RenameCollectionOptions): F[Unit] =
    underlying.renameCollection(session.underlying, target.toJava, options).asyncVoid[F]
}

object MongoCollection {
  private[mongo4cats] def make[F[_]: Async, T: ClassTag](collection: JMongoCollection[T]): F[MongoCollection[F, T]] =
    Monad[F].pure(new LiveMongoCollection(collection))
}
