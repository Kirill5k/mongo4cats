package mongo4cats.codecs

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import mongo4cats.TestData
import mongo4cats.bson.Document
import mongo4cats.client.MongoClient
import mongo4cats.collection.operations.{Filter, Update}
import mongo4cats.database.MongoDatabase
import mongo4cats.embedded.EmbeddedMongo
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AsyncWordSpec

import scala.concurrent.Future

class CodecRegistrySpec extends AsyncWordSpec with Matchers with EmbeddedMongo {

  override val mongoPort: Int = 12349

  "A CodecRegistry" should {

    "be able to handle scala option" in {
      withEmbeddedMongoDatabase { db =>
        val result = for {
          coll <- db.getCollection("coll")
          _    <- coll.insertOne(TestData.gbpAccount)
          _    <- coll.updateMany(Filter.empty, Update.set("currency", None).set("name", Some("updated-acc")))
          doc  <- coll.find.first
        } yield doc

        result.map { doc =>
          doc.flatMap(_.getString("currency")) mustBe None
          doc.flatMap(_.getString("name")) mustBe Some("updated-acc")
        }
      }
    }

    "be able to handle scala map" in {
      withEmbeddedMongoDatabase { db =>
        val result = for {
          coll <- db.getCollection("coll")
          _    <- coll.insertOne(TestData.gbpAccount)
          _    <- coll.updateMany(Filter.empty, Update.set("props", Map("a" -> 42, "b" -> "foo")))
          doc  <- coll.find.first
        } yield doc

        result.map { doc =>
          doc.flatMap(_.getDocument("props")) mustBe Some(Document("a" -> 42, "b" -> "foo"))
        }
      }
    }

    "be able to handle scala iterables" in {
      withEmbeddedMongoDatabase { db =>
        val result = for {
          coll <- db.getCollection("coll")
          _    <- coll.insertOne(TestData.gbpAccount)
          _    <- coll.updateMany(Filter.empty, Update.set("tags", List("foo", "bar", 42)))
          doc  <- coll.find.first
        } yield doc

        result.map { doc =>
          val tags = doc.flatMap(_.getList("tags"))
          tags mustBe Some(List("foo", "bar", 42))
        }
      }
    }

    "be able to handle my document" in {
      withEmbeddedMongoDatabase { db =>
        val result = for {
          coll <- db.getCollectionWithCodec[Document]("coll")
          _    <- coll.insertOne(Document("foo" -> "bar", "tags" -> List("my", "doc")))
          doc  <- coll.find.first
        } yield doc.get

        result.map { doc =>
          doc.getString("foo") mustBe Some("bar")
          doc.get[Iterable[Any]]("tags") mustBe Some(List("my", "doc"))
          doc.getObjectId("_id") mustBe defined
        }
      }
    }
  }

  def withEmbeddedMongoDatabase[A](test: MongoDatabase[IO] => IO[A]): Future[A] =
    withRunningEmbeddedMongo {
      MongoClient
        .fromConnectionString[IO](s"mongodb://localhost:$mongoPort")
        .use { client =>
          for {
            db  <- client.getDatabase("db")
            res <- test(db)
          } yield res
        }
    }.unsafeToFuture()(IORuntime.global)
}
