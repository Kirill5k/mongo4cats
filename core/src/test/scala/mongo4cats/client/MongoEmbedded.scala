package mongo4cats.client

import com.mongodb.BasicDBObject
import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodProcess
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.{IMongodConfig, MongodConfigBuilder, Net}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network

trait MongoEmbedded {

  def withRunningMongoEmbedded(host: String = "localhost", port: Int = 12345)(test: => Unit): Unit = {
    val starter = MongodStarter.getDefaultInstance
    val mongodConfig = new MongodConfigBuilder().version(Version.Main.PRODUCTION).net(new Net(host, port, Network.localhostIsIPv6)).build
    val mongodExecutable = starter.prepare(mongodConfig)
    try {
      val mongod = mongodExecutable.start
      test
    } finally mongodExecutable.stop()
  }
}
