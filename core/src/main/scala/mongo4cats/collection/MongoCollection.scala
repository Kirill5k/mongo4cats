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
import com.mongodb.{MongoNamespace, ReadConcern, ReadPreference, WriteConcern}
import com.mongodb.client.result._
import mongo4cats.helpers._
import mongo4cats.collection.queries.{AggregateQueryBuilder, DistinctQueryBuilder, FindQueryBuilder, WatchQueryBuilder}
import org.bson.conversions.Bson
import com.mongodb.reactivestreams.client.{MongoCollection => JMongoCollection}
import mongo4cats.client.ClientSession
import mongo4cats.collection.operations.{Aggregate, Filter, Index, Update}
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.codecs.configuration.CodecRegistry

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

abstract class MongoCollection[F[_], T] {
  def namespace: MongoNamespace

  def readPreference: ReadPreference
  def withReadPreference(readPreference: ReadPreference): MongoCollection[F, T]

  def writeConcern: WriteConcern
  def withWriteConcern(writeConcert: WriteConcern): MongoCollection[F, T]

  def readConcern: ReadConcern
  def witReadConcern(readConcern: ReadConcern): MongoCollection[F, T]

  def documentClass: Class[T]
  def as[Y: ClassTag]: MongoCollection[F, Y]

  def codecs: CodecRegistry
  def withAddedCodec(codecRegistry: CodecRegistry): MongoCollection[F, T]
  def withAddedCodec[Y](implicit classTag: ClassTag[Y], cp: MongoCodecProvider[Y]): MongoCollection[F, T] = {
    val classY: Class[Y] = implicitly[ClassTag[Y]].runtimeClass.asInstanceOf[Class[Y]]
    Try(codecs.get(classY)) match {
      case Failure(_) => withAddedCodec(fromProviders(cp.get))
      case Success(_) => this
    }
  }

  /** Drops this collection from the Database.
    *
    * [[http://docs.mongodb.org/manual/reference/command/drop/]]
    */
  def drop: F[Unit]
  def drop(session: ClientSession[F]): F[Unit]

  /** Aggregates documents according to the specified aggregation pipeline. [[http://docs.mongodb.org/manual/aggregation/]]
    * @param pipeline
    *   the aggregate pipeline
    */
  def aggregate[Y: ClassTag](pipeline: Seq[Bson]): AggregateQueryBuilder[F, Y]
  def aggregate[Y: ClassTag](pipeline: Aggregate): AggregateQueryBuilder[F, Y]
  def aggregate[Y: ClassTag](session: ClientSession[F], pipeline: Aggregate): AggregateQueryBuilder[F, Y]
  def aggregateWithCodec[Y: ClassTag: MongoCodecProvider](pipeline: Seq[Bson]): AggregateQueryBuilder[F, Y] =
    withAddedCodec[Y].aggregate[Y](pipeline)
  def aggregateWithCodec[Y: ClassTag: MongoCodecProvider](pipeline: Aggregate): AggregateQueryBuilder[F, Y] =
    withAddedCodec[Y].aggregate[Y](pipeline)
  def aggregateWithCodec[Y: ClassTag: MongoCodecProvider](session: ClientSession[F], pipeline: Aggregate): AggregateQueryBuilder[F, Y] =
    withAddedCodec[Y].aggregate[Y](session, pipeline)

  /** Creates a change stream for this collection.
    *
    * @param pipeline
    *   the aggregation pipeline to apply to the change stream
    * @since 2.2
    * @note
    *   Requires MongoDB 3.6 or greater
    */
  def watch[Y: ClassTag](pipeline: Seq[Bson]): WatchQueryBuilder[F, Y]
  def watch[Y: ClassTag](pipeline: Aggregate): WatchQueryBuilder[F, Y]
  def watch[Y: ClassTag](session: ClientSession[F], pipeline: Aggregate): WatchQueryBuilder[F, Y]

  /** Creates a change stream for this collection.
    * @since 2.2
    * @note
    *   Requires MongoDB 3.6 or greater
    */
  def watch[Y: ClassTag]: WatchQueryBuilder[F, Y]                            = watch[Y](Aggregate.empty)
  def watch[Y: ClassTag](session: ClientSession[F]): WatchQueryBuilder[F, Y] = watch[Y](session, Aggregate.empty)

