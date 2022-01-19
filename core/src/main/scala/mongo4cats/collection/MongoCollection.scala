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

trait MongoCollection {
  def namespace: MongoNamespace

  def readPreference: ReadPreference
  def withReadPreference(readPreference: ReadPreference): MongoCollection

  def writeConcern: WriteConcern
  def withWriteConcern(writeConcern: WriteConcern): MongoCollection

  def readConcern: ReadConcern
  def withReadConcern(readConcern: ReadConcern): MongoCollection

  //
  def drop[F[_]: Async](session: ClientSession = ClientSession.void): F[Unit]

  //
  def aggregate: AggregateQueryBuilder
  def watch: WatchQueryBuilder
  def distinct(fieldName: String): DistinctQueryBuilder
  def find: FindQueryBuilder

  //
  def findOneAndDelete[F[_]: Async, A: Decoder](
      filter: Bson,
      options: FindOneAndDeleteOptions,
      session: ClientSession
  ): F[Option[A]]

  def findOneAndDelete[F[_]: Async, A: Decoder](
      filter: Filter,
      options: FindOneAndDeleteOptions = FindOneAndDeleteOptions(),
      session: ClientSession = ClientSession.void
  ): F[Option[A]] =
    findOneAndDelete[F, A](filter.toBson, options, session)

  //
  def findOneAndUpdate[F[_]: Async, A: Decoder](
      filter: Bson,
      update: Bson,
      options: FindOneAndUpdateOptions,
      session: ClientSession
  ): F[Option[A]]

  def findOneAndUpdate[F[_]: Async, A: Decoder](
      filter: Filter,
      update: Update,
      options: FindOneAndUpdateOptions = FindOneAndUpdateOptions(),
      session: ClientSession = ClientSession.void
  ): F[Option[A]] =
    findOneAndUpdate[F, A](filter.toBson, update.toBson, options, session)

  //
  def findOneAndReplace[F[_]: Async, A: Decoder: DocumentEncoder](
      filter: Bson,
      replacement: A,
      options: FindOneAndReplaceOptions,
      session: ClientSession
  ): F[Option[A]]

  def findOneAndReplace[F[_]: Async, A: Decoder: DocumentEncoder](
      filter: Filter,
      replacement: A,
      options: FindOneAndReplaceOptions = FindOneAndReplaceOptions(),
      session: ClientSession = ClientSession.void
  ): F[Option[A]] =
    findOneAndReplace[F, A](filter.toBson, replacement, options, session)

  //
  def dropIndexByName[F[_]: Async](
      name: String,
      options: DropIndexOptions = DropIndexOptions(),
      session: ClientSession = ClientSession.void
  ): F[Unit]

  def dropIndex[F[_]: Async](
      keys: Bson,
      options: DropIndexOptions,
      session: ClientSession
  ): F[Unit]

  def dropIndex[F[_]: Async](
      keys: Index,
      options: DropIndexOptions = DropIndexOptions(),
      session: ClientSession = ClientSession.void
  ): F[Unit] =
    dropIndex[F](keys.toBson, options, session)

  //
  def dropIndexes[F[_]: Async](
      options: DropIndexOptions = DropIndexOptions(),
      session: ClientSession = ClientSession.void
  ): F[Unit]

  //
  def createIndex[F[_]: Async](
      key: Bson,
      options: IndexOptions,
      session: ClientSession
  ): F[String]

  def createIndex[F[_]: Async](
      index: Index,
      options: IndexOptions = IndexOptions(),
      session: ClientSession = ClientSession.void
  ): F[String] =
    createIndex[F](index.toBson, options, session)

  //
  def updateMany[F[_]: Async](
      filter: Bson,
      update: Bson,
      options: UpdateOptions,
      session: ClientSession
  ): F[UpdateResult]

  def updateMany[F[_]: Async](
      filter: Filter,
      update: Update,
      options: UpdateOptions = UpdateOptions(),
      session: ClientSession = ClientSession.void
  ): F[UpdateResult] =
    updateMany[F](filter.toBson, update.toBson, options, session)

