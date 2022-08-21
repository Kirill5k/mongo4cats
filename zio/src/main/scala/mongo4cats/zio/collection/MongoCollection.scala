package mongo4cats.zio.collection

import com.mongodb.{MongoNamespace, ReadConcern, ReadPreference, WriteConcern}
import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.result.{DeleteResult, InsertManyResult, InsertOneResult, UpdateResult}
import com.mongodb.reactivestreams.client.{MongoCollection => JMongoCollection}
import mongo4cats.bson.Document
import mongo4cats.collection
import mongo4cats.collection.{BulkWriteOptions, CountOptions, DeleteOptions, DropIndexOptions, FindOneAndDeleteOptions, FindOneAndReplaceOptions, FindOneAndUpdateOptions, IndexOptions, InsertManyOptions, InsertOneOptions, RenameCollectionOptions, ReplaceOptions, UpdateOptions, WriteCommand}
import mongo4cats.collection.operations.{Aggregate, Filter, Index, Update}
import mongo4cats.collection.queries.{AggregateQueryBuilder, DistinctQueryBuilder, FindQueryBuilder, WatchQueryBuilder}
import mongo4cats.zio.client.ClientSession
import org.bson.codecs.configuration.CodecRegistry
import org.bson.conversions.Bson
import zio.Task

import scala.reflect.ClassTag