  /** Gets the distinct values of the specified field name.
    *
    * [[http://docs.mongodb.org/manual/reference/command/distinct/]]
    * @param fieldName
    *   the field name
    * @param filter
    *   the query filter
    */
  def distinct[Y: ClassTag](fieldName: String, filter: Bson): DistinctQueryBuilder[F, Y]
  def distinct[Y: ClassTag](fieldName: String, filter: Filter): DistinctQueryBuilder[F, Y] = distinct(fieldName, filter.toBson)
  def distinct[Y: ClassTag](session: ClientSession[F], fieldName: String, filter: Filter): DistinctQueryBuilder[F, Y]

  def distinctWithCodec[Y: MongoCodecProvider: ClassTag](fieldName: String, filter: Bson): DistinctQueryBuilder[F, Y] =
    withAddedCodec[Y].distinct[Y](fieldName, filter)

  def distinctWithCodec[Y: MongoCodecProvider: ClassTag](fieldName: String, filter: Filter): DistinctQueryBuilder[F, Y] =
    distinctWithCodec(fieldName, filter.toBson)

  def distinctWithCodec[Y: MongoCodecProvider: ClassTag](
      session: ClientSession[F],
      fieldName: String,
      filter: Filter
  ): DistinctQueryBuilder[F, Y] =
    withAddedCodec[Y].distinct[Y](session, fieldName, filter)

  /** Gets the distinct values of the specified field name.
    *
    * [[http://docs.mongodb.org/manual/reference/command/distinct/Distinct]]
    * @param fieldName
    *   the field name
    */
  def distinct[Y: ClassTag](fieldName: String): DistinctQueryBuilder[F, Y] = distinct(fieldName, Filter.empty)
  def distinct[Y: ClassTag](session: ClientSession[F], fieldName: String): DistinctQueryBuilder[F, Y] =
    distinct(session, fieldName, Filter.empty)
  def distinctWithCodec[Y: MongoCodecProvider: ClassTag](fieldName: String): DistinctQueryBuilder[F, Y] =
    withAddedCodec[Y].distinct[Y](fieldName)
  def distinctWithCodec[Y: MongoCodecProvider: ClassTag](session: ClientSession[F], fieldName: String): DistinctQueryBuilder[F, Y] =
    withAddedCodec[Y].distinct[Y](session, fieldName)

  /** Finds matching documents in the collection.
    *
    * [[http://docs.mongodb.org/manual/tutorial/query-documents/]]
    * @param filter
    *   the query filter
    */
  def find(filter: Bson): FindQueryBuilder[F, T]
  def find(filter: Filter): FindQueryBuilder[F, T] = find(filter.toBson)
  def find(session: ClientSession[F], filter: Filter): FindQueryBuilder[F, T]

  /** Finds all documents in the collection.
    *
    * [[http://docs.mongodb.org/manual/tutorial/query-documents/]]
    */
  def find: FindQueryBuilder[F, T]                            = find(Filter.empty)
  def find(session: ClientSession[F]): FindQueryBuilder[F, T] = find(session, Filter.empty)

  /** Atomically find a document and remove it.
    *
    * @param filter
    *   the query filter to find the document with
    * @param options
    *   the options to apply to the operation
    * @note
    *   If no documents matched the query filter, then None will be returned
    */
  def findOneAndDelete(filter: Bson, options: FindOneAndDeleteOptions): F[Option[T]]
  def findOneAndDelete(filter: Filter, options: FindOneAndDeleteOptions): F[Option[T]] = findOneAndDelete(filter.toBson, options)
  def findOneAndDelete(session: ClientSession[F], filter: Filter, options: FindOneAndDeleteOptions): F[Option[T]]
  def findOneAndDelete(filter: Bson): F[Option[T]]   = findOneAndDelete(filter, FindOneAndDeleteOptions())
  def findOneAndDelete(filter: Filter): F[Option[T]] = findOneAndDelete(filter, FindOneAndDeleteOptions())
  def findOneAndDelete(session: ClientSession[F], filter: Filter): F[Option[T]] =
    findOneAndDelete(session, filter, FindOneAndDeleteOptions())

