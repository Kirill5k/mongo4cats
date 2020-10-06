package mongo4cats

import cats.effect.{ExitCode, IO, IOApp}
import com.mongodb.ServerAddress
import mongo4cats.client.MongoClientF
import database.codecs._
import org.bson.Document

class Application extends IOApp {

  val mongoAddress = new ServerAddress("localhost", 27017)

  override def run(args: List[String]): IO[ExitCode] =
    MongoClientF.fromServerAddress[IO](mongoAddress).use { client =>
      for {
        db <- client.getDatabase("mydb")
        collection <- db.getCollection[Document]("mycoll")
        count <- collection.count()
      _ <- IO(println(s"$count items in collection"))
      } yield ExitCode.Success
    }
}
