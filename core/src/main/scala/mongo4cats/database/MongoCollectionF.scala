package mongo4cats.database

import cats.effect.{Async, Concurrent, Sync}
import fs2.concurrent.Queue
import org.bson.conversions.Bson
import org.mongodb.scala.{Document, MongoCollection, Observer}
import org.mongodb.scala.result.InsertOneResult
import org.mongodb.scala.model.Filters._
import helpers._
import mongo4cats.errors.OperationError
import org.reactivestreams.{Subscriber, Subscription}

final class MongoCollectionF[F[_]: Concurrent] private(
    private val collection: MongoCollection[Document]
) {

  private val NoLimit: Int           = 0
  private val NaturalOrderSort: Bson = Document("$natural" -> 1)

  def insertOne(document: Document): F[InsertOneResult] =
    Async[F].async { k =>
      collection
        .insertOne(document)
        .subscribe(singleItemObserver[InsertOneResult](k))
    }

  def findFirst(filters: Bson): F[Document] =
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
  ): F[Iterable[Document]] =
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
  ): F[Iterable[Document]] =
    Async[F].async { k =>
      collection.find().sort(sort).limit(limit).subscribe(multipleItemsObserver(k))
    }

  def count(): F[Long] =
    Async[F].async { k =>
      collection.countDocuments().subscribe(singleItemObserver[Long](k))
    }

  def stream(): fs2.Stream[F, Document] = {
    for {
      q <- fs2.Stream.eval(Queue.noneTerminated[F, Document])
      _ <- fs2.Stream.eval(Sync[F].delay(collection.find().subscribe(streamObserver(q))))
      doc <- q.dequeue
    } yield doc
  }
}

object MongoCollectionF {

  def make[F[_]: Concurrent](collection: MongoCollection[Document]): F[MongoCollectionF[F]] =
    Sync[F].delay(new MongoCollectionF[F](collection))
}
