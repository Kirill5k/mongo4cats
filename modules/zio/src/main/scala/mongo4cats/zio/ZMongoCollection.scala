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
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.result.{DeleteResult, InsertManyResult, InsertOneResult, UpdateResult}
import com.mongodb.reactivestreams.client.MongoCollection
import mongo4cats.{AsJava, Clazz}
import mongo4cats.bson.Document
import mongo4cats.codecs.CodecRegistry
import mongo4cats.models.collection._
import mongo4cats.operations.{Aggregate, Filter, Index, Update}
import mongo4cats.zio.syntax._
import org.bson.conversions.Bson
import zio.{Task, UIO, ZIO}

import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

final private class ZMongoCollectionLive[T: ClassTag](
    val underlying: MongoCollection[T]
) extends ZMongoCollection[T] with AsJava {

  def withTimeout(timeout: FiniteDuration): ZMongoCollection[T] =
    new ZMongoCollectionLive(underlying.withTimeout(timeout.length, timeout.unit))

  def withReadPreference(rp: ReadPreference): ZMongoCollection[T]          = new ZMongoCollectionLive(underlying.withReadPreference(rp))
  def withWriteConcern(wc: WriteConcern): ZMongoCollection[T]              = new ZMongoCollectionLive(underlying.withWriteConcern(wc))
  def withReadConcern(rc: ReadConcern): ZMongoCollection[T]                = new ZMongoCollectionLive(underlying.withReadConcern(rc))
  def as[Y: ClassTag]: ZMongoCollection[Y]                                 = new ZMongoCollectionLive[Y](withNewDocumentClass(underlying))
  def withAddedCodec(newCodecRegistry: CodecRegistry): ZMongoCollection[T] =
    new ZMongoCollectionLive[T](underlying.withCodecRegistry(CodecRegistry.from(codecs, newCodecRegistry)))

  def drop: Task[Unit]                     = ZIO.attempt(underlying.drop()).flatMap(_.asyncVoid)
  def drop(cs: ZClientSession): Task[Unit] = ZIO.attempt(underlying.drop(cs.underlying)).flatMap(_.asyncVoid)

  def aggregate[Y: ClassTag](pipeline: Seq[Bson]): Queries.Aggregate[Y] =
    Queries.aggregate(underlying.aggregate(asJava(pipeline), Clazz.tag[Y]))
  def aggregate[Y: ClassTag](pipeline: Aggregate): Queries.Aggregate[Y] =
    Queries.aggregate(underlying.aggregate(pipeline.toBson, Clazz.tag[Y]))
  def aggregate[Y: ClassTag](cs: ZClientSession, pipeline: Seq[Bson]): Queries.Aggregate[Y] =
    Queries.aggregate(underlying.aggregate(cs.underlying, asJava(pipeline), Clazz.tag[Y]))

  def aggregate[Y: ClassTag](cs: ZClientSession, pipeline: Aggregate): Queries.Aggregate[Y] =
    Queries.aggregate(underlying.aggregate(cs.underlying, pipeline.toBson, Clazz.tag[Y]))

  def watch(pipeline: Seq[Bson]): Queries.Watch[T]                     = Queries.watch(underlying.watch(asJava(pipeline), Clazz.tag[T]))
  def watch(pipeline: Aggregate): Queries.Watch[T]                     = Queries.watch(underlying.watch(pipeline.toBson, Clazz.tag[T]))
  def watch(cs: ZClientSession, pipeline: Seq[Bson]): Queries.Watch[T] =
    Queries.watch(underlying.watch(cs.underlying, asJava(pipeline), Clazz.tag[T]))

  def watch(cs: ZClientSession, pipeline: Aggregate): Queries.Watch[T] =
    Queries.watch(underlying.watch(cs.underlying, pipeline.toBson, Clazz.tag[T]))

  def distinct[Y: ClassTag](fieldName: String, filter: Bson): Queries.Distinct[Y] =
    Queries.distinct(underlying.distinct(fieldName, filter, Clazz.tag[Y]))

  def distinct[Y: ClassTag](cs: ZClientSession, fieldName: String, filter: Bson): Queries.Distinct[Y] =
    Queries.distinct(underlying.distinct(cs.underlying, fieldName, filter, Clazz.tag[Y]))

  def distinct[Y: ClassTag](cs: ZClientSession, fieldName: String, filter: Filter): Queries.Distinct[Y] =
    Queries.distinct(underlying.distinct(cs.underlying, fieldName, filter.toBson, Clazz.tag[Y]))

  def find(filter: Bson): Queries.Find[T]                     = Queries.find(underlying.find(filter))
  def find(cs: ZClientSession, filter: Bson): Queries.Find[T] =
    Queries.find(underlying.find(cs.underlying, filter))

  def find(cs: ZClientSession, filter: Filter): Queries.Find[T] =
    Queries.find(underlying.find(cs.underlying, filter.toBson))

  def findOneAndDelete(cs: ZClientSession, filter: Bson, options: FindOneAndDeleteOptions): Task[Option[T]] =
    ZIO.attempt(underlying.findOneAndDelete(cs.underlying, filter, options)).flatMap(_.asyncSingle)

  def findOneAndDelete(filter: Bson, options: FindOneAndDeleteOptions): Task[Option[T]] =
    ZIO.attempt(underlying.findOneAndDelete(filter, options)).flatMap(_.asyncSingle)
  def findOneAndDelete(cs: ZClientSession, filter: Filter, options: FindOneAndDeleteOptions): Task[Option[T]] =
    ZIO.attempt(underlying.findOneAndDelete(cs.underlying, filter.toBson, options)).flatMap(_.asyncSingle)

  def findOneAndUpdate(cs: ZClientSession, filter: Bson, update: Bson, options: FindOneAndUpdateOptions): Task[Option[T]] =
    ZIO.attempt(underlying.findOneAndUpdate(cs.underlying, filter, update, options)).flatMap(_.asyncSingle)

  def findOneAndUpdate(filter: Bson, update: Seq[Bson], options: FindOneAndUpdateOptions): Task[Option[T]] =
    ZIO.attempt(underlying.findOneAndUpdate(filter, asJava(update), options)).flatMap(_.asyncSingle)

  def findOneAndUpdate(filter: Filter, update: Seq[Bson], options: FindOneAndUpdateOptions): Task[Option[T]] =
    ZIO.attempt(underlying.findOneAndUpdate(filter.toBson, asJava(update), options)).flatMap(_.asyncSingle)

  def findOneAndUpdate(cs: ZClientSession, filter: Bson, update: Seq[Bson], options: FindOneAndUpdateOptions): Task[Option[T]] =
    ZIO.attempt(underlying.findOneAndUpdate(cs.underlying, filter, asJava(update), options)).flatMap(_.asyncSingle)

  def findOneAndUpdate(cs: ZClientSession, filter: Filter, update: Seq[Bson], options: FindOneAndUpdateOptions): Task[Option[T]] =
    ZIO.attempt(underlying.findOneAndUpdate(cs.underlying, filter.toBson, asJava(update), options)).flatMap(_.asyncSingle)

  def findOneAndUpdate(filter: Bson, update: Bson, options: FindOneAndUpdateOptions): Task[Option[T]] =
    ZIO.attempt(underlying.findOneAndUpdate(filter, update, options)).flatMap(_.asyncSingle)

  def findOneAndUpdate(
      cs: ZClientSession,
      filter: Filter,
      update: Update,
      options: FindOneAndUpdateOptions
  ): Task[Option[T]] =
    ZIO.attempt(underlying.findOneAndUpdate(cs.underlying, filter.toBson, update.toBson, options)).flatMap(_.asyncSingle)

  def findOneAndReplace(cs: ZClientSession, filter: Bson, replacement: T, options: FindOneAndReplaceOptions): Task[Option[T]] =
    ZIO.attempt(underlying.findOneAndReplace(cs.underlying, filter, replacement, options)).flatMap(_.asyncSingle)

  def findOneAndReplace(filter: Bson, replacement: T, options: FindOneAndReplaceOptions): Task[Option[T]] =
    ZIO.attempt(underlying.findOneAndReplace(filter, replacement, options)).flatMap(_.asyncSingle)

  def findOneAndReplace(
      cs: ZClientSession,
      filter: Filter,
      replacement: T,
      options: FindOneAndReplaceOptions
  ): Task[Option[T]] =
    ZIO.attempt(underlying.findOneAndReplace(cs.underlying, filter.toBson, replacement, options)).flatMap(_.asyncSingle)

  def dropIndex(name: String, options: DropIndexOptions): Task[Unit] =
    ZIO.attempt(underlying.dropIndex(name, options)).flatMap(_.asyncVoid)

  def dropIndex(cs: ZClientSession, name: String, options: DropIndexOptions): Task[Unit] =
    ZIO.attempt(underlying.dropIndex(cs.underlying, name, options)).flatMap(_.asyncVoid)

  def dropIndex(cs: ZClientSession, keys: Bson, options: DropIndexOptions): Task[Unit] =
    ZIO.attempt(underlying.dropIndex(cs.underlying, keys, options)).flatMap(_.asyncVoid)

  def dropIndex(keys: Bson, options: DropIndexOptions): Task[Unit] =
    ZIO.attempt(underlying.dropIndex(keys, options)).flatMap(_.asyncVoid)

  def dropIndex(cs: ZClientSession, index: Index, options: DropIndexOptions): Task[Unit] =
    ZIO.attempt(underlying.dropIndex(cs.underlying, index.toBson, options)).flatMap(_.asyncVoid)

  def dropIndexes(options: DropIndexOptions): Task[Unit] =
    ZIO.attempt(underlying.dropIndexes(options)).flatMap(_.asyncVoid)

  def dropIndexes(cs: ZClientSession, options: DropIndexOptions): Task[Unit] =
    ZIO.attempt(underlying.dropIndexes(cs.underlying, options)).flatMap(_.asyncVoid)

  def createIndex(cs: ZClientSession, key: Bson, options: IndexOptions): Task[String] =
    ZIO.attempt(underlying.createIndex(cs.underlying, key, options)).flatMap(_.asyncSingle.unNone)

  def createIndex(key: Bson, options: IndexOptions): Task[String] =
    ZIO.attempt(underlying.createIndex(key, options)).flatMap(_.asyncSingle.unNone)

  def createIndex(cs: ZClientSession, index: Index, options: IndexOptions): Task[String] =
    ZIO.attempt(underlying.createIndex(cs.underlying, index.toBson, options)).flatMap(_.asyncSingle.unNone)

  def listIndexes(cs: ZClientSession): Task[Iterable[Document]] =
    ZIO.attempt(underlying.listIndexes(cs.underlying)).flatMap(_.asyncIterableF(Document.fromJava))

  def listIndexes[Y: ClassTag]: Task[Iterable[Y]] =
    ZIO.attempt(underlying.listIndexes(Clazz.tag[Y])).flatMap(_.asyncIterable)

  def listIndexes: Task[Iterable[Document]] =
    ZIO.attempt(underlying.listIndexes()).flatMap(_.asyncIterableF(Document.fromJava))

  def listIndexes[Y: ClassTag](cs: ZClientSession): Task[Iterable[Y]] =
    ZIO.attempt(underlying.listIndexes(cs.underlying, Clazz.tag[Y])).flatMap(_.asyncIterable)

  def createSearchIndex(definition: Bson): Task[String] =
    ZIO.attempt(underlying.createSearchIndex(definition)).flatMap(_.asyncSingle.unNone)

  def createSearchIndex(name: String, definition: Bson): Task[String] =
    ZIO.attempt(underlying.createSearchIndex(name, definition)).flatMap(_.asyncSingle.unNone)

  def createSearchIndexes(indexes: Seq[SearchIndexModel]): Task[Iterable[String]] =
    ZIO.attempt(underlying.createSearchIndexes(asJava(indexes.map(_.toJava)))).flatMap(_.asyncIterable)

  def listSearchIndexes: Task[Iterable[Document]] =
    ZIO.attempt(underlying.listSearchIndexes()).flatMap(_.asyncIterableF(Document.fromJava))

  def listSearchIndexes(name: String): Task[Iterable[Document]] =
    ZIO.attempt(underlying.listSearchIndexes().name(name)).flatMap(_.asyncIterableF(Document.fromJava))

  def listSearchIndexes[Y: ClassTag]: Task[Iterable[Y]] =
    ZIO.attempt(underlying.listSearchIndexes(Clazz.tag[Y])).flatMap(_.asyncIterable)

  def listSearchIndexes[Y: ClassTag](name: String): Task[Iterable[Y]] =
    ZIO.attempt(underlying.listSearchIndexes(Clazz.tag[Y]).name(name)).flatMap(_.asyncIterable)

  def updateSearchIndex(name: String, definition: Bson): Task[Unit] =
    ZIO.attempt(underlying.updateSearchIndex(name, definition)).flatMap(_.asyncVoid)

  def dropSearchIndex(name: String): Task[Unit] =
    ZIO.attempt(underlying.dropSearchIndex(name)).flatMap(_.asyncVoid)

  def updateMany(cs: ZClientSession, filter: Bson, update: Bson, options: UpdateOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.updateMany(cs.underlying, filter, update, options)).flatMap(_.asyncSingle.unNone)

  def updateMany(filter: Filter, update: Seq[Bson], options: UpdateOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.updateMany(filter.toBson, asJava(update), options)).flatMap(_.asyncSingle.unNone)

  def updateMany(cs: ZClientSession, filter: Bson, update: Seq[Bson], options: UpdateOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.updateMany(cs.underlying, filter, asJava(update), options)).flatMap(_.asyncSingle.unNone)

  def updateMany(cs: ZClientSession, filter: Filter, update: Seq[Bson], options: UpdateOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.updateMany(cs.underlying, filter.toBson, asJava(update), options)).flatMap(_.asyncSingle.unNone)

  def updateMany(filter: Bson, update: Bson, options: UpdateOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.updateMany(filter, update, options)).flatMap(_.asyncSingle.unNone)

  def updateMany(cs: ZClientSession, filter: Filter, update: Update, options: UpdateOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.updateMany(cs.underlying, filter.toBson, update.toBson, options)).flatMap(_.asyncSingle.unNone)

  def updateMany(filter: Bson, update: Seq[Bson], options: UpdateOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.updateMany(filter, asJava(update), options)).flatMap(_.asyncSingle.unNone)

  def updateOne(cs: ZClientSession, filter: Bson, update: Bson, options: UpdateOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.updateOne(cs.underlying, filter, update, options)).flatMap(_.asyncSingle.unNone)

  def updateOne(filter: Filter, update: Seq[Bson], options: UpdateOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.updateOne(filter.toBson, asJava(update), options)).flatMap(_.asyncSingle.unNone)

  def updateOne(cs: ZClientSession, filter: Bson, update: Seq[Bson], options: UpdateOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.updateOne(cs.underlying, filter, asJava(update), options)).flatMap(_.asyncSingle.unNone)

  def updateOne(cs: ZClientSession, filter: Filter, update: Seq[Bson], options: UpdateOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.updateOne(cs.underlying, filter.toBson, asJava(update), options)).flatMap(_.asyncSingle.unNone)

  def updateOne(filter: Bson, update: Bson, options: UpdateOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.updateOne(filter, update, options)).flatMap(_.asyncSingle.unNone)

  def updateOne(cs: ZClientSession, filter: Filter, update: Update, options: UpdateOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.updateOne(cs.underlying, filter.toBson, update.toBson, options)).flatMap(_.asyncSingle.unNone)

  def updateOne(filter: Bson, update: Seq[Bson], options: UpdateOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.updateOne(filter, asJava(update), options)).flatMap(_.asyncSingle.unNone)

  def replaceOne(cs: ZClientSession, filter: Bson, replacement: T, options: ReplaceOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.replaceOne(cs.underlying, filter, replacement, options)).flatMap(_.asyncSingle.unNone)

  def replaceOne(filter: Bson, replacement: T, options: ReplaceOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.replaceOne(filter, replacement, options)).flatMap(_.asyncSingle.unNone)

  def replaceOne(cs: ZClientSession, filter: Filter, replacement: T, options: ReplaceOptions): Task[UpdateResult] =
    ZIO.attempt(underlying.replaceOne(cs.underlying, filter.toBson, replacement, options)).flatMap(_.asyncSingle.unNone)

  def deleteOne(cs: ZClientSession, filter: Bson, options: DeleteOptions): Task[DeleteResult] =
    ZIO.attempt(underlying.deleteOne(cs.underlying, filter, options)).flatMap(_.asyncSingle.unNone)

  def deleteOne(filter: Bson, options: DeleteOptions): Task[DeleteResult] =
    ZIO.attempt(underlying.deleteOne(filter, options)).flatMap(_.asyncSingle.unNone)

  def deleteOne(cs: ZClientSession, filter: Filter, options: DeleteOptions): Task[DeleteResult] =
    ZIO.attempt(underlying.deleteOne(cs.underlying, filter.toBson, options)).flatMap(_.asyncSingle.unNone)

  def deleteMany(cs: ZClientSession, filter: Bson, options: DeleteOptions): Task[DeleteResult] =
    ZIO.attempt(underlying.deleteMany(cs.underlying, filter, options)).flatMap(_.asyncSingle.unNone)

  def deleteMany(filter: Bson, options: DeleteOptions): Task[DeleteResult] =
    ZIO.attempt(underlying.deleteMany(filter, options)).flatMap(_.asyncSingle.unNone)

  def deleteMany(cs: ZClientSession, filter: Filter, options: DeleteOptions): Task[DeleteResult] =
    ZIO.attempt(underlying.deleteMany(cs.underlying, filter.toBson, options)).flatMap(_.asyncSingle.unNone)

  def insertOne(document: T, options: InsertOneOptions): Task[InsertOneResult] =
    ZIO.attempt(underlying.insertOne(document, options)).flatMap(_.asyncSingle.unNone)

  def insertOne(cs: ZClientSession, document: T, options: InsertOneOptions): Task[InsertOneResult] =
    ZIO.attempt(underlying.insertOne(cs.underlying, document, options)).flatMap(_.asyncSingle.unNone)

  def insertMany(documents: Seq[T], options: InsertManyOptions): Task[InsertManyResult] =
    ZIO.attempt(underlying.insertMany(asJava(documents), options)).flatMap(_.asyncSingle.unNone)

  def insertMany(cs: ZClientSession, documents: Seq[T], options: InsertManyOptions): Task[InsertManyResult] =
    ZIO.attempt(underlying.insertMany(cs.underlying, asJava(documents), options)).flatMap(_.asyncSingle.unNone)

  def count(cs: ZClientSession, filter: Bson, options: CountOptions): Task[Long] =
    ZIO.attempt(underlying.countDocuments(cs.underlying, filter, options)).flatMap(_.asyncSingle.unNone).map(_.longValue())

  def count(filter: Bson, options: CountOptions): Task[Long] =
    ZIO.attempt(underlying.countDocuments(filter, options)).flatMap(_.asyncSingle.unNone).map(_.longValue())

  def count(cs: ZClientSession, filter: Filter, options: CountOptions): Task[Long] =
    ZIO.attempt(underlying.countDocuments(cs.underlying, filter.toBson, options)).flatMap(_.asyncSingle.unNone).map(_.longValue())

  def estimatedDocumentCount(options: EstimatedDocumentCountOptions): Task[Long] =
    ZIO.attempt(underlying.estimatedDocumentCount(options)).flatMap(_.asyncSingle.unNone).map(_.longValue())

  def bulkWrite[T1 <: T](commands: Seq[WriteCommand[T1]], options: BulkWriteOptions): Task[BulkWriteResult] =
    ZIO.attempt(underlying.bulkWrite(asJava(commands.map(_.writeModel)), options)).flatMap(_.asyncSingle.unNone)

  def bulkWrite[T1 <: T](cs: ZClientSession, commands: Seq[WriteCommand[T1]], options: BulkWriteOptions): Task[BulkWriteResult] =
    ZIO.attempt(underlying.bulkWrite(cs.underlying, asJava(commands.map(_.writeModel)), options)).flatMap(_.asyncSingle.unNone)

  def renameCollection(target: MongoNamespace, options: RenameCollectionOptions): Task[Unit] =
    ZIO.attempt(underlying.renameCollection(target.toJava, options)).flatMap(_.asyncVoid)

  def renameCollection(cs: ZClientSession, target: MongoNamespace, options: RenameCollectionOptions): Task[Unit] =
    ZIO.attempt(underlying.renameCollection(cs.underlying, target.toJava, options)).flatMap(_.asyncVoid)
}

object ZMongoCollection {
  private[zio] def make[T: ClassTag](collection: MongoCollection[T]): UIO[ZMongoCollection[T]] =
    ZIO.succeed(new ZMongoCollectionLive(collection))
}
