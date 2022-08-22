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
import com.mongodb.reactivestreams.client.{MongoClient => JMongoClient}
import mongo4cats.bson.Document
import mongo4cats.database.MongoDatabase

abstract class MongoClient[F[_], S[_]] {
  def underlying: JMongoClient
  def clusterDescription: ClusterDescription = underlying.getClusterDescription
  def getDatabase(name: String): F[MongoDatabase[F, S]]
  def listDatabaseNames: F[Iterable[String]]
  def listDatabases: F[Iterable[Document]]
  def listDatabases(session: ClientSession[F]): F[Iterable[Document]]
  def startSession(options: ClientSessionOptions): F[ClientSession[F]]
  def startSession: F[ClientSession[F]] = startSession(ClientSessionOptions.apply())
}
