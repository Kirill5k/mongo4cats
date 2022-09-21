package mongo4cats.bench

import cats.effect.IO
import de.flapdoodle.embed.mongo.config.Defaults.{downloadConfigFor, extractedArtifactStoreFor, runtimeConfigFor}
import de.flapdoodle.embed.mongo.config.{MongodConfig, Net, Storage}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.packageresolver.Command.MongoD
import de.flapdoodle.embed.mongo.{MongodProcess, MongodStarter}
import de.flapdoodle.embed.process.extract.{DirectoryAndExecutableNaming, NoopTempNaming, UUIDTempNaming}
import de.flapdoodle.embed.process.io.directories.FixedPath
import de.flapdoodle.embed.process.runtime.Network
import io.circe.generic.auto._
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

trait BaseCollectionBench { self =>

  val mongoPort = 57057

  var mongoProcess: MongodProcess                                       = _
  var client: MongoClient[IO]                                           = _
  var releaseClient: IO[Unit]                                           = _
  var db: GenericMongoDatabase[IO, fs2.Stream[IO, *]]                   = _
  var circeColl: GenericMongoCollection[IO, BenchCC, fs2.Stream[IO, *]] = _
  var bsonColl: GenericMongoCollection[IO, BenchCC, fs2.Stream[IO, *]]  = _

  val benchDB   = "bench-db"
  val benchColl = "bench-coll"
  val tmpDir    = "/tmp/big/mongo4cats.tmp"
  val shmDir    = new File("/dev/shm/mongo4cats")

  def setup(): Unit = {
    implicit val bsonEncoder: BsonEncoder[BenchCC] = DerivationWriteBench.bsonEncoder
    implicit val bsonDecoder: BsonDecoder[BenchCC] = DerivationReadBench.bsonDecoder

    val instanceDir = s"$tmpDir/instances/${Instant.now}"
    // val instanceDir = s"$shmDirFile/instances/${Instant.now}"

    (for {
      _ <- IO.blocking(
        if (shmDir.exists()) Files.walk(shmDir.toPath).sorted(reverseOrder[Path]()).map(_.toFile).forEach { f =>
          f.delete()
          ()
        }
      )

      _mongoProcess <- IO.blocking(
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
                      .artifactStorePath(new FixedPath(s"$tmpDir/embeddedMongodbStore"))
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
                      .directory(new FixedPath(s"$tmpDir/tmp"))
                      .executableNaming(new UUIDTempNaming())
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
              .replication(new Storage(instanceDir, null, 0 /*MegaByte*/ ))
              .build
          )
          .start()
      )
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
      circeColl <- db.getCollectionWithCodec[BenchCC](benchColl)
      _ = self.circeColl = circeColl

      // --- BsonValue ---
      bsonColl <- db.fastCollection[BenchCC](benchColl)
      _ = self.bsonColl = bsonColl
    } yield ()).unsafeRunSync()(cats.effect.unsafe.IORuntime.global)
  }

  @TearDown
  def tearDown(): Unit = (for {
    _ <- releaseClient
    _ <- IO.blocking(mongoProcess.stop())
  } yield ()).unsafeRunSync()(cats.effect.unsafe.IORuntime.global)
}
