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

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import mongo4cats.bson.Document
import mongo4cats.codecs.MongoCodecProvider
import mongo4cats.database.CodecInheritanceFixture._
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

class MongoDatabaseCodecSpec extends AsyncWordSpec with Matchers {

  "A MongoDatabase" when {
    "creating collections" should {
      "inherit added codecs and database overrides alongside library defaults" in {
        val markerCodec = codec[Marker]
        val stringCodec = codec[String]
        val result      = MongoDatabase.make[IO](database(registry(stringCodec))).flatMap { initial =>
          val db = initial.withAddedCodec(registry(markerCodec))
          db.getCollection("documents").map { collection =>
            collection.documentClass mustBe classOf[Document]
            collection.codecs.get(classOf[Marker]) mustBe markerCodec
            collection.codecs.get(classOf[String]) mustBe stringCodec
            collection.codecs.get(classOf[Document]).getEncoderClass mustBe classOf[Document]
            collection.codecs.get(classOf[BigInt]).getEncoderClass mustBe classOf[BigInt]
            db.codecs.get(classOf[Marker]) mustBe markerCodec
          }
        }

        result.unsafeToFuture()(IORuntime.global)
      }

      "prefer explicit collection codecs while retaining the other database codecs" in {
        val databaseCodec   = codec[Marker]
        val inheritedCodec  = codec[OtherMarker]
        val collectionCodec = codec[Marker]
        val result          = MongoDatabase.make[IO](database()).flatMap { initial =>
          val db = initial.withAddedCodec(registry(databaseCodec, inheritedCodec))
          db.getCollection[Marker]("markers", registry(collectionCodec)).map { collection =>
            collection.documentClass mustBe classOf[Marker]
            collection.codecs.get(classOf[Marker]) mustBe collectionCodec
            collection.codecs.get(classOf[OtherMarker]) mustBe inheritedCodec
            collection.codecs.get(classOf[Document]).getEncoderClass mustBe classOf[Document]
            collection.codecs.get(classOf[BigInt]).getEncoderClass mustBe classOf[BigInt]
            db.codecs.get(classOf[Marker]) mustBe databaseCodec
            db.codecs.get(classOf[OtherMarker]) mustBe inheritedCodec
          }
        }

        result.unsafeToFuture()(IORuntime.global)
      }

      "prefer a collection codec provider while preserving inherited codecs and overrides" in {
        val databaseCodec                                       = codec[Marker]
        val inheritedCodec                                      = codec[OtherMarker]
        val collectionCodec                                     = codec[Marker]
        val stringCodec                                         = codec[String]
        implicit val markerProvider: MongoCodecProvider[Marker] = provider(collectionCodec)
        val result = MongoDatabase.make[IO](database(registry(stringCodec))).flatMap { initial =>
          val db = initial.withAddedCodec(registry(databaseCodec, inheritedCodec))
          db.getCollectionWithCodec[Marker]("markers").map { collection =>
            collection.documentClass mustBe classOf[Marker]
            collection.codecs.get(classOf[Marker]) mustBe collectionCodec
            collection.codecs.get(classOf[OtherMarker]) mustBe inheritedCodec
            collection.codecs.get(classOf[String]) mustBe stringCodec
            collection.codecs.get(classOf[Document]).getEncoderClass mustBe classOf[Document]
            collection.codecs.get(classOf[BigInt]).getEncoderClass mustBe classOf[BigInt]
            db.codecs.get(classOf[Marker]) mustBe databaseCodec
            db.codecs.get(classOf[String]) mustBe stringCodec
          }
        }

        result.unsafeToFuture()(IORuntime.global)
      }
    }
  }
}