  /** Atomically find a document and update it.
    *
    * @param filter
    *   a document describing the query filter. This can be of any type for which a `Codec` is registered
    * @param update
    *   a document describing the update. The update to apply must include only update operators. This can be of any type for which a
    *   `Codec` is registered
    * @param options
    *   the options to apply to the operation
    * @note
    *   Depending on the value of the `returnOriginal` property, this will either be the document as it was before the update or as it is
    *   after the update. If no documents matched the query filter, then None will be returned
    */
  def findOneAndUpdate(filter: Bson, update: Bson, options: FindOneAndUpdateOptions): F[Option[T]]
  def findOneAndUpdate(filter: Filter, update: Update, options: FindOneAndUpdateOptions): F[Option[T]] =
    findOneAndUpdate(filter.toBson, update.toBson, options)
  def findOneAndUpdate(session: ClientSession[F], filter: Filter, update: Update, options: FindOneAndUpdateOptions): F[Option[T]]
  def findOneAndUpdate(filter: Bson, update: Bson): F[Option[T]]     = findOneAndUpdate(filter, update, FindOneAndUpdateOptions())
  def findOneAndUpdate(filter: Filter, update: Update): F[Option[T]] = findOneAndUpdate(filter, update, FindOneAndUpdateOptions())
  def findOneAndUpdate(session: ClientSession[F], filter: Filter, update: Update): F[Option[T]] =
    findOneAndUpdate(session, filter, update, FindOneAndUpdateOptions())

  /** Atomically find a document and replace it.
    *
    * @param filter
    *   the query filter to apply the the replace operation
    * @param replacement
    *   the replacement document
    * @param options
    *   the options to apply to the operation
    * @note
    *   Depending on the value of the `returnOriginal` property, this will either be the document as it was before the update or as it is
    *   after the update. If no documents matched the query filter, then None will be returned
    */
  def findOneAndReplace(filter: Bson, replacement: T, options: FindOneAndReplaceOptions): F[Option[T]]
  def findOneAndReplace(filter: Filter, replacement: T, options: FindOneAndReplaceOptions): F[Option[T]] =
    findOneAndReplace(filter.toBson, replacement, options)
  def findOneAndReplace(session: ClientSession[F], filter: Filter, replacement: T, options: FindOneAndReplaceOptions): F[Option[T]]
  def findOneAndReplace(filter: Bson, replacement: T): F[Option[T]]   = findOneAndReplace(filter, replacement, FindOneAndReplaceOptions())
  def findOneAndReplace(filter: Filter, replacement: T): F[Option[T]] = findOneAndReplace(filter, replacement, FindOneAndReplaceOptions())
  def findOneAndReplace(session: ClientSession[F], filter: Filter, replacement: T): F[Option[T]] =
    findOneAndReplace(session, filter, replacement, FindOneAndReplaceOptions())

  /** Drops the given index.
    *
    * [[http://docs.mongodb.org/manual/reference/command/dropIndexes/]]
    * @param name
    *   the name of the index to remove
    * @param options
    *   options to use when dropping indexes
    */
  def dropIndex(name: String, options: DropIndexOptions): F[Unit]
  def dropIndex(name: String): F[Unit] = dropIndex(name, DropIndexOptions())
  def dropIndex(session: ClientSession[F], name: String, options: DropIndexOptions): F[Unit]
  def dropIndex(session: ClientSession[F], name: String): F[Unit] = dropIndex(session, name, DropIndexOptions())

  /** Drops the index given the keys used to create it.
    *
    * @param keys
    *   the keys of the index to remove
    * @param options
    *   options to use when dropping indexes
    * @since 2.2
    */
  def dropIndex(keys: Bson, options: DropIndexOptions): F[Unit]
  def dropIndex(index: Index, options: DropIndexOptions): F[Unit] = dropIndex(index.toBson, options)
  def dropIndex(session: ClientSession[F], index: Index, options: DropIndexOptions): F[Unit]
  def dropIndex(keys: Bson): F[Unit]                              = dropIndex(keys, DropIndexOptions())
  def dropIndex(index: Index): F[Unit]                            = dropIndex(index, DropIndexOptions())
  def dropIndex(session: ClientSession[F], index: Index): F[Unit] = dropIndex(session, index, DropIndexOptions())

