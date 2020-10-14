/*
 * Copyright 2020 Mongo DB client wrapper for Cats Effect & Fs2
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
import mongo4cats.database.helpers._
import mongo4cats.database.queries.{AggregateQueryBuilder, DistinctQueryBuilder, FindQueryBuilder, WatchQueryBuilder}
import org.bson.conversions.Bson
import org.mongodb.scala.model._
import org.mongodb.scala.result._
import org.mongodb.scala.{MongoCollection, MongoNamespace, SingleObservable}

import scala.reflect.ClassTag

final class MongoCollectionF[T: ClassTag] private (
    private val collection: MongoCollection[T]
) {

  def namespace: MongoNamespace =
    collection.namespace

  def documentClass: Class[T] =
    collection.documentClass

  def aggregate(pipeline: Seq[Bson]): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(collection.aggregate(pipeline))

  def watch(pipeline: Seq[Bson]): WatchQueryBuilder[T] =
    WatchQueryBuilder(collection.watch(pipeline))

  def watch: WatchQueryBuilder[T] =
    WatchQueryBuilder(collection.watch())

  def distinct(fieldName: String): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](collection.distinct(fieldName))

  def distinct(fieldName: String, filter: Bson): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](collection.distinct(fieldName, filter))

  def find: FindQueryBuilder[T] =
    FindQueryBuilder[T](collection.find())

  def find(filter: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](collection.find(filter))

  def findOneAndDelete[F[_]: Async](filter: Bson): F[T] =
    async(collection.findOneAndDelete(filter))

  def findOneAndDelete[F[_]: Async](filter: Bson, options: FindOneAndDeleteOptions): F[T] =
    async(collection.findOneAndDelete(filter, options))

  def findOneAndUpdate[F[_]: Async](filter: Bson, update: Bson): F[T] =
    async(collection.findOneAndUpdate(filter, update))

  def findOneAndUpdate[F[_]: Async](filter: Bson, update: Bson, options: FindOneAndUpdateOptions): F[T] =
    async(collection.findOneAndUpdate(filter, update, options))

  def findOneAndReplace[F[_]: Async](filter: Bson, replacement: T): F[T] =
    async(collection.findOneAndReplace(filter, replacement))

  def findOneAndReplace[F[_]: Async](filter: Bson, replacement: T, options: FindOneAndReplaceOptions): F[T] =
    async(collection.findOneAndReplace(filter, replacement, options))

  def dropIndex[F[_]: Async](name: String): F[Unit] =
    asyncVoid(collection.dropIndex(name))

  def dropIndex[F[_]: Async](keys: Bson): F[Unit] =
    asyncVoid(collection.dropIndex(keys))

  def dropIndex[F[_]: Async](keys: Bson, options: DropIndexOptions): F[Unit] =
    asyncVoid(collection.dropIndex(keys, options))

  def dropIndexes[F[_]: Async](options: DropIndexOptions): F[Unit] =
    asyncVoid(collection.dropIndexes(options))

  def dropIndexes[F[_]: Async](): F[Unit] =
    asyncVoid(collection.dropIndexes())

  def drop[F[_]: Async](): F[Unit] =
    asyncVoid(collection.drop())

  def createIndex[F[_]: Async](filters: Bson): F[String] =
    async(collection.createIndex(filters))

  def createIndex[F[_]: Async](filter: Bson, options: IndexOptions): F[String] =
    async(collection.createIndex(filter, options))

  def updateMany[F[_]: Async](filters: Bson, update: Bson): F[UpdateResult] =
    async(collection.updateMany(filters, update))

  def updateMany[F[_]: Async](filters: Bson, update: Seq[Bson]): F[UpdateResult] =
    async(collection.updateMany(filters, update))

  def updateMany[F[_]: Async](filter: Bson, update: Bson, options: UpdateOptions): F[UpdateResult] =
    async(collection.updateMany(filter, update, options))

  def updateMany[F[_]: Async](filter: Bson, update: Seq[Bson], options: UpdateOptions): F[UpdateResult] =
    async(collection.updateMany(filter, update, options))

  def updateOne[F[_]: Async](filters: Bson, update: Bson): F[UpdateResult] =
    async(collection.updateOne(filters, update))

  def updateOne[F[_]: Async](filters: Bson, update: Seq[Bson]): F[UpdateResult] =
    async(collection.updateOne(filters, update))

  def updateOne[F[_]: Async](filter: Bson, update: Bson, options: UpdateOptions): F[UpdateResult] =
    async(collection.updateOne(filter, update, options))

  def updateOne[F[_]: Async](filter: Bson, update: Seq[Bson], options: UpdateOptions): F[UpdateResult] =
    async(collection.updateOne(filter, update, options))

  def replaceOne[F[_]: Async](filters: Bson, replacement: T): F[UpdateResult] =
    async(collection.replaceOne(filters, replacement))

  def replaceOne[F[_]: Async](filter: Bson, replacement: T, options: ReplaceOptions): F[UpdateResult] =
    async(collection.replaceOne(filter, replacement, options))

  def deleteOne[F[_]: Async](filters: Bson): F[DeleteResult] =
    async(collection.deleteOne(filters))

  def deleteOne[F[_]: Async](filter: Bson, options: DeleteOptions): F[DeleteResult] =
    async(collection.deleteOne(filter, options))

  def deleteMany[F[_]: Async](filters: Bson): F[DeleteResult] =
    async(collection.deleteMany(filters))

  def deleteMany[F[_]: Async](filter: Bson, options: DeleteOptions): F[DeleteResult] =
    async(collection.deleteMany(filter, options))

  def insertOne[F[_]: Async](document: T): F[InsertOneResult] =
    async(collection.insertOne(document))

  def insertOne[F[_]: Async](document: T, options: InsertOneOptions): F[InsertOneResult] =
    async(collection.insertOne(document, options))

  def insertMany[F[_]: Async](documents: Seq[T]): F[InsertManyResult] =
    async(collection.insertMany(documents))

  def insertMany[F[_]: Async](documents: Seq[T], options: InsertManyOptions): F[InsertManyResult] =
    async(collection.insertMany(documents, options))

  def count[F[_]: Async]: F[Long] =
    async(collection.countDocuments())

  def count[F[_]: Async](filter: Bson): F[Long] =
    async(collection.countDocuments(filter))

  def count[F[_]: Async](filter: Bson, options: CountOptions): F[Long] =
    async(collection.countDocuments(filter, options))

  private def async[F[_]: Async, R](observable: => SingleObservable[R]): F[R] =
    Async[F].async(singleItemAsync(observable))

  private def asyncVoid[F[_]: Async](observable: => SingleObservable[Void]): F[Unit] =
    Async[F].async(voidAsync(observable))
}

object MongoCollectionF {

  def apply[T: ClassTag](collection: MongoCollection[T]): MongoCollectionF[T] =
    new MongoCollectionF(collection)

}
