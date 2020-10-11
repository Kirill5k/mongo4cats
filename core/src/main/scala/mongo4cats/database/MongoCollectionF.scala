package mongo4cats.database

import cats.effect.Async
import mongo4cats.database.helpers._
import mongo4cats.database.queries.{DistinctQueryBuilder, FindQueryBuilder}
import org.bson.conversions.Bson
import org.mongodb.scala.model._
import org.mongodb.scala.result._
import org.mongodb.scala.{MongoCollection, MongoNamespace, SingleObservable}

import scala.reflect.ClassTag

final class MongoCollectionF[T: ClassTag] private(
    private val collection: MongoCollection[T]
) {

  def namespace: MongoNamespace =
    collection.namespace

  def documentClass: Class[T] =
    collection.documentClass

  //watch
  //aggregate
  //case classes
  //createIndexes

  def distinct(fieldName: String): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](collection.distinct(fieldName))

  def distinct(fieldName: String, filter: Bson): DistinctQueryBuilder[T] =
    DistinctQueryBuilder[T](collection.distinct(fieldName, filter))

  def find: FindQueryBuilder[T] =
    FindQueryBuilder[T](collection.find())

  def find(filter: Bson): FindQueryBuilder[T] =
    FindQueryBuilder[T](collection.find(filter))

  def findOneAndDelete[F[_]: Async](filter: Bson): F[T] =
    doAsync(collection.findOneAndDelete(filter))

  def findOneAndDelete[F[_]: Async](filter: Bson, options: FindOneAndDeleteOptions): F[T] =
    doAsync(collection.findOneAndDelete(filter, options))

  def findOneAndUpdate[F[_]: Async](filter: Bson, update: Bson): F[T] =
    doAsync(collection.findOneAndUpdate(filter, update))

  def findOneAndUpdate[F[_]: Async](filter: Bson, update: Bson, options: FindOneAndUpdateOptions): F[T] =
    doAsync(collection.findOneAndUpdate(filter, update, options))

  def findOneAndReplace[F[_]: Async](filter: Bson, replacement: T): F[T] =
    doAsync(collection.findOneAndReplace(filter, replacement))

  def findOneAndReplace[F[_]: Async](filter: Bson, replacement: T, options: FindOneAndReplaceOptions): F[T] =
    doAsync(collection.findOneAndReplace(filter, replacement, options))

  def dropIndex[F[_]: Async](name: String): F[Unit] =
    doAsyncVoid(collection.dropIndex(name))

  def dropIndex[F[_]: Async](keys: Bson): F[Unit] =
    doAsyncVoid(collection.dropIndex(keys))

  def dropIndex[F[_]: Async](keys: Bson, options: DropIndexOptions): F[Unit] =
    doAsyncVoid(collection.dropIndex(keys, options))

  def dropIndexes[F[_]: Async](options: DropIndexOptions): F[Unit] =
    doAsyncVoid(collection.dropIndexes(options))

  def dropIndexes[F[_]: Async](): F[Unit] =
    doAsyncVoid(collection.dropIndexes())

  def drop[F[_]: Async](): F[Unit] =
    doAsyncVoid(collection.drop())

  def createIndex[F[_]: Async](filters: Bson): F[String] =
    doAsync(collection.createIndex(filters))

  def createIndex[F[_]: Async](filter: Bson, options: IndexOptions): F[String] =
    doAsync(collection.createIndex(filter, options))

  def updateMany[F[_]: Async](filters: Bson, update: Bson): F[UpdateResult] =
    doAsync(collection.updateMany(filters, update))

  def updateMany[F[_]: Async](filters: Bson, update: Seq[Bson]): F[UpdateResult] =
    doAsync(collection.updateMany(filters, update))

  def updateMany[F[_]: Async](filter: Bson, update: Bson, options: UpdateOptions): F[UpdateResult] =
    doAsync(collection.updateMany(filter, update, options))

  def updateMany[F[_]: Async](filter: Bson, update: Seq[Bson], options: UpdateOptions): F[UpdateResult] =
    doAsync(collection.updateMany(filter, update, options))

  def updateOne[F[_]: Async](filters: Bson, update: Bson): F[UpdateResult] =
    doAsync(collection.updateOne(filters, update))

  def updateOne[F[_]: Async](filters: Bson, update: Seq[Bson]): F[UpdateResult] =
    doAsync(collection.updateOne(filters, update))

  def updateOne[F[_]: Async](filter: Bson, update: Bson, options: UpdateOptions): F[UpdateResult] =
    doAsync(collection.updateOne(filter, update, options))

  def updateOne[F[_]: Async](filter: Bson, update: Seq[Bson], options: UpdateOptions): F[UpdateResult] =
    doAsync(collection.updateOne(filter, update, options))

  def replaceOne[F[_]: Async](filters: Bson, replacement: T): F[UpdateResult] =
    doAsync(collection.replaceOne(filters, replacement))

  def replaceOne[F[_]: Async](filter: Bson, replacement: T, options: ReplaceOptions): F[UpdateResult] =
    doAsync(collection.replaceOne(filter, replacement, options))

  def deleteOne[F[_]: Async](filters: Bson): F[DeleteResult] =
    doAsync(collection.deleteOne(filters))

  def deleteOne[F[_]: Async](filter: Bson, options: DeleteOptions): F[DeleteResult] =
    doAsync(collection.deleteOne(filter, options))

  def deleteMany[F[_]: Async](filters: Bson): F[DeleteResult] =
    doAsync(collection.deleteMany(filters))

  def deleteMany[F[_]: Async](filter: Bson, options: DeleteOptions): F[DeleteResult] =
    doAsync(collection.deleteMany(filter, options))

  def insertOne[F[_]: Async](document: T): F[InsertOneResult] =
    doAsync(collection.insertOne(document))

  def insertOne[F[_]: Async](document: T, options: InsertOneOptions): F[InsertOneResult] =
    doAsync(collection.insertOne(document, options))

  def insertMany[F[_]: Async](documents: Seq[T]): F[InsertManyResult] =
    doAsync(collection.insertMany(documents))

  def insertMany[F[_]: Async](documents: Seq[T], options: InsertManyOptions): F[InsertManyResult] =
    doAsync(collection.insertMany(documents, options))

  def count[F[_]: Async]: F[Long] =
    doAsync(collection.countDocuments())

  def count[F[_]: Async](filter: Bson): F[Long] =
    doAsync(collection.countDocuments(filter))

  def count[F[_]: Async](filter: Bson, options: CountOptions): F[Long] =
    doAsync(collection.countDocuments(filter, options))

  private def doAsync[F[_]: Async, R](observable: => SingleObservable[R]): F[R] =
    Async[F].async { k =>
      observable.subscribe(singleItemObserver[R](k))
    }

  private def doAsyncVoid[F[_]: Async](observable: => SingleObservable[Void]): F[Unit] =
    Async[F].async { k =>
      observable.subscribe(voidObserver(k))
    }
}

object MongoCollectionF {

  def apply[T: ClassTag](collection: MongoCollection[T]): MongoCollectionF[T] =
    new MongoCollectionF(collection)

}