  /** Drop all the indexes on this collection, except for the default on _id.
    *
    * [[http://docs.mongodb.org/manual/reference/command/dropIndexes/]]
    * @param options
    *   options to use when dropping indexes
    * @since 2.2
    */
  def dropIndexes(options: DropIndexOptions): F[Unit]
  def dropIndexes(session: ClientSession[F], options: DropIndexOptions): F[Unit]
  def dropIndexes: F[Unit]                            = dropIndexes(DropIndexOptions())
  def dropIndexes(session: ClientSession[F]): F[Unit] = dropIndexes(session, DropIndexOptions())

  /** [[http://docs.mongodb.org/manual/reference/command/create]]
    * @param key
    *   an object describing the index key(s). This can be of any type for which a `Codec` is registered
    * @param options
    *   the options for the index
    */
  def createIndex(key: Bson, options: IndexOptions): F[String]
  def createIndex(index: Index, options: IndexOptions): F[String] = createIndex(index.toBson, options)
  def createIndex(session: ClientSession[F], index: Index, options: IndexOptions): F[String]
  def createIndex(key: Bson): F[String]                               = createIndex(key, IndexOptions())
  def createIndex(index: Index): F[String]                            = createIndex(index, IndexOptions())
  def createIndex(session: ClientSession[F], index: Index): F[String] = createIndex(session, index, IndexOptions())

  /** Update all documents in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/]] [[http://docs.mongodb.org/manual/reference/operator/update/]]
    * @param filter
    *   a document describing the query filter. This can be of any type for which a `Codec` is registered
    * @param update
    *   a document describing the update. The update to apply must include only update operators. This can be of any type for which a
    *   `Codec` is registered
    * @param options
    *   the options to apply to the update operation
    */
  def updateMany(filter: Bson, update: Bson, options: UpdateOptions): F[UpdateResult]
  def updateMany(filter: Filter, update: Update, options: UpdateOptions): F[UpdateResult] =
    updateMany(filter.toBson, update.toBson, options)
  def updateMany(session: ClientSession[F], filter: Filter, update: Update, options: UpdateOptions): F[UpdateResult]
  def updateMany(filters: Bson, update: Bson): F[UpdateResult]     = updateMany(filters, update, UpdateOptions())
  def updateMany(filters: Filter, update: Update): F[UpdateResult] = updateMany(filters, update, UpdateOptions())
  def updateMany(session: ClientSession[F], filter: Filter, update: Update): F[UpdateResult] =
    updateMany(session, filter, update, UpdateOptions())

  /** Update all documents in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/]] [[http://docs.mongodb.org/manual/reference/operator/update/]]
    * @param filter
    *   a document describing the query filter. This can be of any type for which a `Codec` is registered
    * @param update
    *   a pipeline describing the update.
    * @param options
    *   the options to apply to the update operation
    * @since 2.7
    * @note
    *   Requires MongoDB 4.2 or greater
    */
  def updateMany(filter: Bson, update: Seq[Bson], options: UpdateOptions): F[UpdateResult]

  /** Update a single document in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/]] [[http://docs.mongodb.org/manual/reference/operator/update/]]
    * @param filter
    *   a document describing the query filter. This can be of any type for which a `Codec` is registered
    * @param update
    *   a document describing the update. The update to apply must include only update operators. This can be of any type for which a
    *   `Codec` is registered
    * @param options
    *   the options to apply to the update operation
    */
  def updateOne(filter: Bson, update: Bson, options: UpdateOptions): F[UpdateResult]
  def updateOne(filter: Filter, update: Update, options: UpdateOptions): F[UpdateResult] = updateOne(filter.toBson, update.toBson, options)
  def updateOne(session: ClientSession[F], filter: Filter, update: Update, options: UpdateOptions): F[UpdateResult]
  def updateOne(filters: Bson, update: Bson): F[UpdateResult]     = updateOne(filters, update, UpdateOptions())
  def updateOne(filters: Filter, update: Update): F[UpdateResult] = updateOne(filters, update, UpdateOptions())
  def updateOne(session: ClientSession[F], filters: Filter, update: Update): F[UpdateResult] =
    updateOne(session, filters, update, UpdateOptions())

