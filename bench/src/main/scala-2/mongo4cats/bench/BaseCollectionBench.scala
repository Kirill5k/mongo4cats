package mongo4cats.bench

import cats.syntax.all._
import cats.effect.IO
import de.flapdoodle.embed.mongo.config.Defaults.{downloadConfigFor, extractedArtifactStoreFor, runtimeConfigFor}
import de.flapdoodle.embed.mongo.config.{MongodConfig, Net, Storage}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.packageresolver.Command.MongoD
import de.flapdoodle.embed.mongo.{MongodProcess, MongodStarter}
import de.flapdoodle.embed.process.extract.{DirectoryAndExecutableNaming, NoopTempNaming, UUIDTempNaming, UserTempNaming}
import de.flapdoodle.embed.process.io.directories.FixedPath
import de.flapdoodle.embed.process.runtime.Network
import io.circe.generic.auto._
import mongo4cats.bench.BenchData.data
import mongo4cats.circe._
import mongo4cats.client.MongoClient
import mongo4cats.collection.GenericMongoCollection
import mongo4cats.database.GenericMongoDatabase
import mongo4cats.derivation.bson.{BsonDecoder, BsonEncoder, _}
import org.openjdk.jmh.annotations.TearDown
import org.slf4j.LoggerFactory
import org.slf4j.helpers.NOPLogger.NOP_LOGGER

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import java.util.Comparator
import java.util.Comparator.reverseOrder
import scala.sys.process._

trait BaseCollectionBench { self =>

  val mongoPort = 57057

  var mongoProcess: MongodProcess                                         = _
  var client: MongoClient[IO]                                             = _
  var releaseClient: IO[Unit]                                             = _
  var db: GenericMongoDatabase[IO, fs2.Stream[IO, *]]                     = _
  var circeColl: GenericMongoCollection[IO, BenchData, fs2.Stream[IO, *]] = _
  var bsonColl: GenericMongoCollection[IO, BenchData, fs2.Stream[IO, *]]  = _

  val benchDB     = "bench-db"
  val benchColl   = "bench-coll"
  val tmpDir      = "/tmp/big/mongo4cats.tmp"
  val baseDataDir = s"$tmpDir/data"
  val shmDir      = "/dev/shm/mongo4cats"

  def deleteDir(dirPath: String): IO[Unit] = {
    val dir = new File(dirPath)
    IO.blocking(if (dir.exists()) Files.walk(dir.toPath).sorted(reverseOrder[Path]()).map(_.toFile).forEach { f => f.delete(); () })
  }

  def setup(): Unit = {
    implicit val bsonEncoder: BsonEncoder[BenchData] = DerivationWriteBench.bsonEncoder
    implicit val bsonDecoder: BsonDecoder[BenchData] = DerivationReadBench.bsonDecoder

    val dataDir = s"$baseDataDir/${Instant.now}"
    // val instanceDir = s"$shmDirFile/data/${Instant.now}"

    (for {
      _ <- deleteDir(shmDir)

      _mongoProcess <- IO
        .blocking(
          MongodStarter
            .getInstance(
              runtimeConfigFor(
                MongoD,
                // LoggerFactory.getLogger(classOf[BaseCollectionBench])
                NOP_LOGGER
              )
                .artifactStore(
                  extractedArtifactStoreFor(MongoD)
                    .withDownloadConfig(
                      downloadConfigFor(MongoD)
                        .artifactStorePath(new FixedPath(s"$tmpDir/downloadedMongoArchive"))
                        .build()
                    )
                    .withExtraction(
                      DirectoryAndExecutableNaming
                        .builder()
                        .directory(new FixedPath(s"$tmpDir/extracted"))
                        .executableNaming(new NoopTempNaming())
                        .build()
                    )
                    .withTemp(
                      DirectoryAndExecutableNaming
                        .builder()
                        .directory(new FixedPath(s"$tmpDir/exec"))
                        .executableNaming(new NoopTempNaming())
                        .build()
                    )
                )
                // .processOutput(new ProcessOutput(processOutput("OUT", FULL_MONGOD_LOG), processOutput("ERROR"), processOutput("CMD")))
                .build()
            )
            .prepare(
              MongodConfig.builder
                .version(Version.Main.PRODUCTION)
                .net(new Net("localhost", mongoPort, Network.localhostIsIPv6))
                .replication(new Storage(dataDir, null, 0 /*MegaByte*/ ))
                .build
            )
            .start()
        )
        .attemptTap {
          case Left(ex) =>
            IO(ex.printStackTrace()) *>
              IO(Seq("pkill", "-9", "-f", "extractmongod").!)
          case Right(value) => IO.unit
        }

      _ = self.mongoProcess = _mongoProcess

      client_releaseClient <- MongoClient.fromConnectionString[IO](s"mongodb://localhost:$mongoPort").allocated
      _ = self.client = client_releaseClient._1
      _ = self.releaseClient = client_releaseClient._2

      // --- Circe ---
      db <- client.getDatabase(benchDB)
      _ = self.db = db
      // _ = println(s"""|Write Concern: ${db.writeConcern}
      //      |Read  Concern: ${db.readConcern}
      //      |""".stripMargin)

      _         <- db.createCollection(benchColl)
      circeColl <- db.getCollectionWithCodec[BenchData](benchColl)
      _ = self.circeColl = circeColl

      // --- BsonValue ---
      bsonColl <- db.fastCollection[BenchData](benchColl)
      _ = self.bsonColl = bsonColl
    } yield ()).unsafeRunSync()(cats.effect.unsafe.IORuntime.global)
  }

  @TearDown
  def tearDown(): Unit = (for {
    _ <- releaseClient
    _ <- IO.blocking(mongoProcess.stop())
    _ <- deleteDir(baseDataDir)
  } yield ()).unsafeRunSync()(cats.effect.unsafe.IORuntime.global)
}