  def updateManyAggregate[F[_]: Async](
      filter: Bson,
      update: Seq[Bson],
      options: UpdateOptions,
      session: ClientSession
  ): F[UpdateResult]

  def updateManyAggregate[F[_]: Async](
      filter: Bson,
      update: Aggregate,
      options: UpdateOptions = UpdateOptions(),
      session: ClientSession = ClientSession.void
  ): F[UpdateResult] =
    updateManyAggregate[F](filter, update.toBsons, options, session)

  //
  def updateOne[F[_]: Async](
      filter: Bson,
      update: Bson,
      options: UpdateOptions,
      session: ClientSession
  ): F[UpdateResult]

  def updateOne[F[_]: Async](
      filter: Filter,
      update: Update,
      options: UpdateOptions = UpdateOptions(),
      session: ClientSession = ClientSession.void
  ): F[UpdateResult] =
    updateOne[F](filter.toBson, update.toBson, options, session)

  def updateOneAggregate[F[_]: Async](
      filter: Bson,
      update: Seq[Bson],
      options: UpdateOptions,
      session: ClientSession
  ): F[UpdateResult]

  def updateOneAggregate[F[_]: Async](
      filter: Bson,
      update: Aggregate,
      options: UpdateOptions = UpdateOptions(),
      session: ClientSession = ClientSession.void
  ): F[UpdateResult] =
    updateOneAggregate[F](filter, update.toBsons, options, session)

  //
  def replaceOne[F[_]: Async, T: DocumentEncoder](
      filter: Bson,
      replacement: T,
      options: ReplaceOptions,
      session: ClientSession
  ): F[UpdateResult]

  def replaceOne[F[_]: Async, T: DocumentEncoder](
      filter: Filter,
      replacement: T,
      options: ReplaceOptions = ReplaceOptions(),
      session: ClientSession = ClientSession.void
  ): F[UpdateResult] =
    replaceOne[F, T](filter.toBson, replacement, options, session)

  //
  def deleteOne[F[_]: Async](
      filter: Bson,
      options: DeleteOptions,
      session: ClientSession
  ): F[DeleteResult]

  def deleteOne[F[_]: Async](
      filter: Filter,
      options: DeleteOptions = DeleteOptions(),
      session: ClientSession = ClientSession.void
  ): F[DeleteResult] =
    deleteOne[F](filter.toBson, options, session)

  //
  def deleteMany[F[_]: Async](
      filter: Bson,
      options: DeleteOptions,
      session: ClientSession
  ): F[DeleteResult]

  def deleteMany[F[_]: Async](
      filter: Filter,
      options: DeleteOptions = DeleteOptions(),
      session: ClientSession = ClientSession.void
  ): F[DeleteResult] =
    deleteMany[F](filter.toBson, options, session)

  //
  def insertOne[F[_]: Async, T: DocumentEncoder](
      document: T,
      options: InsertOneOptions = InsertOneOptions(),
      session: ClientSession = ClientSession.void
  ): F[InsertOneResult]

  //
  def insertMany[F[_]: Async, T: DocumentEncoder](
      documents: Seq[T],
      options: InsertManyOptions = InsertManyOptions(),
      session: ClientSession = ClientSession.void
  ): F[InsertManyResult]

  //
  def count[F[_]: Async](
      filter: Bson,
      options: CountOptions,
      session: ClientSession
  ): F[Long]

  def count[F[_]: Async](
      filter: Filter = Filter.empty,
      options: CountOptions = CountOptions(),
      session: ClientSession = ClientSession.void
  ): F[Long] =
    count(filter.toBson, options, session)
}