  /** Update a single document in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/]] [[http://docs.mongodb.org/manual/reference/operator/update/]]
    * @param filter
    *   a document describing the query filter. This can be of any type for which a `Codec` is registered
    * @param update
    *   a pipeline describing the update.
    * @param options
    *   the options to apply to the update operation
    * @since 2.7
    * @note
    *   Requires MongoDB 4.2 or greater
    */
  def updateOne(filter: Bson, update: Seq[Bson], options: UpdateOptions): F[UpdateResult]
  def updateOne(filters: Bson, update: Seq[Bson]): F[UpdateResult] = updateOne(filters, update, UpdateOptions())

  /** Replace a document in the collection according to the specified arguments.
    *
    * [[http://docs.mongodb.org/manual/tutorial/modify-documents/#replace-the-document]]
    * @param filter
    *   the query filter to apply the the replace operation
    * @param replacement
    *   the replacement document
    * @param options
    *   the options to apply to the replace operation
    */
  def replaceOne(filter: Bson, replacement: T, options: ReplaceOptions): F[UpdateResult]
  def replaceOne(filter: Filter, replacement: T, options: ReplaceOptions): F[UpdateResult] = replaceOne(filter.toBson, replacement, options)
  def replaceOne(session: ClientSession[F], filter: Filter, replacement: T, options: ReplaceOptions): F[UpdateResult]
  def replaceOne(filters: Bson, replacement: T): F[UpdateResult]   = replaceOne(filters, replacement, ReplaceOptions())
  def replaceOne(filters: Filter, replacement: T): F[UpdateResult] = replaceOne(filters, replacement, ReplaceOptions())
  def replaceOne(session: ClientSession[F], filters: Filter, replacement: T): F[UpdateResult] =
    replaceOne(session, filters, replacement, ReplaceOptions())

  /** Removes at most one document from the collection that matches the given filter. If no documents match, the collection is not modified.
    *
    * @param filter
    *   the query filter to apply the the delete operation
    * @param options
    *   the options to apply to the delete operation
    * @since
    * 1.2
    */
  def deleteOne(filter: Bson, options: DeleteOptions): F[DeleteResult]
  def deleteOne(filter: Filter, options: DeleteOptions): F[DeleteResult] = deleteOne(filter.toBson, options)
  def deleteOne(session: ClientSession[F], filter: Filter, options: DeleteOptions): F[DeleteResult]
  def deleteOne(filters: Bson): F[DeleteResult]                             = deleteOne(filters, DeleteOptions())
  def deleteOne(filter: Filter): F[DeleteResult]                            = deleteOne(filter, DeleteOptions())
  def deleteOne(session: ClientSession[F], filter: Filter): F[DeleteResult] = deleteOne(session, filter, DeleteOptions())

  /** Removes all documents from the collection that match the given query filter. If no documents match, the collection is not modified.
    *
    * @param filter
    *   the query filter to apply the the delete operation
    * @param options
    *   the options to apply to the delete operation
    * @since
    * 1.2
    */
  def deleteMany(filter: Bson, options: DeleteOptions): F[DeleteResult]
  def deleteMany(filter: Filter, options: DeleteOptions): F[DeleteResult] = deleteMany(filter.toBson, options)
  def deleteMany(session: ClientSession[F], filter: Filter, options: DeleteOptions): F[DeleteResult]
  def deleteMany(filters: Bson): F[DeleteResult]                              = deleteMany(filters, DeleteOptions())
  def deleteMany(filters: Filter): F[DeleteResult]                            = deleteMany(filters, DeleteOptions())
  def deleteMany(session: ClientSession[F], filters: Filter): F[DeleteResult] = deleteMany(session, filters, DeleteOptions())

  /** Inserts the provided document. If the document is missing an identifier, the driver should generate one.
    *
    * @param document
    *   the document to insert
    * @param options
    *   the options to apply to the operation
    * @since
    * 1.1
    */
  def insertOne(document: T, options: InsertOneOptions): F[InsertOneResult]
  def insertOne(session: ClientSession[F], document: T, options: InsertOneOptions): F[InsertOneResult]
  def insertOne(document: T): F[InsertOneResult]                            = insertOne(document, InsertOneOptions())
  def insertOne(session: ClientSession[F], document: T): F[InsertOneResult] = insertOne(session, document, InsertOneOptions())

