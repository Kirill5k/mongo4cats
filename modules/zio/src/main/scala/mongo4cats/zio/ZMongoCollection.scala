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

import scala.reflect.ClassTag

final private class ZMongoCollectionLive[T: ClassTag](
    val underlying: MongoCollection[T]
) extends ZMongoCollection[T] with AsJava {

  def withReadPreference(rp: ReadPreference): ZMongoCollection[T] = new ZMongoCollectionLive(underlying.withReadPreference(rp))
  def withWriteConcern(wc: WriteConcern): ZMongoCollection[T]     = new ZMongoCollectionLive(underlying.withWriteConcern(wc))
  def withReadConcern(rc: ReadConcern): ZMongoCollection[T]       = new ZMongoCollectionLive(underlying.withReadConcern(rc))
  def as[Y: ClassTag]: ZMongoCollection[Y]                        = new ZMongoCollectionLive[Y](withNewDocumentClass(underlying))
  def withAddedCodec(newCodecRegistry: CodecRegistry): ZMongoCollection[T] =
    new ZMongoCollectionLive[T](underlying.withCodecRegistry(CodecRegistry.from(codecs, newCodecRegistry)))

  def drop: Task[Unit]                     = underlying.drop().asyncVoid
  def drop(cs: ZClientSession): Task[Unit] = underlying.drop(cs.underlying).asyncVoid

  def aggregate[Y: ClassTag](pipeline: Seq[Bson]): Queries.Aggregate[Y] =
    Queries.aggregate(underlying.aggregate(asJava(pipeline), Clazz.tag[Y]))
  def aggregate[Y: ClassTag](pipeline: Aggregate): Queries.Aggregate[Y] =
    Queries.aggregate(underlying.aggregate(pipeline.toBson, Clazz.tag[Y]))
  def aggregate[Y: ClassTag](cs: ZClientSession, pipeline: Aggregate): Queries.Aggregate[Y] =
    Queries.aggregate(underlying.aggregate(cs.underlying, pipeline.toBson, Clazz.tag[Y]))

  def watch(pipeline: Seq[Bson]): Queries.Watch[T] = Queries.watch(underlying.watch(asJava(pipeline), Clazz.tag[T]))
  def watch(pipeline: Aggregate): Queries.Watch[T] = Queries.watch(underlying.watch(pipeline.toBson, Clazz.tag[T]))
  def watch(cs: ZClientSession, pipeline: Aggregate): Queries.Watch[T] =
    Queries.watch(underlying.watch(cs.underlying, pipeline.toBson, Clazz.tag[T]))

  def distinct[Y: ClassTag](fieldName: String, filter: Bson): Queries.Distinct[Y] =
    Queries.distinct(underlying.distinct(fieldName, filter, Clazz.tag[Y]))

  def distinct[Y: ClassTag](cs: ZClientSession, fieldName: String, filter: Filter): Queries.Distinct[Y] =
    Queries.distinct(underlying.distinct(cs.underlying, fieldName, filter.toBson, Clazz.tag[Y]))

  def find(filter: Bson): Queries.Find[T] = Queries.find(underlying.find(filter))
  def find(cs: ZClientSession, filter: Filter): Queries.Find[T] =
    Queries.find(underlying.find(cs.underlying, filter.toBson))

  def findOneAndDelete(filter: Bson, options: FindOneAndDeleteOptions): Task[Option[T]] =
    underlying.findOneAndDelete(filter, options).asyncSingle
  def findOneAndDelete(cs: ZClientSession, filter: Filter, options: FindOneAndDeleteOptions): Task[Option[T]] =
    underlying.findOneAndDelete(cs.underlying, filter.toBson, options).asyncSingle

  def findOneAndUpdate(filter: Bson, update: Bson, options: FindOneAndUpdateOptions): Task[Option[T]] =
    underlying.findOneAndUpdate(filter, update, options).asyncSingle

  def findOneAndUpdate(
      cs: ZClientSession,
      filter: Filter,
      update: Update,
      options: FindOneAndUpdateOptions
  ): Task[Option[T]] =
    underlying.findOneAndUpdate(cs.underlying, filter.toBson, update.toBson, options).asyncSingle

  def findOneAndReplace(filter: Bson, replacement: T, options: FindOneAndReplaceOptions): Task[Option[T]] =
    underlying.findOneAndReplace(filter, replacement, options).asyncSingle

  def findOneAndReplace(
      cs: ZClientSession,
      filter: Filter,
      replacement: T,
      options: FindOneAndReplaceOptions
  ): Task[Option[T]] =
    underlying.findOneAndReplace(cs.underlying, filter.toBson, replacement, options).asyncSingle

  def dropIndex(name: String, options: DropIndexOptions): Task[Unit] =
    underlying.dropIndex(name, options).asyncVoid

  def dropIndex(cs: ZClientSession, name: String, options: DropIndexOptions): Task[Unit] =
    underlying.dropIndex(cs.underlying, name, options).asyncVoid

  def dropIndex(keys: Bson, options: DropIndexOptions): Task[Unit] =
    underlying.dropIndex(keys, options).asyncVoid

  def dropIndex(cs: ZClientSession, index: Index, options: DropIndexOptions): Task[Unit] =
    underlying.dropIndex(cs.underlying, index.toBson, options).asyncVoid

  def dropIndexes(options: DropIndexOptions): Task[Unit] =
    underlying.dropIndexes(options).asyncVoid

  def dropIndexes(cs: ZClientSession, options: DropIndexOptions): Task[Unit] =
    underlying.dropIndexes(cs.underlying, options).asyncVoid

  def createIndex(key: Bson, options: IndexOptions): Task[String] =
    underlying.createIndex(key, options).asyncSingle.unNone

  def createIndex(cs: ZClientSession, index: Index, options: IndexOptions): Task[String] =
    underlying.createIndex(cs.underlying, index.toBson, options).asyncSingle.unNone

  def listIndexes(cs: ZClientSession): Task[Iterable[Document]] =
    underlying.listIndexes(cs.underlying).asyncIterableF(Document.fromJava)

  def listIndexes[Y: ClassTag]: Task[Iterable[Y]] =
    underlying.listIndexes(Clazz.tag[Y]).asyncIterable

  def listIndexes: Task[Iterable[Document]] =
    underlying.listIndexes().asyncIterableF(Document.fromJava)

  def listIndexes[Y: ClassTag](cs: ZClientSession): Task[Iterable[Y]] =
    underlying.listIndexes(cs.underlying, Clazz.tag[Y]).asyncIterable

  def updateMany(filter: Bson, update: Bson, options: UpdateOptions): Task[UpdateResult] =
    underlying.updateMany(filter, update, options).asyncSingle.unNone

  def updateMany(cs: ZClientSession, filter: Filter, update: Update, options: UpdateOptions): Task[UpdateResult] =
    underlying.updateMany(cs.underlying, filter.toBson, update.toBson, options).asyncSingle.unNone

  def updateMany(filter: Bson, update: Seq[Bson], options: UpdateOptions): Task[UpdateResult] =
    underlying.updateMany(filter, asJava(update), options).asyncSingle.unNone

  def updateOne(filter: Bson, update: Bson, options: UpdateOptions): Task[UpdateResult] =
    underlying.updateOne(filter, update, options).asyncSingle.unNone

  def updateOne(cs: ZClientSession, filter: Filter, update: Update, options: UpdateOptions): Task[UpdateResult] =
    underlying.updateOne(cs.underlying, filter.toBson, update.toBson, options).asyncSingle.unNone

  def updateOne(filter: Bson, update: Seq[Bson], options: UpdateOptions): Task[UpdateResult] =
    underlying.updateOne(filter, asJava(update), options).asyncSingle.unNone

  def replaceOne(filter: Bson, replacement: T, options: ReplaceOptions): Task[UpdateResult] =
    underlying.replaceOne(filter, replacement, options).asyncSingle.unNone

  def replaceOne(cs: ZClientSession, filter: Filter, replacement: T, options: ReplaceOptions): Task[UpdateResult] =
    underlying.replaceOne(cs.underlying, filter.toBson, replacement, options).asyncSingle.unNone

  def deleteOne(filter: Bson, options: DeleteOptions): Task[DeleteResult] =
    underlying.deleteOne(filter, options).asyncSingle.unNone

  def deleteOne(cs: ZClientSession, filter: Filter, options: DeleteOptions): Task[DeleteResult] =
    underlying.deleteOne(cs.underlying, filter.toBson, options).asyncSingle.unNone

  def deleteMany(filter: Bson, options: DeleteOptions): Task[DeleteResult] =
    underlying.deleteMany(filter, options).asyncSingle.unNone

  def deleteMany(cs: ZClientSession, filter: Filter, options: DeleteOptions): Task[DeleteResult] =
    underlying.deleteMany(cs.underlying, filter.toBson, options).asyncSingle.unNone

  def insertOne(document: T, options: InsertOneOptions): Task[InsertOneResult] =
    underlying.insertOne(document, options).asyncSingle.unNone

  def insertOne(cs: ZClientSession, document: T, options: InsertOneOptions): Task[InsertOneResult] =
    underlying.insertOne(cs.underlying, document, options).asyncSingle.unNone

  def insertMany(documents: Seq[T], options: InsertManyOptions): Task[InsertManyResult] =
    underlying.insertMany(asJava(documents), options).asyncSingle.unNone

  def insertMany(cs: ZClientSession, documents: Seq[T], options: InsertManyOptions): Task[InsertManyResult] =
    underlying.insertMany(cs.underlying, asJava(documents), options).asyncSingle.unNone

  def count(filter: Bson, options: CountOptions): Task[Long] =
    underlying.countDocuments(filter, options).asyncSingle.unNone.map(_.longValue())

  def count(cs: ZClientSession, filter: Filter, options: CountOptions): Task[Long] =
    underlying.countDocuments(cs.underlying, filter.toBson, options).asyncSingle.unNone.map(_.longValue())

  def bulkWrite[T1 <: T](commands: Seq[WriteCommand[T1]], options: BulkWriteOptions): Task[BulkWriteResult] =
    underlying.bulkWrite(asJava(commands.map(_.writeModel)), options).asyncSingle.unNone

  def bulkWrite[T1 <: T](cs: ZClientSession, commands: Seq[WriteCommand[T1]], options: BulkWriteOptions): Task[BulkWriteResult] =
    underlying.bulkWrite(cs.underlying, asJava(commands.map(_.writeModel)), options).asyncSingle.unNone

  def renameCollection(target: MongoNamespace, options: RenameCollectionOptions): Task[Unit] =
    underlying.renameCollection(target.toJava, options).asyncVoid

  def renameCollection(cs: ZClientSession, target: MongoNamespace, options: RenameCollectionOptions): Task[Unit] =
    underlying.renameCollection(cs.underlying, target.toJava, options).asyncVoid
}

object ZMongoCollection {
  private[zio] def make[T: ClassTag](collection: MongoCollection[T]): UIO[ZMongoCollection[T]] =
    ZIO.succeed(new ZMongoCollectionLive(collection))
}
