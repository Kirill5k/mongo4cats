package mongo4cats.database

import cats.effect.{Async, Concurrent, Sync}
import fs2.concurrent.Queue
import mongo4cats.database.helpers._
import mongo4cats.database.queries.QueryBuilder
import org.bson.conversions.Bson
import org.mongodb.scala.result.InsertOneResult
import org.mongodb.scala.{Document, MongoCollection, MongoNamespace}

import scala.reflect.ClassTag

final class MongoCollectionF[T: ClassTag] private(
    private val collection: MongoCollection[T]
) {

  def namespace: MongoNamespace =
    collection.namespace

  //watch
  //aggregate
  //update
  //insertMany
  //bulk
  //delete
  //case classes

  def drop[F[_]: Async](): F[Unit] =
    Async[F].async { k =>
      collection.drop().subscribe(voidObserver(k))
    }

  def insertOne[F[_]: Async](document: T): F[InsertOneResult] =
    Async[F].async { k =>
      collection
        .insertOne(document)
        .subscribe(singleItemObserver[InsertOneResult](k))
    }

  def find(): QueryBuilder[T] =
    QueryBuilder(collection.find())

  def find(filter: Bson): QueryBuilder[T] =
    QueryBuilder(collection.find(filter))

  def count[F[_]: Async](): F[Long] =
    Async[F].async { k =>
      collection.countDocuments().subscribe(singleItemObserver[Long](k))
    }

  def count[F[_]: Async](filter: Bson): F[Long] =
    Async[F].async { k =>
      collection.countDocuments(filter).subscribe(singleItemObserver[Long](k))
    }
}

object MongoCollectionF {

  def apply[T: ClassTag](collection: MongoCollection[T]): MongoCollectionF[T] =
    new MongoCollectionF(collection)

}