  /** Inserts a batch of documents. The preferred way to perform bulk inserts is to use the BulkWrite API. However, when talking with a
    * server &lt; 2.6, using this method will be faster due to constraints in the bulk API related to error handling.
    *
    * @param documents
    *   the documents to insert
    * @param options
    *   the options to apply to the operation
    */
  def insertMany(documents: Seq[T], options: InsertManyOptions): F[InsertManyResult]
  def insertMany(session: ClientSession[F], documents: Seq[T], options: InsertManyOptions): F[InsertManyResult]
  def insertMany(documents: Seq[T]): F[InsertManyResult]                            = insertMany(documents, InsertManyOptions())
  def insertMany(session: ClientSession[F], documents: Seq[T]): F[InsertManyResult] = insertMany(session, documents, InsertManyOptions())

  /** Counts the number of documents in the collection according to the given options.
    *
    * @param filter
    *   the query filter
    * @param options
    *   the options describing the count
    * @since 2.4
    */
  def count(filter: Bson, options: CountOptions): F[Long]
  def count(filter: Filter, options: CountOptions): F[Long] = count(filter.toBson, options)
  def count(session: ClientSession[F], filter: Filter, options: CountOptions): F[Long]
  def count(filter: Bson): F[Long]                              = count(filter, CountOptions())
  def count(filter: Filter): F[Long]                            = count(filter, CountOptions())
  def count(session: ClientSession[F], filter: Filter): F[Long] = count(session, filter, CountOptions())

  /** Counts the number of documents in the collection.
    *
    * @since 2.4
    */
  def count: F[Long]                            = count(Filter.empty, CountOptions())
  def count(session: ClientSession[F]): F[Long] = count(session, Filter.empty, CountOptions())
}

