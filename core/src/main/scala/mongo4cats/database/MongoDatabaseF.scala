/*
 * Copyright 2020 Mongo DB client wrapper for Cats Effect & Fs2
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

package mongo4cats.database

import cats.effect.{Async, Concurrent, Sync}
import cats.implicits._
import mongo4cats.database.helpers._
import org.mongodb.scala.MongoDatabase

import scala.reflect.ClassTag

final class MongoDatabaseF[F[_]: Concurrent] private (
    private val database: MongoDatabase
) {

  def name: F[String] =
    Sync[F].pure(database.name)

  def getCollection[T: ClassTag](name: String): F[MongoCollectionF[T]] =
    Sync[F]
      .delay(database.getCollection[T](name).withDocumentClass[T]())
      .map(MongoCollectionF.apply[T])

  def collectionNames(): F[Iterable[String]] =
    Async[F].async(multipleItemsAsync(database.listCollectionNames()))

  def createCollection(name: String): F[Unit] =
    Async[F].async(voidAsync(database.createCollection(name)))
}

object MongoDatabaseF {
  def make[F[_]: Concurrent](database: MongoDatabase): F[MongoDatabaseF[F]] =
    Sync[F].delay(new MongoDatabaseF[F](database))
}
