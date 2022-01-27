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

package mongo4cats.testkit

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.effect.std.Random
import cats.syntax.all._
import com.dimafeng.testcontainers.MongoDBContainer
import mongo4cats.bson.BsonDocumentEncoder
import mongo4cats.client.MongoClient
import mongo4cats.collection.IndexOptions
import mongo4cats.collection.MongoCollection
import mongo4cats.collection.operations.Index
import mongo4cats.database.MongoDatabase
import org.testcontainers.utility.DockerImageName
import org.testcontainers.containers.GenericContainer
import cats.effect.Ref
import cats.effect.Deferred
import com.dimafeng.testcontainers.SingleContainer

object MongoTestkit {

  case class MongoIndex(ix: Index, opts: IndexOptions)
  case class MongoSetup[A](
      dbName: Option[String],
      collName: String,
      indices: List[MongoIndex],
      elems: Seq[A],
      create: Boolean = true
  )

  private val sharedContainer: Resource[IO, MongoDBContainer] = sharedResource(
    MongoDBContainer(DockerImageName.parse("mongo:4.4.10"))
  )

  def mongoTestContainer: Resource[IO, MongoClient[IO]] =
    sharedContainer.flatMap { container =>
      val host = container.host
      val port = container.mappedPort(27017)
      MongoClient.fromConnectionString[IO](s"mongodb://$host:$port")
    }

  def mongoSetupRandomDb(client: MongoClient[IO]): IO[MongoDatabase[IO]] =
    Random
      .scalaUtilRandom[IO]
      .flatMap(_.nextLong.map(l => s"test_$l"))
      .flatMap(client.getDatabase(_))

  def mongoSetup[A: BsonDocumentEncoder, MongoCodecProvider](
      client: MongoClient[IO],
      setup: MongoSetup[A]
  ): IO[MongoCollection[IO]] =
    for {
      db <- setup.dbName.fold(mongoSetupRandomDb(client))(client.getDatabase(_))
      _ <- IO.whenA(setup.create)(db.createCollection(setup.collName))
      coll <- db.getCollection(setup.collName)
      _ <- setup.indices.traverse(i => coll.createIndex(i.ix, i.opts))
      _ <- if (setup.elems.nonEmpty) coll.insertMany[A](setup.elems).void else ().pure[IO]
    } yield coll

  def mongoSetupResource[A: BsonDocumentEncoder](
      client: MongoClient[IO],
      setup: MongoSetup[A]
  ): Resource[IO, MongoCollection[IO]] =
    Resource.make(mongoSetup(client, setup))(_.drop())

  // //////

  // Normally we'd want to stop the container after the suite
  // However, we're using the same container in multiple suites
  // Following the workaround here we simply don't stop the container
  // https://github.com/testcontainers/testcontainers-scala/issues/160
  def sharedResource[C <: SingleContainer[_ <: GenericContainer[_]]](
      containerDefinition: C
  ): Resource[IO, C] = {
    val readyDeferred = Deferred.unsafe[IO, Unit]
    val refContainer = Ref.unsafe[IO, Option[C]](None)

    Resource.eval(
      refContainer
        .modify[(Boolean, C)] {
          case None           => (Some(containerDefinition), (true, containerDefinition))
          case Some(existing) => (Some(existing), (false, existing))
        }
        .flatMap {
          case (true, c)  => IO.delay(c.start()) >> readyDeferred.complete(()).as(c)
          case (false, c) => readyDeferred.get >> IO.pure(c)
        }
    )
  }

}
