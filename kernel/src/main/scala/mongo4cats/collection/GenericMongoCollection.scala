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

import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.result._
import com.mongodb.reactivestreams.client.{MongoCollection => JMongoCollection}
import com.mongodb.{MongoNamespace, ReadConcern, ReadPreference, WriteConcern}
import mongo4cats.Clazz
import mongo4cats.bson.Document
import mongo4cats.client.ClientSession
import mongo4cats.codecs.MongoCodecProvider
import mongo4cats.collection.models._
import mongo4cats.operations.{Aggregate, Filter, Index, Update}
import mongo4cats.queries.{AggregateQueryBuilder, DistinctQueryBuilder, FindQueryBuilder, WatchQueryBuilder}
import org.bson.codecs.configuration.CodecRegistries.fromProviders
import org.bson.codecs.configuration.CodecRegistry
import org.bson.conversions.Bson

import scala.reflect.ClassTag
import scala.util.Try

abstract class GenericMongoCollection[F[_], T, S[_]] {
  def underlying: JMongoCollection[T]

  def namespace: MongoNamespace      = underlying.getNamespace
  def documentClass: Class[T]        = underlying.getDocumentClass
  def readPreference: ReadPreference = underlying.getReadPreference
  def readConcern: ReadConcern       = underlying.getReadConcern
  def codecs: CodecRegistry          = underlying.getCodecRegistry
  def writeConcern: WriteConcern     = underlying.getWriteConcern

  def withReadPreference(readPreference: ReadPreference): GenericMongoCollection[F, T, S]
  def withWriteConcern(writeConcert: WriteConcern): GenericMongoCollection[F, T, S]
  def withReadConcern(readConcern: ReadConcern): GenericMongoCollection[F, T, S]

  def as[Y: ClassTag]: GenericMongoCollection[F, Y, S]

  def withAddedCodec(codecRegistry: CodecRegistry): GenericMongoCollection[F, T, S]
  def withAddedCodec[Y](implicit classTag: ClassTag[Y], cp: MongoCodecProvider[Y]): GenericMongoCollection[F, T, S] =
    Try(codecs.get(Clazz.tag[Y])).fold(_ => withAddedCodec(fromProviders(cp.get)), _ => this)

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
  def aggregate[Y: ClassTag](pipeline: Seq[Bson]): AggregateQueryBuilder[F, Y, S]
  def aggregate[Y: ClassTag](pipeline: Aggregate): AggregateQueryBuilder[F, Y, S]
  def aggregate[Y: ClassTag](session: ClientSession[F], pipeline: Aggregate): AggregateQueryBuilder[F, Y, S]
  def aggregateWithCodec[Y: ClassTag: MongoCodecProvider](pipeline: Seq[Bson]): AggregateQueryBuilder[F, Y, S] =
    withAddedCodec[Y].aggregate[Y](pipeline)
  def aggregateWithCodec[Y: ClassTag: MongoCodecProvider](pipeline: Aggregate): AggregateQueryBuilder[F, Y, S] =
    withAddedCodec[Y].aggregate[Y](pipeline)
  def aggregateWithCodec[Y: ClassTag: MongoCodecProvider](session: ClientSession[F], pipeline: Aggregate): AggregateQueryBuilder[F, Y, S] =
    withAddedCodec[Y].aggregate[Y](session, pipeline)

  /** Creates a change stream for this collection.
    *
    * @param pipeline
    *   the aggregation pipeline to apply to the change stream
    * @since 2.2
    * @note
    *   Requires MongoDB 3.6 or greater
    */
  def watch(pipeline: Seq[Bson]): WatchQueryBuilder[F, Document, S]
  def watch(pipeline: Aggregate): WatchQueryBuilder[F, Document, S]
  def watch(session: ClientSession[F], pipeline: Aggregate): WatchQueryBuilder[F, Document, S]

  /** Creates a change stream for this collection.
    * @since 2.2
    * @note
    *   Requires MongoDB 3.6 or greater
    */
  def watch: WatchQueryBuilder[F, Document, S]                            = watch(Aggregate.empty)
  def watch(session: ClientSession[F]): WatchQueryBuilder[F, Document, S] = watch(session, Aggregate.empty)

  /** Gets the distinct values of the specified field name.
    *
    * [[http://docs.mongodb.org/manual/reference/command/distinct/]]
    * @param fieldName
    *   the field name
    * @param filter
    *   the query filter
    */
  def distinct[Y: ClassTag](fieldName: String, filter: Bson): DistinctQueryBuilder[F, Y, S]
  def distinct[Y: ClassTag](fieldName: String, filter: Filter): DistinctQueryBuilder[F, Y, S] = distinct(fieldName, filter.toBson)
  def distinct[Y: ClassTag](session: ClientSession[F], fieldName: String, filter: Filter): DistinctQueryBuilder[F, Y, S]

