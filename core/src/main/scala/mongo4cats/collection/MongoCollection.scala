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

import cats.arrow.FunctionK
import cats.~>
import cats.effect.Async
import cats.implicits._
import com.mongodb.client.result._
import com.mongodb.reactivestreams.client.{MongoCollection => JMongoCollection}
import com.mongodb.{MongoNamespace, ReadConcern, ReadPreference, WriteConcern}
import mongo4cats.bson.{Decoder, DocumentEncoder}
import mongo4cats.bson.syntax._
import mongo4cats.client.ClientSession
import mongo4cats.collection.operations.{Aggregate, Filter, Index, Update}
import mongo4cats.collection.queries.{
  AggregateQueryBuilder,
  DistinctQueryBuilder,
  FindQueryBuilder,
  WatchQueryBuilder
}
import mongo4cats.helpers._
import org.bson.BsonDocument
import org.bson.conversions.Bson
import scala.jdk.CollectionConverters._

trait MongoCollection[F[_]] {
  def namespace: MongoNamespace

  def readPreference: ReadPreference
  def withReadPreference(readPreference: ReadPreference): MongoCollection[F]

  def writeConcern: WriteConcern
  def withWriteConcern(writeConcern: WriteConcern): MongoCollection[F]

  def readConcern: ReadConcern
  def withReadConcern(readConcern: ReadConcern): MongoCollection[F]

  //
  def drop(clientSession: Option[ClientSession[F]] = None): F[Unit]

  //
  def aggregate: AggregateQueryBuilder[F]
  def watch: WatchQueryBuilder[F]
  def distinct(fieldName: String): DistinctQueryBuilder[F]
  def find: FindQueryBuilder[F]

  //
  def findOneAndDelete[A: Decoder](
      filter: Bson,
      options: FindOneAndDeleteOptions,
      clientSession: Option[ClientSession[F]]
  ): F[Option[A]]

