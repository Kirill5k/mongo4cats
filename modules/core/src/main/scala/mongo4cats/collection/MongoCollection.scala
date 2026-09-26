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

  def watch(pipeline: Seq[Bson]): Queries.Watch[F, T] =
    Queries.watch(underlying.watch(asJava(pipeline), Clazz.tag[T]))

  def watch(pipeline: Aggregate): Queries.Watch[F, T] =
    Queries.watch(underlying.watch(pipeline.toBson, Clazz.tag[T]))

  def watch(cs: ClientSession[F], pipeline: Aggregate): Queries.Watch[F, T] =
    Queries.watch(underlying.watch(cs.underlying, pipeline.toBson, Clazz.tag[T]))

  def distinct[Y: ClassTag](fieldName: String, filter: Bson): Queries.Distinct[F, Y] =
    Queries.distinct(underlying.distinct(fieldName, filter, Clazz.tag[Y]))

  def distinct[Y: ClassTag](cs: ClientSession[F], fieldName: String, filter: Filter): Queries.Distinct[F, Y] =
    Queries.distinct(underlying.distinct(cs.underlying, fieldName, filter.toBson, Clazz.tag[Y]))

  def find(filter: Bson): Queries.Find[F, T] =
    Queries.find(underlying.find(filter))

  def find(cs: ClientSession[F], filter: Filter): Queries.Find[F, T] =
    Queries.find(underlying.find(cs.underlying, filter.toBson))

  def findOneAndDelete(filter: Bson, options: FindOneAndDeleteOptions): F[Option[T]] =
    Async[F].defer(underlying.findOneAndDelete(filter, options).asyncSingle[F])

  def findOneAndDelete(cs: ClientSession[F], filter: Filter, options: FindOneAndDeleteOptions): F[Option[T]] =
    Async[F].defer(underlying.findOneAndDelete(cs.underlying, filter.toBson, options).asyncSingle[F])

  def findOneAndUpdate(filter: Bson, update: Bson, options: FindOneAndUpdateOptions): F[Option[T]] =
    Async[F].defer(underlying.findOneAndUpdate(filter, update, options).asyncSingle[F])

  def findOneAndUpdate(cs: ClientSession[F], filter: Filter, update: Update, options: FindOneAndUpdateOptions): F[Option[T]] =
    Async[F].defer(underlying.findOneAndUpdate(cs.underlying, filter.toBson, update.toBson, options).asyncSingle[F])

  def findOneAndReplace(filter: Bson, replacement: T, options: FindOneAndReplaceOptions): F[Option[T]] =
    Async[F].defer(underlying.findOneAndReplace(filter, replacement, options).asyncSingle[F])

  def findOneAndReplace(cs: ClientSession[F], filter: Filter, replacement: T, options: FindOneAndReplaceOptions): F[Option[T]] =
    Async[F].defer(underlying.findOneAndReplace(cs.underlying, filter.toBson, replacement, options).asyncSingle[F])

  def dropIndex(name: String, options: DropIndexOptions): F[Unit] =
    Async[F].defer(underlying.dropIndex(name, options).asyncVoid[F])
  def dropIndex(cs: ClientSession[F], name: String, options: DropIndexOptions): F[Unit] =
    Async[F].defer(underlying.dropIndex(cs.underlying, name, options).asyncVoid[F])
  def dropIndex(keys: Bson, options: DropIndexOptions): F[Unit] =
    Async[F].defer(underlying.dropIndex(keys, options).asyncVoid[F])
  def dropIndex(cs: ClientSession[F], index: Index, options: DropIndexOptions): F[Unit] =
    Async[F].defer(underlying.dropIndex(cs.underlying, index.toBson, options).asyncVoid[F])

  def dropIndexes(options: DropIndexOptions): F[Unit] =
    Async[F].defer(underlying.dropIndexes(options).asyncVoid[F])
  def dropIndexes(cs: ClientSession[F], options: DropIndexOptions): F[Unit] =
    Async[F].defer(underlying.dropIndexes(cs.underlying, options).asyncVoid[F])

  def drop: F[Unit] =
    Async[F].defer(underlying.drop().asyncVoid[F])
  def drop(cs: ClientSession[F]): F[Unit] =
    Async[F].defer(underlying.drop(cs.underlying).asyncVoid[F])

  def createIndex(key: Bson, options: IndexOptions): F[String] =
    Async[F].defer(underlying.createIndex(key, options).asyncSingle[F].unNone)
  def createIndex(cs: ClientSession[F], index: Index, options: IndexOptions): F[String] =
    Async[F].defer(underlying.createIndex(cs.underlying, index.toBson, options).asyncSingle[F].unNone)

  def listIndexes: F[Iterable[Document]] =
    Async[F].defer(underlying.listIndexes().asyncIterableF[F, Document](Document.fromJava))
  def listIndexes[Y: ClassTag]: F[Iterable[Y]] =
    Async[F].defer(underlying.listIndexes(Clazz.tag[Y]).asyncIterable[F])
  def listIndexes(cs: ClientSession[F]): F[Iterable[Document]] =
    Async[F].defer(underlying.listIndexes(cs.underlying).asyncIterableF[F, Document](Document.fromJava))
  def listIndexes[Y: ClassTag](cs: ClientSession[F]): F[Iterable[Y]] =
    Async[F].defer(underlying.listIndexes(cs.underlying, Clazz.tag[Y]).asyncIterable[F])

  def createSearchIndex(definition: Bson): F[String] =
    Async[F].defer(underlying.createSearchIndex(definition).asyncSingle[F].unNone)

  def createSearchIndex(name: String, definition: Bson): F[String] =
    Async[F].defer(underlying.createSearchIndex(name, definition).asyncSingle[F].unNone)

  def createSearchIndexes(indexes: Seq[SearchIndexModel]): F[Iterable[String]] =
    Async[F].defer(underlying.createSearchIndexes(asJava(indexes.map(_.toJava))).asyncIterable[F])

  def listSearchIndexes: F[Iterable[Document]] =
    Async[F].defer(underlying.listSearchIndexes().asyncIterableF[F, Document](Document.fromJava))

  def listSearchIndexes(name: String): F[Iterable[Document]] =
    Async[F].defer(underlying.listSearchIndexes().name(name).asyncIterableF[F, Document](Document.fromJava))

  def listSearchIndexes[Y: ClassTag]: F[Iterable[Y]] =
    Async[F].defer(underlying.listSearchIndexes(Clazz.tag[Y]).asyncIterable[F])

  def listSearchIndexes[Y: ClassTag](name: String): F[Iterable[Y]] =
    Async[F].defer(underlying.listSearchIndexes(Clazz.tag[Y]).name(name).asyncIterable[F])

  def updateSearchIndex(name: String, definition: Bson): F[Unit] =
    Async[F].defer(underlying.updateSearchIndex(name, definition).asyncVoid[F])

  def dropSearchIndex(name: String): F[Unit] =
    Async[F].defer(underlying.dropSearchIndex(name).asyncVoid[F])

  def updateMany(filter: Bson, update: Bson, options: UpdateOptions): F[UpdateResult] =
    Async[F].defer(underlying.updateMany(filter, update, options).asyncSingle[F].unNone)

  def updateMany(filter: Bson, update: Seq[Bson], options: UpdateOptions): F[UpdateResult] =
    Async[F].defer(underlying.updateMany(filter, asJava(update), options).asyncSingle[F].unNone)

  def updateMany(cs: ClientSession[F], filter: Filter, update: Update, options: UpdateOptions): F[UpdateResult] =
    Async[F].defer(underlying.updateMany(cs.underlying, filter.toBson, update.toBson, options).asyncSingle[F].unNone)

  def updateOne(filter: Bson, update: Bson, options: UpdateOptions): F[UpdateResult] =
    Async[F].defer(underlying.updateOne(filter, update, options).asyncSingle[F].unNone)

  def updateOne(filter: Bson, update: Seq[Bson], options: UpdateOptions): F[UpdateResult] =
    Async[F].defer(underlying.updateOne(filter, asJava(update), options).asyncSingle[F].unNone)

  def updateOne(cs: ClientSession[F], filter: Filter, update: Update, options: UpdateOptions): F[UpdateResult] =
    Async[F].defer(underlying.updateOne(cs.underlying, filter.toBson, update.toBson, options).asyncSingle[F].unNone)

  def replaceOne(filter: Bson, replacement: T, options: ReplaceOptions): F[UpdateResult] =
    Async[F].defer(underlying.replaceOne(filter, replacement, options).asyncSingle[F].unNone)

  def replaceOne(cs: ClientSession[F], filter: Filter, replacement: T, options: ReplaceOptions): F[UpdateResult] =
    Async[F].defer(underlying.replaceOne(cs.underlying, filter.toBson, replacement, options).asyncSingle[F].unNone)

  def deleteOne(filter: Bson, options: DeleteOptions): F[DeleteResult] =
    Async[F].defer(underlying.deleteOne(filter, options).asyncSingle[F].unNone)
  def deleteOne(cs: ClientSession[F], filter: Filter, options: DeleteOptions): F[DeleteResult] =
    Async[F].defer(underlying.deleteOne(cs.underlying, filter.toBson, options).asyncSingle[F].unNone)

  def deleteMany(filter: Bson, options: DeleteOptions): F[DeleteResult] =
    Async[F].defer(underlying.deleteMany(filter, options).asyncSingle[F].unNone)
  def deleteMany(cs: ClientSession[F], filter: Filter, options: DeleteOptions): F[DeleteResult] =
    Async[F].defer(underlying.deleteMany(cs.underlying, filter.toBson, options).asyncSingle[F].unNone)

  def insertOne(document: T, options: InsertOneOptions): F[InsertOneResult] =
    Async[F].defer(underlying.insertOne(document, options).asyncSingle[F].unNone)
  def insertOne(cs: ClientSession[F], document: T, options: InsertOneOptions): F[InsertOneResult] =
    Async[F].defer(underlying.insertOne(cs.underlying, document, options).asyncSingle[F].unNone)

  def insertMany(docs: Seq[T], options: InsertManyOptions): F[InsertManyResult] =
    Async[F].defer(underlying.insertMany(asJava(docs), options).asyncSingle[F].unNone)
  def insertMany(cs: ClientSession[F], docs: Seq[T], options: InsertManyOptions): F[InsertManyResult] =
    Async[F].defer(underlying.insertMany(cs.underlying, asJava(docs), options).asyncSingle[F].unNone)

  def count(filter: Bson, options: CountOptions): F[Long] =
    Async[F].defer(underlying.countDocuments(filter, options).asyncSingle[F].unNone.map(_.longValue()))
  def count(cs: ClientSession[F], filter: Filter, options: CountOptions): F[Long] =
    Async[F].defer(underlying.countDocuments(cs.underlying, filter.toBson, options).asyncSingle[F].unNone.map(_.longValue()))

  def bulkWrite[T1 <: T](commands: Seq[WriteCommand[T1]], options: BulkWriteOptions): F[BulkWriteResult] =
    Async[F].defer(underlying.bulkWrite(asJava(commands.map(_.writeModel)), options).asyncSingle[F].unNone)

  def bulkWrite[T1 <: T](cs: ClientSession[F], commands: Seq[WriteCommand[T1]], options: BulkWriteOptions): F[BulkWriteResult] =
    Async[F].defer(underlying.bulkWrite(cs.underlying, asJava(commands.map(_.writeModel)), options).asyncSingle[F].unNone)

  def renameCollection(target: MongoNamespace, options: RenameCollectionOptions): F[Unit] =
    Async[F].defer(underlying.renameCollection(target.toJava, options).asyncVoid[F])

  def renameCollection(session: ClientSession[F], target: MongoNamespace, options: RenameCollectionOptions): F[Unit] =
    Async[F].defer(underlying.renameCollection(session.underlying, target.toJava, options).asyncVoid[F])
}

object MongoCollection {
  private[mongo4cats] def make[F[_]: Async, T: ClassTag](collection: JMongoCollection[T]): F[MongoCollection[F, T]] =
    Monad[F].pure(new LiveMongoCollection(collection))
}
