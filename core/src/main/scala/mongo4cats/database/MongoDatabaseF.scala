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

package mongo4cats.database

import cats.Monad
import cats.effect.Async
import cats.syntax.functor._
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.CreateCollectionOptions
import com.mongodb.reactivestreams.client.MongoDatabase
import mongo4cats.database.helpers._
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.bson.codecs.configuration.CodecRegistry

import scala.reflect.ClassTag

trait MongoDatabaseF[F[_]] {
  def name: String
  def getCollection(name: String): F[MongoCollectionF[Document]]
  def getCollection[T: ClassTag](name: String, codecRegistry: CodecRegistry): F[MongoCollectionF[T]]
  def getCollectionWithCodecRegistry[T: ClassTag](name: String, codecRegistry: CodecRegistry): F[MongoCollectionF[T]] =
    getCollection[T](name, codecRegistry)
  def getCollectionWithCodec[T: ClassTag](name: String)(implicit cp: MongoCodecProvider[T]): F[MongoCollectionF[T]] =
    getCollection[T](name, fromRegistries(fromProviders(cp.get), MongoDatabaseF.DefaultCodecRegistry))
  def collectionNames: F[Iterable[String]]
  def createCollection(name: String): F[Unit]
  def createCollection(name: String, options: CreateCollectionOptions): F[Unit]
}

final private class LiveMongoDatabaseF[F[_]](
    private val database: MongoDatabase
)(implicit
    val F: Async[F]
) extends MongoDatabaseF[F] {

  def name: String =
    database.getName

  def getCollection(name: String): F[MongoCollectionF[Document]] =
    F.delay(database.getCollection(name).withCodecRegistry(MongoDatabaseF.DefaultCodecRegistry))
      .map(MongoCollectionF.apply[Document])

  def getCollection[T: ClassTag](name: String, codecRegistry: CodecRegistry): F[MongoCollectionF[T]] = {
    val clazz = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    F.delay {
      database
        .getCollection[T](name, clazz)
        .withCodecRegistry(codecRegistry)
        .withDocumentClass[T](clazz)
    }.map(MongoCollectionF.apply[T])
  }

  def collectionNames: F[Iterable[String]] =
    database.listCollectionNames().asyncIterable[F]

  def createCollection(name: String): F[Unit] =
    createCollection(name, new CreateCollectionOptions())

  def createCollection(name: String, options: CreateCollectionOptions): F[Unit] =
    database.createCollection(name, options).asyncVoid[F]
}

object MongoDatabaseF {

  val DefaultCodecRegistry: CodecRegistry = MongoClientSettings.getDefaultCodecRegistry

  private[mongo4cats] def make[F[_]: Async](database: MongoDatabase): F[MongoDatabaseF[F]] =
    Monad[F].pure(new LiveMongoDatabaseF[F](database))
}