final private class ZioMongoCollection[T: ClassTag](
    val underlying: JMongoCollection[T]
) extends MongoCollection[T] {
  override def withReadPreference(readPreference: ReadPreference): collection.MongoCollection[Task, T] = ???
  override def withWriteConcern(writeConcert: WriteConcern): collection.MongoCollection[Task, T] = ???
  override def withReadConcern(readConcern: ReadConcern): collection.MongoCollection[Task, T] = ???
  override def as[Y: ClassTag]: collection.MongoCollection[Task, Y] = ???
  override def withAddedCodec(codecRegistry: CodecRegistry): collection.MongoCollection[Task, T] = ???

  override def drop: Task[Unit] = ???
  override def drop(session: ClientSession): Task[Unit] = ???

  override def aggregate[Y: ClassTag](pipeline: Seq[Bson]): AggregateQueryBuilder[Task, Y] = ???
  override def aggregate[Y: ClassTag](pipeline: Aggregate): AggregateQueryBuilder[Task, Y] = ???
  override def aggregate[Y: ClassTag](session: ClientSession, pipeline: Aggregate): AggregateQueryBuilder[Task, Y] = ???

  override def watch(pipeline: Seq[Bson]): WatchQueryBuilder[Task, Document] = ???
  override def watch(pipeline: Aggregate): WatchQueryBuilder[Task, Document] = ???
  override def watch(session: ClientSession, pipeline: Aggregate): WatchQueryBuilder[Task, Document] = ???

  override def distinct[Y: ClassTag](fieldName: String, filter: Bson): DistinctQueryBuilder[Task, Y] = ???
  override def distinct[Y: ClassTag](session: ClientSession, fieldName: String, filter: Filter): DistinctQueryBuilder[Task, Y] = ???

  override def find(filter: Bson): FindQueryBuilder[Task, T] = ???
  override def find(session: ClientSession, filter: Filter): FindQueryBuilder[Task, T] = ???

  override def findOneAndDelete(filter: Bson, options: FindOneAndDeleteOptions): Task[Option[T]] = ???
  override def findOneAndDelete(session: ClientSession, filter: Filter, options: FindOneAndDeleteOptions): Task[Option[T]] = ???

  override def findOneAndUpdate(filter: Bson, update: Bson, options: FindOneAndUpdateOptions): Task[Option[T]] = ???
  override def findOneAndUpdate(session: ClientSession, filter: Filter, update: Update, options: FindOneAndUpdateOptions): Task[Option[T]] = ???

  override def findOneAndReplace(filter: Bson, replacement: T, options: FindOneAndReplaceOptions): Task[Option[T]] = ???
  override def findOneAndReplace(session: ClientSession, filter: Filter, replacement: T, options: FindOneAndReplaceOptions): Task[Option[T]] = ???

  override def dropIndex(name: String, options: DropIndexOptions): Task[Unit] = ???
  override def dropIndex(session: ClientSession, name: String, options: DropIndexOptions): Task[Unit] = ???

  override def dropIndex(keys: Bson, options: DropIndexOptions): Task[Unit] = ???
  override def dropIndex(session: ClientSession, index: Index, options: DropIndexOptions): Task[Unit] = ???

  override def dropIndexes(options: DropIndexOptions): Task[Unit] = ???
  override def dropIndexes(session: ClientSession, options: DropIndexOptions): Task[Unit] = ???

  override def createIndex(key: Bson, options: IndexOptions): Task[String] = ???
  override def createIndex(session: ClientSession, index: Index, options: IndexOptions): Task[String] = ???

  override def listIndexes(session: ClientSession): Task[Iterable[Document]] = ???
  override def listIndexes[Y: ClassTag]: Task[Iterable[Y]] = ???
  override def listIndexes: Task[Iterable[Document]] = ???
  override def listIndexes[Y: ClassTag](cs: ClientSession): Task[Iterable[Y]] = ???

  override def updateMany(filter: Bson, update: Bson, options: UpdateOptions): Task[UpdateResult] = ???
  override def updateMany(session: ClientSession, filter: Filter, update: Update, options: UpdateOptions): Task[UpdateResult] = ???
  override def updateMany(filter: Bson, update: Seq[Bson], options: UpdateOptions): Task[UpdateResult] = ???

  override def updateOne(filter: Bson, update: Bson, options: UpdateOptions): Task[UpdateResult] = ???
  override def updateOne(session: ClientSession, filter: Filter, update: Update, options: UpdateOptions): Task[UpdateResult] = ???
  override def updateOne(filter: Bson, update: Seq[Bson], options: UpdateOptions): Task[UpdateResult] = ???

  override def replaceOne(filter: Bson, replacement: T, options: ReplaceOptions): Task[UpdateResult] = ???
  override def replaceOne(session: ClientSession, filter: Filter, replacement: T, options: ReplaceOptions): Task[UpdateResult] = ???

  override def deleteOne(filter: Bson, options: DeleteOptions): Task[DeleteResult] = ???
  override def deleteOne(session: ClientSession, filter: Filter, options: DeleteOptions): Task[DeleteResult] = ???

  override def deleteMany(filter: Bson, options: DeleteOptions): Task[DeleteResult] = ???
  override def deleteMany(session: ClientSession, filter: Filter, options: DeleteOptions): Task[DeleteResult] = ???

  override def insertOne(document: T, options: InsertOneOptions): Task[InsertOneResult] = ???
  override def insertOne(session: ClientSession, document: T, options: InsertOneOptions): Task[InsertOneResult] = ???

  override def insertMany(documents: Seq[T], options: InsertManyOptions): Task[InsertManyResult] = ???
  override def insertMany(session: ClientSession, documents: Seq[T], options: InsertManyOptions): Task[InsertManyResult] = ???

  override def count(filter: Bson, options: CountOptions): Task[Long] = ???
  override def count(session: ClientSession, filter: Filter, options: CountOptions): Task[Long] = ???

  override def bulkWrite[T1 <: T](commands: Seq[WriteCommand[T1]], options: BulkWriteOptions): Task[BulkWriteResult] = ???
  override def bulkWrite[T1 <: T](cs: ClientSession, commands: Seq[WriteCommand[T1]], options: BulkWriteOptions): Task[BulkWriteResult] = ???

  override def renameCollection(target: MongoNamespace, options: RenameCollectionOptions): Task[Unit] = ???
  override def renameCollection(session: ClientSession, target: MongoNamespace, options: RenameCollectionOptions): Task[Unit] = ???
}

object MongoCollection {}
