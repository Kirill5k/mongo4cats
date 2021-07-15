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

package mongo4cats

import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.{MongodConfig, Net}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network

object EmbeddedMongo {
  private val starter = MongodStarter.getDefaultInstance

  def prepare(config: MongodConfig, attempt: Int = 5): MongodExecutable =
    if (attempt < 0) throw new RuntimeException("tried to prepare executable far too many times")
    else Try(starter.prepare(config)).getOrElse(prepare(config, attempt - 1))
}

trait EmbeddedMongo {

  def withRunningEmbeddedMongo[A](host: String = "localhost", port: Int = 12345)(test: => A): A = {
    val starter = MongodStarter.getDefaultInstance
    val mongodConfig = MongodConfig
      .builder()
      .version(Version.Main.PRODUCTION)
      .net(new Net(host, port, Network.localhostIsIPv6))
      .build
    val mongodExecutable = EmbeddedMongo.prepare(mongodConfig)
    try {
      val _ = mongodExecutable.start
      test
    } finally mongodExecutable.stop()
  }
}