object MongoCollection {
  def apply(collection: JMongoCollection[BsonDocument]): MongoCollection = new MongoCollection {
    def namespace =
      collection.getNamespace

    def readPreference =
      collection.getReadPreference
    def withReadPreference(readPreference: ReadPreference) =
      MongoCollection(collection.withReadPreference(readPreference))

    def writeConcern =
      collection.getWriteConcern
    def withWriteConcern(writeConcern: WriteConcern) =
      MongoCollection(collection.withWriteConcern(writeConcern))

    def readConcern =
      collection.getReadConcern
    def withReadConcern(readConcern: ReadConcern) =
      MongoCollection(collection.withReadConcern(readConcern))

    def drop[F[_]: Async](session: ClientSession): F[Unit] =
      if (!session.isNull)
        collection.drop(session.session).asyncVoid[F]
      else
        collection.drop.asyncVoid[F]

    def aggregate: AggregateQueryBuilder =
      AggregateQueryBuilder(collection, List.empty, None, List.empty)

    def watch =
      WatchQueryBuilder(collection, List.empty, None, List.empty)

    def find =
      FindQueryBuilder(collection, None, List.empty)

    def distinct(fieldName: String) =
      DistinctQueryBuilder(fieldName, collection, None, List.empty)

    def findOneAndDelete[F[_]: Async, A: Decoder](
        filter: Bson,
        options: FindOneAndDeleteOptions,
        session: ClientSession
    ): F[Option[A]] =
      if (session.isNull)
        collection
          .findOneAndDelete(session.session, filter, options)
          .asyncOption[F]
          .flatMap(_.traverse { bson =>
            bson.as[A].liftTo[F]
          })
      else
        collection
          .findOneAndDelete(filter, options)
          .asyncOption[F]
          .flatMap(_.traverse { bson =>
            bson.as[A].liftTo[F]
          })

    def findOneAndUpdate[F[_]: Async, A: Decoder](
        filter: Bson,
        update: Bson,
        options: FindOneAndUpdateOptions,
        session: ClientSession
    ): F[Option[A]] =
      if (!session.isNull)
        collection
          .findOneAndUpdate(session.session, filter, update, options)
          .asyncOption[F]
          .flatMap(_.traverse { bson =>
            bson.as[A].liftTo[F]
          })
      else
        collection
          .findOneAndUpdate(filter, update, options)
          .asyncOption[F]
          .flatMap(_.traverse { bson =>
            bson.as[A].liftTo[F]
          })

    def findOneAndReplace[F[_]: Async, A: Decoder: DocumentEncoder](
        filter: Bson,
        replacement: A,
        options: FindOneAndReplaceOptions,
        session: ClientSession
    ): F[Option[A]] =
      if (!session.isNull)
        collection
          .findOneAndReplace(session.session, filter, replacement.asBsonDoc, options)
          .asyncOption[F]
          .flatMap(_.traverse { bson =>
            bson.as[A].liftTo[F]
          })
      else
        collection
          .findOneAndReplace(filter, replacement.asBsonDoc, options)
          .asyncOption[F]
          .flatMap(_.traverse { bson =>
            bson.as[A].liftTo[F]
          })

    def dropIndexByName[F[_]: Async](
        name: String,
        options: DropIndexOptions = DropIndexOptions(),
        session: ClientSession = ClientSession.void
    ): F[Unit] =
      if (!session.isNull)
        collection.dropIndex(session.session, name, options).asyncVoid[F]
      else
        collection.dropIndex(name, options).asyncVoid[F]

    def dropIndex[F[_]: Async](
        keys: Bson,
        options: DropIndexOptions,
        session: ClientSession
    ): F[Unit] =
      if (!session.isNull)
        collection.dropIndex(session.session, keys, options).asyncVoid[F]
      else
        collection.dropIndex(keys, options).asyncVoid[F]

    def dropIndexes[F[_]: Async](
        options: DropIndexOptions = DropIndexOptions(),
        session: ClientSession = ClientSession.void
    ): F[Unit] =
      if (!session.isNull)
        collection.dropIndexes(session.session, options).asyncVoid[F]
      else
        collection.dropIndexes(options).asyncVoid[F]

    def createIndex[F[_]: Async](
        key: Bson,
        options: IndexOptions,
        session: ClientSession
    ): F[String] =
      if (!session.isNull)
        collection.createIndex(session.session, key, options).asyncSingle[F]
      else
        collection.createIndex(key, options).asyncSingle[F]

    def updateMany[F[_]: Async](
        filter: Bson,
        update: Bson,
        options: UpdateOptions,
        session: ClientSession
    ): F[UpdateResult] =
      if (!session.isNull)
        collection.updateMany(session.session, filter, update, options).asyncSingle[F]
      else
        collection.updateMany(filter, update, options).asyncSingle[F]

    def updateManyAggregate[F[_]: Async](
        filter: Bson,
        update: Seq[Bson],
        options: UpdateOptions,
        session: ClientSession
    ): F[UpdateResult] =
      if (!session.isNull)
        collection.updateMany(session.session, filter, update.asJava, options).asyncSingle[F]
      else
        collection.updateMany(filter, update.asJava, options).asyncSingle[F]

    def updateOne[F[_]: Async](
        filter: Bson,
        update: Bson,
        options: UpdateOptions,
        session: ClientSession
    ): F[UpdateResult] =
      if (!session.isNull)
        collection.updateOne(session.session, filter, update, options).asyncSingle[F]
      else
        collection.updateOne(filter, update, options).asyncSingle[F]

    def updateOneAggregate[F[_]: Async](
        filter: Bson,
        update: Seq[Bson],
        options: UpdateOptions,
        session: ClientSession
    ): F[UpdateResult] =
      if (!session.isNull)
        collection.updateOne(session.session, filter, update.asJava, options).asyncSingle[F]
      else
        collection.updateOne(filter, update.asJava, options).asyncSingle[F]

    def replaceOne[F[_]: Async, T: DocumentEncoder](
        filter: Bson,
        replacement: T,
        options: ReplaceOptions,
        session: ClientSession
    ): F[UpdateResult] =
      if (!session.isNull)
        collection
          .replaceOne(session.session, filter, replacement.asBsonDoc, options)
          .asyncSingle[F]
      else
        collection.replaceOne(filter, replacement.asBsonDoc, options).asyncSingle[F]

    def deleteOne[F[_]: Async](
        filter: Bson,
        options: DeleteOptions,
        session: ClientSession
    ): F[DeleteResult] =
      if (!session.isNull)
        collection.deleteOne(session.session, filter, options).asyncSingle[F]
      else
        collection.deleteOne(filter, options).asyncSingle[F]

    def deleteMany[F[_]: Async](
        filter: Bson,
        options: DeleteOptions,
        session: ClientSession
    ): F[DeleteResult] =
      if (!session.isNull)
        collection.deleteMany(session.session, filter, options).asyncSingle[F]
      else
        collection.deleteMany(filter, options).asyncSingle[F]

    def insertOne[F[_]: Async, T: DocumentEncoder](
        document: T,
        options: InsertOneOptions = InsertOneOptions(),
        session: ClientSession = ClientSession.void
    ): F[InsertOneResult] =
      if (!session.isNull)
        collection.insertOne(session.session, document.asBsonDoc, options).asyncSingle[F]
      else
        collection.insertOne(document.asBsonDoc, options).asyncSingle[F]

    def insertMany[F[_]: Async, T: DocumentEncoder](
        documents: Seq[T],
        options: InsertManyOptions = InsertManyOptions(),
        session: ClientSession = ClientSession.void
    ): F[InsertManyResult] =
      if (!session.isNull)
        collection
          .insertMany(session.session, documents.map(_.asBsonDoc).asJava, options)
          .asyncSingle[F]
      else
        collection
          .insertMany(documents.map(_.asBsonDoc).asJava, options)
          .asyncSingle[F]

    def count[F[_]: Async](
        filter: Bson,
        options: CountOptions,
        session: ClientSession
    ): F[Long] =
      if (!session.isNull)
        collection
          .countDocuments(session.session, filter, options)
          .asyncSingle[F]
          .map(_.longValue())
      else
        collection
          .countDocuments(filter, options)
          .asyncSingle[F]
          .map(_.longValue())
  }
}
