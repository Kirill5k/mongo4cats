package mongo4cats.database

import cats.effect.{Async, Concurrent, Sync}
import fs2.concurrent.Queue
import mongo4cats.database.helpers._
import org.bson.conversions.Bson
import org.mongodb.scala.result.InsertOneResult
import org.mongodb.scala.{Document, MongoCollection}

import scala.reflect.ClassTag

final class MongoCollectionF[F[_]: Concurrent, T: ClassTag] private(
    private val collection: MongoCollection[T]
) {

  private val NoLimit: Int           = 0
  private val NaturalOrderSort: Bson = Document("$natural" -> 1)


  //aggregate
  //projections
  //update
  //insertMany
  //bulk
  //delete
  //case classes

  def insertOne(document: T): F[InsertOneResult] =
    Async[F].async { k =>
      collection
        .insertOne(document)
        .subscribe(singleItemObserver[InsertOneResult](k))
    }

  def findFirst(filters: Bson): F[T] =
    Async[F].async { k =>
      collection
        .find(filters)
        .first()
        .subscribe(singleItemObserver(k))
    }

  def findMany(
      filters: Bson,
      limit: Int = NoLimit,
      sort: Bson = NaturalOrderSort
  ): F[Iterable[T]] =
    Async[F].async { k =>
      collection
        .find(filters)
        .sort(sort)
        .limit(limit)
        .subscribe(multipleItemsObserver(k))
    }

  def findAll(
      limit: Int = NoLimit,
      sort: Bson = NaturalOrderSort
  ): F[Iterable[T]] =
    Async[F].async { k =>
      collection.find().sort(sort).limit(limit).subscribe(multipleItemsObserver(k))
    }

  def count(): F[Long] =
    Async[F].async { k =>
      collection.countDocuments().subscribe(singleItemObserver[Long](k))
    }

  def stream(): fs2.Stream[F, T] = {
    for {
      q <- fs2.Stream.eval(Queue.noneTerminated[F, T])
      _ <- fs2.Stream.eval(Sync[F].delay(collection.find().subscribe(streamObserver(q))))
      doc <- q.dequeue
    } yield doc
  }
}

object MongoCollectionF {

  def make[F[_]: Concurrent, T: ClassTag](collection: MongoCollection[T]): F[MongoCollectionF[F, T]] =
    Sync[F].delay(new MongoCollectionF[F, T](collection))
}
