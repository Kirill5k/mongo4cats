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

package mongo4cats.models

import java.net.InetSocketAddress
import com.mongodb.{
  ClientSessionOptions => JClientSessionOptions,
  ConnectionString => JConnectionString,
  MongoClientSettings => JMongoClientSettings,
  MongoDriverInformation => JMongoDriverInformation,
  ReadConcern,
  ReadPreference,
  ServerAddress => JServerAddress,
  TransactionOptions => JTransactionOptions,
  WriteConcern
}
import mongo4cats.codecs.CodecRegistry

package object client {
  type ServerAddress = JServerAddress
  object ServerAddress {
    def apply(host: String, port: Int): ServerAddress    = new JServerAddress(host, port)
    def apply(address: InetSocketAddress): ServerAddress = apply(address.getHostName, address.getPort)
  }

  type ConnectionString = JConnectionString
  object ConnectionString {
    def apply(connectionString: String): ConnectionString = new JConnectionString(connectionString)
  }

  type MongoClientSettings = JMongoClientSettings
  object MongoClientSettings {
    def builderFrom(settings: MongoClientSettings): JMongoClientSettings.Builder = JMongoClientSettings.builder(settings)

    def builder(
        readPreference: ReadPreference = ReadPreference.primary(),
        writeConcern: WriteConcern = WriteConcern.ACKNOWLEDGED,
        retryWrites: Boolean = true,
        retryReads: Boolean = true,
        readConcern: ReadConcern = ReadConcern.DEFAULT,
        codecRegistry: CodecRegistry = CodecRegistry.Default
    ): JMongoClientSettings.Builder = JMongoClientSettings
      .builder()
      .readPreference(readPreference)
      .writeConcern(writeConcern)
      .retryWrites(retryWrites)
      .retryReads(retryReads)
      .readConcern(readConcern)
      .codecRegistry(codecRegistry)
  }

  type MongoDriverInformation = JMongoDriverInformation
  object MongoDriverInformation {
    def apply(): JMongoDriverInformation                                              = builder.build()
    def builder: JMongoDriverInformation.Builder                                      = JMongoDriverInformation.builder()
    def builder(information: MongoDriverInformation): JMongoDriverInformation.Builder = JMongoDriverInformation.builder(information)
  }

  type ClientSessionOptions = JClientSessionOptions
  object ClientSessionOptions {
    def builder: JClientSessionOptions.Builder                                    = JClientSessionOptions.builder()
    def builder(information: ClientSessionOptions): JClientSessionOptions.Builder = JClientSessionOptions.builder(information)

    def apply(
        causallyConsistent: Boolean = true,
        snapshot: Boolean = false
    ): JClientSessionOptions = builder.snapshot(snapshot).causallyConsistent(causallyConsistent).build()
  }

  type TransactionOptions = JTransactionOptions
  object TransactionOptions {
    def apply(): JTransactionOptions         = builder.build()
    def builder: JTransactionOptions.Builder = JTransactionOptions.builder()
  }
}
