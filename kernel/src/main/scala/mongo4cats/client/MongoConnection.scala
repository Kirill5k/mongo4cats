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

sealed abstract class MongoConnectionType(val `type`: String)

object MongoConnectionType {
  case object Classic extends MongoConnectionType("mongodb")

  case object Srv extends MongoConnectionType("mongodb+srv")
}

final case class MongoCredential(username: String, password: String)

/** A data model representation of a MongoDB Connection String
  *
  * @param host
  *   The host that serves MongoDB
  * @param port
  *   Port where the MongoDB is served in the host
  * @param credential
  *   Optional credentials that maybe used to establish authentication with the MongoDB
  * @param connectionType
  *   For switching between different MongoDB connection types, see [[MongoConnectionType]] for possible options
  */
sealed abstract class MongoConnection(
    host: String,
    port: Int,
    credential: Option[MongoCredential],
    connectionType: MongoConnectionType
) {
  override def toString: String = {
    val credentialString = credential.map(cred => s"${cred.username}:${cred.password}@").getOrElse("")
    s"${connectionType.`type`}://$credentialString$host:$port"
  }
}

object MongoConnection {

  import MongoConnectionType.Classic

  def apply(
      host: String,
      port: Int = 27017,
      credential: Option[MongoCredential] = None,
      connectionType: MongoConnectionType = Classic
  ): MongoConnection =
    new MongoConnection(host = host, port = port, credential = credential, connectionType = connectionType) {}
}