  def distinctWithCodec[Y: MongoCodecProvider: ClassTag](fieldName: String, filter: Bson): DistinctQueryBuilder[F, Y, S] =
    withAddedCodec[Y].distinct[Y](fieldName, filter)

  def distinctWithCodec[Y: MongoCodecProvider: ClassTag](fieldName: String, filter: Filter): DistinctQueryBuilder[F, Y, S] =
    distinctWithCodec(fieldName, filter.toBson)

  def distinctWithCodec[Y: MongoCodecProvider: ClassTag](
      session: ClientSession[F],
      fieldName: String,
      filter: Filter
  ): DistinctQueryBuilder[F, Y, S] =
    withAddedCodec[Y].distinct[Y](session, fieldName, filter)

  /** Gets the distinct values of the specified field name.
    *
    * [[http://docs.mongodb.org/manual/reference/command/distinct/Distinct]]
    * @param fieldName
    *   the field name
    */
  def distinct[Y: ClassTag](fieldName: String): DistinctQueryBuilder[F, Y, S] = distinct(fieldName, Filter.empty)
  def distinct[Y: ClassTag](session: ClientSession[F], fieldName: String): DistinctQueryBuilder[F, Y, S] =
    distinct(session, fieldName, Filter.empty)
  def distinctWithCodec[Y: MongoCodecProvider: ClassTag](fieldName: String): DistinctQueryBuilder[F, Y, S] =
    withAddedCodec[Y].distinct[Y](fieldName)
  def distinctWithCodec[Y: MongoCodecProvider: ClassTag](session: ClientSession[F], fieldName: String): DistinctQueryBuilder[F, Y, S] =
    withAddedCodec[Y].distinct[Y](session, fieldName)

  /** Finds matching documents in the collection.
    *
    * [[http://docs.mongodb.org/manual/tutorial/query-documents/]]
    * @param filter
    *   the query filter
    */
  def find(filter: Bson): FindQueryBuilder[F, T, S]
  def find(filter: Filter): FindQueryBuilder[F, T, S] = find(filter.toBson)
  def find(session: ClientSession[F], filter: Filter): FindQueryBuilder[F, T, S]

  /** Finds all documents in the collection.
    *
    * [[http://docs.mongodb.org/manual/tutorial/query-documents/]]
    */
  def find: FindQueryBuilder[F, T, S]                            = find(Filter.empty)
  def find(session: ClientSession[F]): FindQueryBuilder[F, T, S] = find(session, Filter.empty)

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

  /** Get all the indexes in this collection.
    *
    * [[http://docs.mongodb.org/manual/reference/command/listIndexes]]
    * @param session
    *   the client session with which to associate this operation
    */
  def listIndexes(session: ClientSession[F]): F[Iterable[Document]]
  def listIndexes[Y: ClassTag]: F[Iterable[Y]]
  def listIndexes: F[Iterable[Document]]
  def listIndexes[Y: ClassTag](cs: ClientSession[F]): F[Iterable[Y]]

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
    *   the options to apply to the delete operation \@since
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
    *   the options to apply to the delete operation \@since
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
    *   the options to apply to the operation \@since
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

  /** Executes a mix of inserts, updates, replaces, and deletes.
    *
    * [[https://docs.mongodb.com/manual/reference/method/db.collection.bulkWrite/]]
    * @param commands
    *   the writes to execute
    * @param options
    *   the options to apply to the bulk write operation
    */
  def bulkWrite[T1 <: T](commands: Seq[WriteCommand[T1]], options: BulkWriteOptions): F[BulkWriteResult]
  def bulkWrite[T1 <: T](commands: Seq[WriteCommand[T1]]): F[BulkWriteResult] = bulkWrite(commands, BulkWriteOptions())
  def bulkWrite[T1 <: T](cs: ClientSession[F], commands: Seq[WriteCommand[T1]], options: BulkWriteOptions): F[BulkWriteResult]
  def bulkWrite[T1 <: T](cs: ClientSession[F], commands: Seq[WriteCommand[T1]]): F[BulkWriteResult] =
    bulkWrite(cs, commands, BulkWriteOptions())

  /** Rename the collection with oldCollectionName to the newCollectionName.
    *
    * [[https://docs.mongodb.com/manual/reference/method/db.collection.renameCollection/]]
    * @param target
    *   the name the collection will be renamed to
    * @param options
    *   the options for renaming a collection
    */
  def renameCollection(target: MongoNamespace, options: RenameCollectionOptions): F[Unit]
  def renameCollection(target: MongoNamespace): F[Unit] = renameCollection(target, RenameCollectionOptions())

  def renameCollection(session: ClientSession[F], target: MongoNamespace, options: RenameCollectionOptions): F[Unit]
  def renameCollection(session: ClientSession[F], target: MongoNamespace): F[Unit] =
    renameCollection(session, target, RenameCollectionOptions())
}
