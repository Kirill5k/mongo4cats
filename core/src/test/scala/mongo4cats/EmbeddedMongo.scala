package mongo4cats

import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.{MongodConfigBuilder, Net}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network

trait EmbeddedMongo {

  def withRunningEmbeddedMongo[A](host: String = "localhost", port: Int = 12345)(test: => A): A = {
    val starter          = MongodStarter.getDefaultInstance
    val mongodConfig     = new MongodConfigBuilder().version(Version.Main.PRODUCTION).net(new Net(host, port, Network.localhostIsIPv6)).build
    val mongodExecutable = starter.prepare(mongodConfig)
    try {
      val _ = mongodExecutable.start
      test
    } finally mongodExecutable.stop()
  }
}
