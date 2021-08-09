package mongo4cats.client

import com.mongodb.{
  MongoDriverInformation => JMongoDriverInformation,
  MongoClientSettings => JMongoClientSettings,
  ServerAddress => JServerAddress
}

import java.net.InetSocketAddress

object settings {

  type ServerAddress = JServerAddress
  object ServerAddress {
    def apply(host: String, port: Int): ServerAddress    = new JServerAddress(host, port)
    def apply(address: InetSocketAddress): ServerAddress = apply(address.getHostName, address.getPort)
  }

  type MongoClientSettings = JMongoClientSettings
  object MongoClientSettings {
    def builder: JMongoClientSettings.Builder                                = JMongoClientSettings.builder()
    def builder(settings: MongoClientSettings): JMongoClientSettings.Builder = JMongoClientSettings.builder(settings)
  }

  type MongoDriverInformation = JMongoDriverInformation
  object MongoDriverInformation {
    def builder: JMongoDriverInformation.Builder                                      = JMongoDriverInformation.builder()
    def builder(information: MongoDriverInformation): JMongoDriverInformation.Builder = JMongoDriverInformation.builder(information)
  }
}
