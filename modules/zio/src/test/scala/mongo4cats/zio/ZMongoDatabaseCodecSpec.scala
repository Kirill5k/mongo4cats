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

package mongo4cats.zio

import mongo4cats.bson.Document
import mongo4cats.codecs.MongoCodecProvider
import mongo4cats.database.CodecInheritanceFixture._
import zio.Scope
import zio.test._

object ZMongoDatabaseCodecSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ZMongoDatabase collection codecs")(
    test("inherit codecs added to the database when getting a document collection") {
      val markerCodec    = codec[Marker]
      val databaseString = codec[String]

      for {
        original <- ZMongoDatabase.make(database(registry(databaseString)))
        db = original.withAddedCodec(registry(markerCodec))
        collection <- db.getCollection("documents")
      } yield assertTrue(
        db.codecs.get(classOf[Marker]) eq markerCodec,
        collection.codecs.get(classOf[Marker]) eq markerCodec,
        collection.codecs.get(classOf[Document]).getEncoderClass == classOf[Document],
        collection.codecs.get(classOf[String]) eq databaseString
      )
    },
    test("prefer collection codecs while retaining database codecs and leaving the database unchanged") {
      val databaseMarker   = codec[Marker]
      val collectionMarker = codec[Marker]
      val otherMarker      = codec[OtherMarker]
      val collectionString = codec[String]

      for {
        original <- ZMongoDatabase.make(database())
        db = original.withAddedCodec(registry(databaseMarker, otherMarker))
        collection <- db.getCollection[Marker]("markers", registry(collectionMarker, collectionString))
        inherited  <- db.getCollection("documents")
      } yield assertTrue(
        collection.codecs.get(classOf[Marker]) eq collectionMarker,
        collection.codecs.get(classOf[String]) eq collectionString,
        collection.codecs.get(classOf[OtherMarker]) eq otherMarker,
        collection.codecs.get(classOf[Document]).getEncoderClass == classOf[Document],
        db.codecs.get(classOf[Marker]) eq databaseMarker,
        db.codecs.get(classOf[String]) ne collectionString,
        inherited.codecs.get(classOf[Marker]) eq databaseMarker
      )
    },
    test("prefer implicit collection codecs while retaining inherited codecs ahead of defaults") {
      val databaseMarker                                      = codec[Marker]
      val collectionMarker                                    = codec[Marker]
      val otherMarker                                         = codec[OtherMarker]
      val databaseString                                      = codec[String]
      implicit val markerProvider: MongoCodecProvider[Marker] = provider(collectionMarker)

      for {
        original <- ZMongoDatabase.make(database(registry(databaseString)))
        db = original.withAddedCodec(registry(databaseMarker, otherMarker))
        collection <- db.getCollectionWithCodec[Marker]("markers")
      } yield assertTrue(
        collection.codecs.get(classOf[Marker]) eq collectionMarker,
        collection.codecs.get(classOf[OtherMarker]) eq otherMarker,
        collection.codecs.get(classOf[String]) eq databaseString,
        collection.codecs.get(classOf[Document]).getEncoderClass == classOf[Document],
        db.codecs.get(classOf[Marker]) eq databaseMarker,
        db.codecs.get(classOf[String]) eq databaseString
      )
    }
  )
}
