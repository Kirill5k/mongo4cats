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
import mongo4cats.database.helpers._
import mongo4cats.database.queries.{AggregateQueryBuilder, DistinctQueryBuilder, FindQueryBuilder, WatchQueryBuilder}
import org.bson.conversions.Bson
import org.mongodb.scala.model._
import org.mongodb.scala.result._
import org.mongodb.scala.{MongoCollection, MongoNamespace}

import scala.reflect.ClassTag

final class MongoCollectionF[T: ClassTag] private (
    private val collection: MongoCollection[T]
) {

  def namespace: MongoNamespace =
    collection.namespace

  def documentClass: Class[T] =
    collection.documentClass

  /** Aggregates documents according to the specified aggregation pipeline.
    * [[http://docs.mongodb.org/manual/aggregation/ Aggregation]]
    * @param pipeline the aggregate pipeline
    */
  def aggregate(pipeline: Seq[Bson]): AggregateQueryBuilder[T] =
    AggregateQueryBuilder(collection.aggregate(pipeline), Nil)

  /** Creates a change stream for this collection.
    *
    * @param pipeline the aggregation pipeline to apply to the change stream
    * @since 2.2
    * @note Requires MongoDB 3.6 or greater
    */
  def watch(pipeline: Seq[Bson]): WatchQueryBuilder[T] =
    WatchQueryBuilder(collection.watch(pipeline), Nil)

  /** Creates a change stream for this collection.
    * @since 2.2
    * @note Requires MongoDB 3.6 or greater
    */
  def watch: WatchQueryBuilder[T] =
    WatchQueryBuilder(collection.watch(), Nil)

  /** Gets the distinct values of the specified field name.
    * [[http://docs.mongodb.org/manual/reference/command/distinct/ Distinct]]
    * @param fieldName the field name
    */
  def distinct(fieldName: String): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](collection.distinct(fieldName), Nil)

  /** Gets the distinct values of the specified field name.
    *
    * [[http://docs.mongodb.org/manual/reference/command/distinct/ Distinct]]
    * @param fieldName the field name
    * @param filter  the query filter
    */
  def distinct(fieldName: String, filter: Bson): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](collection.distinct(fieldName, filter), Nil)

  /** Finds all documents in the collection.
    *
    * [[http://docs.mongodb.org/manual/tutorial/query-documents/ Find]]
    */
  def find: FindQueryBuilder[T] =
    FindQueryBuilder[T](collection.find(), Nil)

  /** Finds all documents in the collection.
    *
    * [[http://docs.mongodb.org/manual/tutorial/query-documents/ Find]]
    * @param filter the query filter
    */
  def find(filter: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](collection.find(filter), Nil)

  /** Atomically find a document and remove it.
    *
    * @param filter  the query filter to find the document with
    * @note If no documents matched the query filter, then null will be returned
    */
  def findOneAndDelete[F[_]: Async](filter: Bson): F[T] =
    collection.findOneAndDelete(filter).asyncSingle[F]

  /** Atomically find a document and remove it.
    *
    * @param filter  the query filter to find the document with
    * @param options the options to apply to the operation
    * @note If no documents matched the query filter, then null will be returned
    */
  def findOneAndDelete[F[_]: Async](filter: Bson, options: FindOneAndDeleteOptions): F[T] =
    collection.findOneAndDelete(filter, options).asyncSingle[F]

  /** Atomically find a document and update it.
    *
    * @param filter  a document describing the query filter, which may not be null. This can be of any type for which a `Codec` is
    *                registered
    * @param update  a document describing the update, which may not be null. The update to apply must include only update operators. This
    *                can be of any type for which a `Codec` is registered
    * @note If no documents matched the query filter, then null will be returned
    */
  def findOneAndUpdate[F[_]: Async](filter: Bson, update: Bson): F[T] =
    collection.findOneAndUpdate(filter, update).asyncSingle[F]

  /** Atomically find a document and update it.
    *
    * @param filter  a document describing the query filter, which may not be null. This can be of any type for which a `Codec` is
    *                registered
    * @param update  a document describing the update, which may not be null. The update to apply must include only update operators. This
    *                can be of any type for which a `Codec` is registered
    * @param options the options to apply to the operation
    * @note Depending on the value of the `returnOriginal` property,
    *       this will either be the document as it was before the update or as it is after the update.  If no documents matched the
    *       query filter, then null will be returned
    */
  def findOneAndUpdate[F[_]: Async](filter: Bson, update: Bson, options: FindOneAndUpdateOptions): F[T] =
    collection.findOneAndUpdate(filter, update, options).asyncSingle[F]

  /** Atomically find a document and replace it.
    *
    * @param filter      the query filter to apply the the replace operation
    * @param replacement the replacement document
    * @note If no documents matched the query filter, then null will be returned
    */
  def findOneAndReplace[F[_]: Async](filter: Bson, replacement: T): F[T] =
    collection.findOneAndReplace(filter, replacement).asyncSingle[F]

  /** Atomically find a document and replace it.
    *
    * @param filter      the query filter to apply the the replace operation
    * @param replacement the replacement document
    * @param options     the options to apply to the operation
    * @note  Depending on the value of the `returnOriginal` property,
    *        this will either be the document as it was before the update or as it is after the update.
    *        If no documents matched the query filter, then null will be returned
    */
  def findOneAndReplace[F[_]: Async](filter: Bson, replacement: T, options: FindOneAndReplaceOptions): F[T] =
    collection.findOneAndReplace(filter, replacement, options).asyncSingle[F]

  /** Drops the given index.
    *
    * [[http://docs.mongodb.org/manual/reference/command/dropIndexes/ Drop Indexes]]
    * @param name the name of the index to remove
    */
  def dropIndex[F[_]: Async](name: String): F[Unit] =
    collection.dropIndex(name).void[F]

  /** Drops the index given the keys used to create it.
    *
    * @param keys the keys of the index to remove
    */
  def dropIndex[F[_]: Async](keys: Bson): F[Unit] =
    collection.dropIndex(keys).void[F]

  /** Drops the index given the keys used to create it.
    *
    * @param keys the keys of the index to remove
    * @param options options to use when dropping indexes
    * @since 2.2
    */
  def dropIndex[F[_]: Async](keys: Bson, options: DropIndexOptions): F[Unit] =
    collection.dropIndex(keys, options).void[F]

  /** Drop all the indexes on this collection, except for the default on _id.
    *
    * [[http://docs.mongodb.org/manual/reference/command/dropIndexes/ Drop Indexes]]
    * @param options options to use when dropping indexes
    * @since 2.2
    */
  def dropIndexes[F[_]: Async](options: DropIndexOptions): F[Unit] =
    collection.dropIndexes(options).void[F]

  /** Drop all the indexes on this collection, except for the default on _id.
    *
    * [[http://docs.mongodb.org/manual/reference/command/dropIndexes/ Drop Indexes]]
    */
  def dropIndexes[F[_]: Async](): F[Unit] =
    collection.dropIndexes().void[F]

  /** Drops this collection from the Database.
    *
    * [[http://docs.mongodb.org/manual/reference/command/drop/ Drop Collection]]
    */
  def drop[F[_]: Async](): F[Unit] =
    collection.drop().void[F]

  /** [[http://docs.mongodb.org/manual/reference/command/createIndexes Create Index]]
    * @param filters an object describing the index key(s), which may not be null. This can be of any type for which a `Codec` is registered
    */
  def createIndex[F[_]: Async](filters: Bson): F[String] =
    collection.createIndex(filters).asyncSingle[F]

  /** [[http://docs.mongodb.org/manual/reference/command/createIndexes Create Index]]
    * @param filter  an object describing the index key(s), which may not be null. This can be of any type for which a `Codec` is
    *                registered
    * @param options the options for the index
    */
  def createIndex[F[_]: Async](filter: Bson, options: IndexOptions): F[String] =
    collection.createIndex(filter, options).asyncSingle[F]

  /** Update all documents in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/ Updates]]
    * [[http://docs.mongodb.org/manual/reference/operator/update/ Update Operators]]
    * @param filters a document describing the query filter, which may not be null. This can be of any type for which a `Codec` is
    *                registered
    * @param update  a document describing the update, which may not be null. The update to apply must include only update operators. This
    *                can be of any type for which a `Codec` is registered
    */
  def updateMany[F[_]: Async](filters: Bson, update: Bson): F[UpdateResult] =
    collection.updateMany(filters, update).asyncSingle[F]

  /** Update all documents in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/ Updates]]
    * [[http://docs.mongodb.org/manual/reference/operator/update/ Update Operators]]
    * @param filters a document describing the query filter, which may not be null. This can be of any type for which a `Codec` is
    *                registered
    * @param update  a pipeline describing the update.
    * @since 2.7
    * @note Requires MongoDB 4.2 or greater
    */
  def updateMany[F[_]: Async](filters: Bson, update: Seq[Bson]): F[UpdateResult] =
    collection.updateMany(filters, update).asyncSingle[F]

  /** Update all documents in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/ Updates]]
    * [[http://docs.mongodb.org/manual/reference/operator/update/ Update Operators]]
    * @param filter  a document describing the query filter, which may not be null. This can be of any type for which a `Codec` is
    *                registered
    * @param update  a document describing the update, which may not be null. The update to apply must include only update operators. This
    *                can be of any type for which a `Codec` is registered
    * @param options the options to apply to the update operation
    */
  def updateMany[F[_]: Async](filter: Bson, update: Bson, options: UpdateOptions): F[UpdateResult] =
    collection.updateMany(filter, update, options).asyncSingle[F]

  /** Update all documents in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/ Updates]]
    * [[http://docs.mongodb.org/manual/reference/operator/update/ Update Operators]]
    * @param filter  a document describing the query filter, which may not be null. This can be of any type for which a `Codec` is
    *                registered
    * @param update  a pipeline describing the update.
    * @param options the options to apply to the update operation
    * @since 2.7
    * @note Requires MongoDB 4.2 or greater
    */
  def updateMany[F[_]: Async](filter: Bson, update: Seq[Bson], options: UpdateOptions): F[UpdateResult] =
    collection.updateMany(filter, update, options).asyncSingle[F]

  /** Update a single document in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/ Updates]]
    * [[http://docs.mongodb.org/manual/reference/operator/update/ Update Operators]]
    * @param filters a document describing the query filter, which may not be null. This can be of any type for which a `Codec` is
    *                registered
    * @param update  a document describing the update, which may not be null. The update to apply must include only update operators. This
    *                can be of any type for which a `Codec` is registered
    */
  def updateOne[F[_]: Async](filters: Bson, update: Bson): F[UpdateResult] =
    collection.updateOne(filters, update).asyncSingle[F]

  /** Update a single document in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/ Updates]]
    * [[http://docs.mongodb.org/manual/reference/operator/update/ Update Operators]]
    * @param filters a document describing the query filter, which may not be null. This can be of any type for which a `Codec` is
    *                registered
    * @param update  a pipeline describing the update
    * @since 2.7
    * @note Requires MongoDB 4.2 or greater
    */
  def updateOne[F[_]: Async](filters: Bson, update: Seq[Bson]): F[UpdateResult] =
    collection.updateOne(filters, update).asyncSingle[F]

  /** Update a single document in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/ Updates]]
    * [[http://docs.mongodb.org/manual/reference/operator/update/ Update Operators]]
    * @param filter  a document describing the query filter, which may not be null. This can be of any type for which a `Codec` is
    *                registered
    * @param update  a document describing the update, which may not be null. The update to apply must include only update operators. This
    *                can be of any type for which a `Codec` is registered
    * @param options the options to apply to the update operation
    */
  def updateOne[F[_]: Async](filter: Bson, update: Bson, options: UpdateOptions): F[UpdateResult] =
    collection.updateOne(filter, update, options).asyncSingle[F]

  /** Update a single document in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/ Updates]]
    * [[http://docs.mongodb.org/manual/reference/operator/update/ Update Operators]]
    * @param filter  a document describing the query filter, which may not be null. This can be of any type for which a `Codec` is
    *                registered
    * @param update  a pipeline describing the update.
    * @param options the options to apply to the update operation
    * @since 2.7
    * @note Requires MongoDB 4.2 or greater
    */
  def updateOne[F[_]: Async](filter: Bson, update: Seq[Bson], options: UpdateOptions): F[UpdateResult] =
    collection.updateOne(filter, update, options).asyncSingle[F]

  /** Replace a document in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/#replace-the-document Replace]]
    * @param filters     the query filter to apply the the replace operation
    * @param replacement the replacement document
    */
  def replaceOne[F[_]: Async](filters: Bson, replacement: T): F[UpdateResult] =
    collection.replaceOne(filters, replacement).asyncSingle[F]

  /** Replace a document in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/#replace-the-document Replace]]
    * @param filter      the query filter to apply the the replace operation
    * @param replacement the replacement document
    * @param options     the options to apply to the replace operation
    */
  def replaceOne[F[_]: Async](filter: Bson, replacement: T, options: ReplaceOptions): F[UpdateResult] =
    collection.replaceOne(filter, replacement, options).asyncSingle[F]

  /** Removes at most one document from the collection that matches the given filter.  If no documents match, the collection is not
    * modified.
    *
    * @param filters the query filter to apply the the delete operation
    */
  def deleteOne[F[_]: Async](filters: Bson): F[DeleteResult] =
    collection.deleteOne(filters).asyncSingle[F]

  /** Removes at most one document from the collection that matches the given filter.  If no documents match, the collection is not
    * modified.
    *
    * @param filter the query filter to apply the the delete operation
    * @param options the options to apply to the delete operation
    * @since 1.2
    */
  def deleteOne[F[_]: Async](filter: Bson, options: DeleteOptions): F[DeleteResult] =
    collection.deleteOne(filter, options).asyncSingle[F]

  /** Removes all documents from the collection that match the given query filter.  If no documents match, the collection is not modified.
    *
    * @param filters the query filter to apply the the delete operation
    */
  def deleteMany[F[_]: Async](filters: Bson): F[DeleteResult] =
    collection.deleteMany(filters).asyncSingle[F]

  /** Removes all documents from the collection that match the given query filter.  If no documents match, the collection is not modified.
    *
    * @param filter the query filter to apply the the delete operation
    * @param options the options to apply to the delete operation
    * @since 1.2
    */
  def deleteMany[F[_]: Async](filter: Bson, options: DeleteOptions): F[DeleteResult] =
    collection.deleteMany(filter, options).asyncSingle[F]

  /** Inserts the provided document. If the document is missing an identifier, the driver should generate one.
    *
    * @param document the document to insert
    */
  def insertOne[F[_]: Async](document: T): F[InsertOneResult] =
    collection.insertOne(document).asyncSingle[F]

  /** Inserts the provided document. If the document is missing an identifier, the driver should generate one.
    *
    * @param document the document to insert
    * @param options  the options to apply to the operation
    * @since 1.1
    */
  def insertOne[F[_]: Async](document: T, options: InsertOneOptions): F[InsertOneResult] =
    collection.insertOne(document, options).asyncSingle[F]

  /** Inserts a batch of documents. The preferred way to perform bulk inserts is to use the BulkWrite API. However, when talking with a
    * server &lt; 2.6, using this method will be faster due to constraints in the bulk API related to error handling.
    *
    * @param documents the documents to insert
    */
  def insertMany[F[_]: Async](documents: Seq[T]): F[InsertManyResult] =
    collection.insertMany(documents).asyncSingle[F]

  /** Inserts a batch of documents. The preferred way to perform bulk inserts is to use the BulkWrite API. However, when talking with a
    * server &lt; 2.6, using this method will be faster due to constraints in the bulk API related to error handling.
    *
    * @param documents the documents to insert
    * @param options   the options to apply to the operation
    */
  def insertMany[F[_]: Async](documents: Seq[T], options: InsertManyOptions): F[InsertManyResult] =
    collection.insertMany(documents, options).asyncSingle[F]

  /** Counts the number of documents in the collection.
    *
    * @since 2.4
    */
  def count[F[_]: Async]: F[Long] =
    collection.countDocuments().asyncSingle[F]

  /** Counts the number of documents in the collection according to the given options.
    *
    * @param filter the query filter
    * @since 2.4
    */
  def count[F[_]: Async](filter: Bson): F[Long] =
    collection.countDocuments(filter).asyncSingle[F]

  /** Counts the number of documents in the collection according to the given options.
    *
    * @param filter  the query filter
    * @param options the options describing the count
    * @since 2.4
    */
  def count[F[_]: Async](filter: Bson, options: CountOptions): F[Long] =
    collection.countDocuments(filter, options).asyncSingle[F]
}

object MongoCollectionF {

  def apply[T: ClassTag](collection: MongoCollection[T]): MongoCollectionF[T] =
    new MongoCollectionF(collection)

}