  def findOneAndDelete[A: Decoder](
      filter: Filter,
      options: FindOneAndDeleteOptions = FindOneAndDeleteOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[Option[A]] =
    findOneAndDelete[A](filter.toBson, options, clientSession)

  //
  def findOneAndUpdate[A: Decoder](
      filter: Bson,
      update: Bson,
      options: FindOneAndUpdateOptions,
      clientSession: Option[ClientSession[F]]
  ): F[Option[A]]

  def findOneAndUpdate[A: Decoder](
      filter: Filter,
      update: Update,
      options: FindOneAndUpdateOptions = FindOneAndUpdateOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[Option[A]] =
    findOneAndUpdate[A](filter.toBson, update.toBson, options, clientSession)

  //
  def findOneAndReplace[A: Decoder: DocumentEncoder](
      filter: Bson,
      replacement: A,
      options: FindOneAndReplaceOptions,
      clientSession: Option[ClientSession[F]]
  ): F[Option[A]]

  def findOneAndReplace[A: Decoder: DocumentEncoder](
      filter: Filter,
      replacement: A,
      options: FindOneAndReplaceOptions = FindOneAndReplaceOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[Option[A]] =
    findOneAndReplace[A](filter.toBson, replacement, options, clientSession)

  //
  def dropIndexByName(
      name: String,
      options: DropIndexOptions = DropIndexOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[Unit]

  def dropIndex(
      keys: Bson,
      options: DropIndexOptions,
      clientSession: Option[ClientSession[F]]
  ): F[Unit]

  def dropIndex(
      keys: Index,
      options: DropIndexOptions = DropIndexOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[Unit] =
    dropIndex(keys.toBson, options, clientSession)

  //
  def dropIndexes(
      options: DropIndexOptions = DropIndexOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[Unit]

  //
  def createIndex(
      key: Bson,
      options: IndexOptions,
      clientSession: Option[ClientSession[F]]
  ): F[String]

  def createIndex(
      index: Index,
      options: IndexOptions = IndexOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[String] =
    createIndex(index.toBson, options, clientSession)

  //
  def updateMany(
      filter: Bson,
      update: Bson,
      options: UpdateOptions,
      clientSession: Option[ClientSession[F]]
  ): F[UpdateResult]

  def updateMany(
      filter: Filter,
      update: Update,
      options: UpdateOptions = UpdateOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[UpdateResult] =
    updateMany(filter.toBson, update.toBson, options, clientSession)

  def updateManyAggregate(
      filter: Bson,
      update: Seq[Bson],
      options: UpdateOptions,
      clientSession: Option[ClientSession[F]]
  ): F[UpdateResult]

  def updateManyAggregate(
      filter: Bson,
      update: Aggregate,
      options: UpdateOptions = UpdateOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[UpdateResult] =
    updateManyAggregate(filter, update.toBsons, options, clientSession)

  //
  def updateOne(
      filter: Bson,
      update: Bson,
      options: UpdateOptions,
      clientSession: Option[ClientSession[F]]
  ): F[UpdateResult]

  def updateOne(
      filter: Filter,
      update: Update,
      options: UpdateOptions = UpdateOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[UpdateResult] =
    updateOne(filter.toBson, update.toBson, options, clientSession)

  def updateOneAggregate(
      filter: Bson,
      update: Seq[Bson],
      options: UpdateOptions,
      clientSession: Option[ClientSession[F]]
  ): F[UpdateResult]

  def updateOneAggregate(
      filter: Bson,
      update: Aggregate,
      options: UpdateOptions = UpdateOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[UpdateResult] =
    updateOneAggregate(filter, update.toBsons, options, clientSession)

  //
  def replaceOne[T: DocumentEncoder](
      filter: Bson,
      replacement: T,
      options: ReplaceOptions,
      clientSession: Option[ClientSession[F]]
  ): F[UpdateResult]

  def replaceOne[T: DocumentEncoder](
      filter: Filter,
      replacement: T,
      options: ReplaceOptions = ReplaceOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[UpdateResult] =
    replaceOne[T](filter.toBson, replacement, options, clientSession)

  //
  def deleteOne(
      filter: Bson,
      options: DeleteOptions,
      clientSession: Option[ClientSession[F]]
  ): F[DeleteResult]

  def deleteOne(
      filter: Filter,
      options: DeleteOptions = DeleteOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[DeleteResult] =
    deleteOne(filter.toBson, options, clientSession)

  //
  def deleteMany(
      filter: Bson,
      options: DeleteOptions,
      clientSession: Option[ClientSession[F]]
  ): F[DeleteResult]

  def deleteMany(
      filter: Filter,
      options: DeleteOptions = DeleteOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[DeleteResult] =
    deleteMany(filter.toBson, options, clientSession)

  //
  def insertOne[T: DocumentEncoder](
      document: T,
      options: InsertOneOptions = InsertOneOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[InsertOneResult]

  //
  def insertMany[T: DocumentEncoder](
      documents: Seq[T],
      options: InsertManyOptions = InsertManyOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[InsertManyResult]

  //
  def count(
      filter: Bson,
      options: CountOptions,
      clientSession: Option[ClientSession[F]]
  ): F[Long]

  def count(
      filter: Filter = Filter.empty,
      options: CountOptions = CountOptions(),
      clientSession: Option[ClientSession[F]] = None
  ): F[Long] =
    count(filter.toBson, options, clientSession)

  def mapK[G[_]](f: F ~> G): MongoCollection[G]
  def asK[G[_]: Async]: MongoCollection[G]
}

object MongoCollection {
  def apply[F[_]: Async](collection: JMongoCollection[BsonDocument]): MongoCollection[F] =
    new TransformedMongoCollection[F, F](collection, FunctionK.id)

  final private case class TransformedMongoCollection[F[_]: Async, G[_]](
      collection: JMongoCollection[BsonDocument],
      transform: F ~> G
  ) extends MongoCollection[G] {
    def namespace =
      collection.getNamespace

    def readPreference =
      collection.getReadPreference
    def withReadPreference(readPreference: ReadPreference) =
      copy(collection = collection.withReadPreference(readPreference))

    def writeConcern =
      collection.getWriteConcern
    def withWriteConcern(writeConcern: WriteConcern) =
      copy(collection = collection.withWriteConcern(writeConcern))

    def readConcern =
      collection.getReadConcern
    def withReadConcern(readConcern: ReadConcern) =
      copy(collection = collection.withReadConcern(readConcern))

    def drop(clientSession: Option[ClientSession[G]]) = transform {
      clientSession match {
        case Some(session) =>
          collection.drop(session.session).asyncVoid[F]
        case None =>
          collection.drop.asyncVoid[F]
      }
    }

    def aggregate =
      AggregateQueryBuilder[F](collection).mapK(transform)

    def watch =
      WatchQueryBuilder[F](collection).mapK(transform)

    def find =
      FindQueryBuilder[F](collection).mapK(transform)

    def distinct(fieldName: String) =
      DistinctQueryBuilder[F](fieldName, collection).mapK(transform)

    def findOneAndDelete[A: Decoder](
        filter: Bson,
        options: FindOneAndDeleteOptions,
        clientSession: Option[ClientSession[G]]
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection
            .findOneAndDelete(session.session, filter, options)
            .asyncOption[F]
            .flatMap(_.traverse { bson =>
              bson.as[A].liftTo[F]
            })
        case None =>
          collection
            .findOneAndDelete(filter, options)
            .asyncOption[F]
            .flatMap(_.traverse { bson =>
              bson.as[A].liftTo[F]
            })
      }
    }

    def findOneAndUpdate[A: Decoder](
        filter: Bson,
        update: Bson,
        options: FindOneAndUpdateOptions,
        clientSession: Option[ClientSession[G]]
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection
            .findOneAndUpdate(session.session, filter, update, options)
            .asyncOption[F]
            .flatMap(_.traverse { bson =>
              bson.as[A].liftTo[F]
            })
        case None =>
          collection
            .findOneAndUpdate(filter, update, options)
            .asyncOption[F]
            .flatMap(_.traverse { bson =>
              bson.as[A].liftTo[F]
            })
      }
    }

    def findOneAndReplace[A: Decoder: DocumentEncoder](
        filter: Bson,
        replacement: A,
        options: FindOneAndReplaceOptions,
        clientSession: Option[ClientSession[G]]
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection
            .findOneAndReplace(session.session, filter, replacement.asBsonDoc, options)
            .asyncOption[F]
            .flatMap(_.traverse { bson =>
              bson.as[A].liftTo[F]
            })
        case None =>
          collection
            .findOneAndReplace(filter, replacement.asBsonDoc, options)
            .asyncOption[F]
            .flatMap(_.traverse { bson =>
              bson.as[A].liftTo[F]
            })
      }
    }

    def dropIndexByName(
        name: String,
        options: DropIndexOptions = DropIndexOptions(),
        clientSession: Option[ClientSession[G]] = None
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection.dropIndex(session.session, name, options).asyncVoid[F]
        case None =>
          collection.dropIndex(name, options).asyncVoid[F]
      }
    }

    def dropIndex(
        keys: Bson,
        options: DropIndexOptions,
        clientSession: Option[ClientSession[G]]
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection.dropIndex(session.session, keys, options).asyncVoid[F]
        case None =>
          collection.dropIndex(keys, options).asyncVoid[F]
      }
    }

    def dropIndexes(
        options: DropIndexOptions = DropIndexOptions(),
        clientSession: Option[ClientSession[G]] = None
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection.dropIndexes(session.session, options).asyncVoid[F]
        case None =>
          collection.dropIndexes(options).asyncVoid[F]
      }
    }

    def createIndex(
        key: Bson,
        options: IndexOptions,
        clientSession: Option[ClientSession[G]]
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection.createIndex(session.session, key, options).asyncSingle[F]
        case None =>
          collection.createIndex(key, options).asyncSingle[F]
      }
    }

    def updateMany(
        filter: Bson,
        update: Bson,
        options: UpdateOptions,
        clientSession: Option[ClientSession[G]]
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection.updateMany(session.session, filter, update, options).asyncSingle[F]
        case None =>
          collection.updateMany(filter, update, options).asyncSingle[F]
      }
    }

    def updateManyAggregate(
        filter: Bson,
        update: Seq[Bson],
        options: UpdateOptions,
        clientSession: Option[ClientSession[G]]
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection.updateMany(session.session, filter, update.asJava, options).asyncSingle[F]
        case None =>
          collection.updateMany(filter, update.asJava, options).asyncSingle[F]
      }
    }

    def updateOne(
        filter: Bson,
        update: Bson,
        options: UpdateOptions,
        clientSession: Option[ClientSession[G]]
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection.updateOne(session.session, filter, update, options).asyncSingle[F]
        case None =>
          collection.updateOne(filter, update, options).asyncSingle[F]
      }
    }

    def updateOneAggregate(
        filter: Bson,
        update: Seq[Bson],
        options: UpdateOptions,
        clientSession: Option[ClientSession[G]]
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection.updateOne(session.session, filter, update.asJava, options).asyncSingle[F]
        case None =>
          collection.updateOne(filter, update.asJava, options).asyncSingle[F]
      }
    }

    def replaceOne[T: DocumentEncoder](
        filter: Bson,
        replacement: T,
        options: ReplaceOptions,
        clientSession: Option[ClientSession[G]]
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection
            .replaceOne(session.session, filter, replacement.asBsonDoc, options)
            .asyncSingle[F]
        case None =>
          collection.replaceOne(filter, replacement.asBsonDoc, options).asyncSingle[F]
      }
    }

    def deleteOne(
        filter: Bson,
        options: DeleteOptions,
        clientSession: Option[ClientSession[G]]
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection.deleteOne(session.session, filter, options).asyncSingle[F]
        case None =>
          collection.deleteOne(filter, options).asyncSingle[F]
      }
    }

    def deleteMany(
        filter: Bson,
        options: DeleteOptions,
        clientSession: Option[ClientSession[G]]
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection.deleteMany(session.session, filter, options).asyncSingle[F]
        case None =>
          collection.deleteMany(filter, options).asyncSingle[F]
      }
    }

    def insertOne[T: DocumentEncoder](
        document: T,
        options: InsertOneOptions = InsertOneOptions(),
        clientSession: Option[ClientSession[G]] = None
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection.insertOne(session.session, document.asBsonDoc, options).asyncSingle[F]
        case None =>
          collection.insertOne(document.asBsonDoc, options).asyncSingle[F]
      }
    }

    def insertMany[T: DocumentEncoder](
        documents: Seq[T],
        options: InsertManyOptions = InsertManyOptions(),
        clientSession: Option[ClientSession[G]] = None
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection
            .insertMany(session.session, documents.map(_.asBsonDoc).asJava, options)
            .asyncSingle[F]
        case None =>
          collection
            .insertMany(documents.map(_.asBsonDoc).asJava, options)
            .asyncSingle[F]
      }
    }

    def count(
        filter: Bson,
        options: CountOptions,
        clientSession: Option[ClientSession[G]]
    ) = transform {
      clientSession match {
        case Some(session) =>
          collection
            .countDocuments(session.session, filter, options)
            .asyncSingle[F]
            .map(_.longValue())
        case None =>
          collection
            .countDocuments(filter, options)
            .asyncSingle[F]
            .map(_.longValue())
      }
    }

    //
    def mapK[H[_]](f: G ~> H) =
      copy(transform = transform andThen f)

    def asK[H[_]: Async] =
      new TransformedMongoCollection[H, H](collection, FunctionK.id)
  }
}
