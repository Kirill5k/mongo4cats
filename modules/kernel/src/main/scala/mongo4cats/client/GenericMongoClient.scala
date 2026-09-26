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

package mongo4cats.client

import com.mongodb.connection.ClusterDescription
import com.mongodb.client.model.bulk.ClientBulkWriteResult
import com.mongodb.reactivestreams.client.{MongoClient => JMongoClient}
import mongo4cats.bson.Document
import mongo4cats.models.client.{ClientBulkWriteOptions, ClientSessionOptions, ClientWriteCommand}
import mongo4cats.database.GenericMongoDatabase

abstract class GenericMongoClient[F[_], S[_], R[_]] {
  def underlying: JMongoClient
  def clusterDescription: ClusterDescription = underlying.getClusterDescription
  def getDatabase(name: String): F[GenericMongoDatabase[F, S]]
  def listDatabaseNames: F[Iterable[String]]
  def listDatabases: F[Iterable[Document]]
  def listDatabases(session: ClientSession[F]): F[Iterable[Document]]

  /** Writes to multiple collections and databases in the same cluster. Requires MongoDB 8.0 or later.
    *
    * Commands use the client's codec registry. The driver handles batching and retries; the operation is not automatically atomic. Failures
    * are raised in the effect, preserving [[com.mongodb.ClientBulkWriteException]] and any available partial result.
    */
  def bulkWrite(commands: Seq[ClientWriteCommand], options: ClientBulkWriteOptions): F[ClientBulkWriteResult]
  def bulkWrite(commands: Seq[ClientWriteCommand]): F[ClientBulkWriteResult] = bulkWrite(commands, ClientBulkWriteOptions())
  def bulkWrite(session: ClientSession[F], commands: Seq[ClientWriteCommand], options: ClientBulkWriteOptions): F[ClientBulkWriteResult]
  def bulkWrite(session: ClientSession[F], commands: Seq[ClientWriteCommand]): F[ClientBulkWriteResult] =
    bulkWrite(session, commands, ClientBulkWriteOptions())

  def startSession(options: ClientSessionOptions): R[ClientSession[F]]
  def startSession: R[ClientSession[F]] = startSession(ClientSessionOptions.apply())
}