final private class LiveMongoCollection[F[_]: Async, T: ClassTag](
    private val collection: JMongoCollection[T]
) extends MongoCollection[F, T] {

  def readPreference: ReadPreference = collection.getReadPreference
  def withReadPreference(readPreference: ReadPreference): MongoCollection[F, T] =
    new LiveMongoCollection[F, T](collection.withReadPreference(readPreference))
  def writeConcern: WriteConcern = collection.getWriteConcern
  def withWriteConcern(writeConcert: WriteConcern): MongoCollection[F, T] =
    new LiveMongoCollection[F, T](collection.withWriteConcern(writeConcert))
  def readConcern: ReadConcern = collection.getReadConcern
  def witReadConcern(readConcern: ReadConcern): MongoCollection[F, T] =
    new LiveMongoCollection[F, T](collection.withReadConcern(readConcern))

  private def withNewDocumentClass[Y: ClassTag](coll: JMongoCollection[T]): JMongoCollection[Y] =
    coll.withDocumentClass[Y](clazz[Y])

  private def clazz[Y: ClassTag]: Class[Y] =
    implicitly[ClassTag[Y]].runtimeClass.asInstanceOf[Class[Y]]

  def codecs: CodecRegistry =
    collection.getCodecRegistry

  def namespace: MongoNamespace =
    collection.getNamespace

  def documentClass: Class[T] =
    collection.getDocumentClass

  def withAddedCodec(codecRegistry: CodecRegistry): MongoCollection[F, T] = {
    val newCodecs = fromRegistries(codecs, codecRegistry)
    new LiveMongoCollection[F, T](collection.withCodecRegistry(newCodecs))
  }

  def as[Y: ClassTag]: MongoCollection[F, Y] =
    new LiveMongoCollection[F, Y](withNewDocumentClass(collection))

  def aggregate[Y: ClassTag](pipeline: Seq[Bson]): AggregateQueryBuilder[F, Y] =
    AggregateQueryBuilder(withNewDocumentClass[Y](collection).aggregate(pipeline.asJava), Nil)

  def aggregate[Y: ClassTag](pipeline: Aggregate): AggregateQueryBuilder[F, Y] =
    AggregateQueryBuilder(withNewDocumentClass[Y](collection).aggregate(pipeline.toBson), Nil)

  def aggregate[Y: ClassTag](cs: ClientSession[F], pipeline: Aggregate): AggregateQueryBuilder[F, Y] =
    AggregateQueryBuilder(withNewDocumentClass[Y](collection).aggregate(cs.session, pipeline.toBson), Nil)

  def watch[Y: ClassTag](pipeline: Seq[Bson]): WatchQueryBuilder[F, Y] =
    WatchQueryBuilder[F, Y](collection.watch(pipeline.asJava, clazz[Y]), Nil)

  def watch[Y: ClassTag](pipeline: Aggregate): WatchQueryBuilder[F, Y] =
    WatchQueryBuilder[F, Y](collection.watch(pipeline.toBson, clazz[Y]), Nil)

  def watch[Y: ClassTag](cs: ClientSession[F], pipeline: Aggregate): WatchQueryBuilder[F, Y] =
    WatchQueryBuilder[F, Y](collection.watch(cs.session, pipeline.toBson, clazz[Y]), Nil)

  def distinct[Y: ClassTag](fieldName: String, filter: Bson): DistinctQueryBuilder[F, Y] =
    DistinctQueryBuilder[F, Y](collection.distinct(fieldName, filter, clazz[Y]), Nil)

  def distinct[Y: ClassTag](cs: ClientSession[F], fieldName: String, filter: Filter): DistinctQueryBuilder[F, Y] =
    DistinctQueryBuilder[F, Y](collection.distinct(cs.session, fieldName, filter.toBson, clazz[Y]), Nil)

  def find(filter: Bson): FindQueryBuilder[F, T] =
    FindQueryBuilder[F, T](collection.find(filter), Nil)

  def find(cs: ClientSession[F], filter: Filter): FindQueryBuilder[F, T] =
    FindQueryBuilder[F, T](collection.find(cs.session, filter.toBson), Nil)

  def findOneAndDelete(filter: Bson, options: FindOneAndDeleteOptions): F[Option[T]] =
    collection.findOneAndDelete(filter, options).asyncSingle[F].map(Option.apply[T])

  def findOneAndDelete(cs: ClientSession[F], filter: Filter, options: FindOneAndDeleteOptions): F[Option[T]] =
    collection.findOneAndDelete(cs.session, filter.toBson, options).asyncSingle[F].map(Option.apply[T])

  def findOneAndUpdate(filter: Bson, update: Bson, options: FindOneAndUpdateOptions): F[Option[T]] =
    collection.findOneAndUpdate(filter, update, options).asyncSingle[F].map(Option.apply[T])

  def findOneAndUpdate(cs: ClientSession[F], filter: Filter, update: Update, options: FindOneAndUpdateOptions): F[Option[T]] =
    collection.findOneAndUpdate(cs.session, filter.toBson, update.toBson, options).asyncSingle[F].map(Option.apply[T])

  def findOneAndReplace(filter: Bson, replacement: T, options: FindOneAndReplaceOptions): F[Option[T]] =
    collection.findOneAndReplace(filter, replacement, options).asyncSingle[F].map(Option.apply[T])

  def findOneAndReplace(cs: ClientSession[F], filter: Filter, replacement: T, options: FindOneAndReplaceOptions): F[Option[T]] =
    collection.findOneAndReplace(cs.session, filter.toBson, replacement, options).asyncSingle[F].map(Option.apply[T])

  def dropIndex(name: String, options: DropIndexOptions): F[Unit] = collection.dropIndex(name, options).asyncVoid[F]
  def dropIndex(cs: ClientSession[F], name: String, options: DropIndexOptions): F[Unit] =
    collection.dropIndex(cs.session, name, options).asyncVoid[F]
  def dropIndex(keys: Bson, options: DropIndexOptions): F[Unit] = collection.dropIndex(keys, options).asyncVoid[F]
  def dropIndex(cs: ClientSession[F], index: Index, options: DropIndexOptions): F[Unit] =
    collection.dropIndex(cs.session, index.toBson, options).asyncVoid[F]

  def dropIndexes(options: DropIndexOptions): F[Unit]                       = collection.dropIndexes(options).asyncVoid[F]
  def dropIndexes(cs: ClientSession[F], options: DropIndexOptions): F[Unit] = collection.dropIndexes(cs.session, options).asyncVoid[F]

  def drop: F[Unit]                       = collection.drop().asyncVoid[F]
  def drop(cs: ClientSession[F]): F[Unit] = collection.drop(cs.session).asyncVoid[F]

  def createIndex(key: Bson, options: IndexOptions): F[String] = collection.createIndex(key, options).asyncSingle[F]
  def createIndex(cs: ClientSession[F], index: Index, options: IndexOptions): F[String] =
    collection.createIndex(cs.session, index.toBson, options).asyncSingle[F]

  def updateMany(filter: Bson, update: Bson, options: UpdateOptions): F[UpdateResult] =
    collection.updateMany(filter, update, options).asyncSingle[F]

  def updateMany(filter: Bson, update: Seq[Bson], options: UpdateOptions): F[UpdateResult] =
    collection.updateMany(filter, update.asJava, options).asyncSingle[F]

  def updateMany(cs: ClientSession[F], filter: Filter, update: Update, options: UpdateOptions): F[UpdateResult] =
    collection.updateMany(cs.session, filter.toBson, update.toBson, options).asyncSingle[F]

  def updateOne(filter: Bson, update: Bson, options: UpdateOptions): F[UpdateResult] =
    collection.updateOne(filter, update, options).asyncSingle[F]

  def updateOne(filter: Bson, update: Seq[Bson], options: UpdateOptions): F[UpdateResult] =
    collection.updateOne(filter, update.asJava, options).asyncSingle[F]

  def updateOne(cs: ClientSession[F], filter: Filter, update: Update, options: UpdateOptions): F[UpdateResult] =
    collection.updateOne(cs.session, filter.toBson, update.toBson, options).asyncSingle[F]

  def replaceOne(filter: Bson, replacement: T, options: ReplaceOptions): F[UpdateResult] =
    collection.replaceOne(filter, replacement, options).asyncSingle[F]

  def replaceOne(cs: ClientSession[F], filter: Filter, replacement: T, options: ReplaceOptions): F[UpdateResult] =
    collection.replaceOne(cs.session, filter.toBson, replacement, options).asyncSingle[F]

  def deleteOne(filter: Bson, options: DeleteOptions): F[DeleteResult] = collection.deleteOne(filter, options).asyncSingle[F]
  def deleteOne(cs: ClientSession[F], filter: Filter, options: DeleteOptions): F[DeleteResult] =
    collection.deleteOne(cs.session, filter.toBson, options).asyncSingle[F]

  def deleteMany(filter: Bson, options: DeleteOptions): F[DeleteResult] = collection.deleteMany(filter, options).asyncSingle[F]
  def deleteMany(cs: ClientSession[F], filter: Filter, options: DeleteOptions): F[DeleteResult] =
    collection.deleteMany(cs.session, filter.toBson, options).asyncSingle[F]

  def insertOne(document: T, options: InsertOneOptions): F[InsertOneResult] = collection.insertOne(document, options).asyncSingle[F]
  def insertOne(cs: ClientSession[F], document: T, options: InsertOneOptions): F[InsertOneResult] =
    collection.insertOne(cs.session, document, options).asyncSingle[F]

  def insertMany(docs: Seq[T], options: InsertManyOptions): F[InsertManyResult] = collection.insertMany(docs.asJava, options).asyncSingle[F]
  def insertMany(cs: ClientSession[F], docs: Seq[T], options: InsertManyOptions): F[InsertManyResult] =
    collection.insertMany(cs.session, docs.asJava, options).asyncSingle[F]

  def count(filter: Bson, options: CountOptions): F[Long] = collection.countDocuments(filter, options).asyncSingle[F].map(_.longValue())
  def count(cs: ClientSession[F], filter: Filter, options: CountOptions): F[Long] =
    collection.countDocuments(cs.session, filter.toBson, options).asyncSingle[F].map(_.longValue())
}

object MongoCollection {

  private[mongo4cats] def make[F[_]: Async, T: ClassTag](collection: JMongoCollection[T]): F[MongoCollection[F, T]] =
    Monad[F].pure(new LiveMongoCollection(collection))

}
