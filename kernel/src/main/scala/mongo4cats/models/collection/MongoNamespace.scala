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

package mongo4cats.models.collection

import com.mongodb.{MongoNamespace => JMongoNamespace}

final case class MongoNamespace(databaseName: String, collectionName: String) {
  private[mongo4cats] def toJava = new JMongoNamespace(databaseName, collectionName)
}

object MongoNamespace {
  private[mongo4cats] def fromJava(namespace: JMongoNamespace): MongoNamespace =
    MongoNamespace(namespace.getDatabaseName, namespace.getCollectionName)
}
