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
import cats.implicits._
import com.mongodb.reactivestreams.client.MongoDatabase
import mongo4cats.database.codecs.CustomCodecProvider
import mongo4cats.database.helpers._
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries.fromProviders
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs._

import scala.reflect.ClassTag

trait MongoDatabaseF[F[_]] {
  def name: String
  def getCollection(name: String): F[MongoCollectionF[Document]]
  def getCollection[T: ClassTag](name: String, codecRegistry: CodecRegistry): F[MongoCollectionF[T]]
  def getCollectionWithCodecRegistry[T: ClassTag](name: String, codecRegistry: CodecRegistry): F[MongoCollectionF[T]] =
    getCollection[T](name, codecRegistry)
  def getCollectionWithCodec[T: ClassTag](name: String)(implicit cp: MongoCodecProvider[T]): F[MongoCollectionF[T]] =
    getCollection[T](name, fromProviders(cp.get, MongoDatabaseF.DefaultCodecRegistry))
  def collectionNames: F[Iterable[String]]
  def createCollection(name: String): F[Unit]
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
    database.createCollection(name).asyncVoid[F]
}

object MongoDatabaseF {

  val DefaultCodecRegistry = fromProviders(
    new ValueCodecProvider,
    new BsonValueCodecProvider,
    new DocumentCodecProvider,
    new IterableCodecProvider,
    new MapCodecProvider,
    CustomCodecProvider
  )

  def make[F[_]: Async](database: MongoDatabase): F[MongoDatabaseF[F]] =
    Monad[F].pure(new LiveMongoDatabaseF[F](database))
}
