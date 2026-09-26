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

package mongo4cats.models.client

import com.mongodb.client.model.bulk.ClientNamespacedWriteModel
import mongo4cats.AsJava
import mongo4cats.models.collection.MongoNamespace
import mongo4cats.operations.{Filter, Update}
import org.bson.conversions.Bson

/** A write targeting a collection in a client-level bulk write. A batch may contain different document types; their codecs must be
  * registered in the client's codec registry.
  */
sealed trait ClientWriteCommand {
  def namespace: MongoNamespace
  private[mongo4cats] def writeModel: ClientNamespacedWriteModel
}

object ClientWriteCommand {
  final case class InsertOne[T](namespace: MongoNamespace, document: T) extends ClientWriteCommand {
    private[mongo4cats] def writeModel: ClientNamespacedWriteModel =
      ClientNamespacedWriteModel.insertOne(namespace.toJava, document)
  }

  final case class ReplaceOne[T](
      namespace: MongoNamespace,
      filter: Filter,
      replacement: T,
      options: ClientReplaceOneOptions = ClientReplaceOneOptions()
  ) extends ClientWriteCommand {
    private[mongo4cats] def writeModel: ClientNamespacedWriteModel =
      ClientNamespacedWriteModel.replaceOne(namespace.toJava, filter.toBson, replacement, options)
  }

  final case class UpdateOne(
      namespace: MongoNamespace,
      filter: Filter,
      update: Update,
      options: ClientUpdateOneOptions = ClientUpdateOneOptions()
  ) extends ClientWriteCommand {
    private[mongo4cats] def writeModel: ClientNamespacedWriteModel =
      ClientNamespacedWriteModel.updateOne(namespace.toJava, filter.toBson, update.toBson, options)
  }

  final case class PipelinedUpdateOne(
      namespace: MongoNamespace,
      filter: Filter,
      update: Seq[Bson],
      options: ClientUpdateOneOptions = ClientUpdateOneOptions()
  ) extends ClientWriteCommand with AsJava {
    private[mongo4cats] def writeModel: ClientNamespacedWriteModel =
      ClientNamespacedWriteModel.updateOne(namespace.toJava, filter.toBson, asJava(update), options)
  }

  final case class UpdateMany(
      namespace: MongoNamespace,
      filter: Filter,
      update: Update,
      options: ClientUpdateManyOptions = ClientUpdateManyOptions()
  ) extends ClientWriteCommand {
    private[mongo4cats] def writeModel: ClientNamespacedWriteModel =
      ClientNamespacedWriteModel.updateMany(namespace.toJava, filter.toBson, update.toBson, options)
  }

  final case class PipelinedUpdateMany(
      namespace: MongoNamespace,
      filter: Filter,
      update: Seq[Bson],
      options: ClientUpdateManyOptions = ClientUpdateManyOptions()
  ) extends ClientWriteCommand with AsJava {
    private[mongo4cats] def writeModel: ClientNamespacedWriteModel =
      ClientNamespacedWriteModel.updateMany(namespace.toJava, filter.toBson, asJava(update), options)
  }

  final case class DeleteOne(
      namespace: MongoNamespace,
      filter: Filter,
      options: ClientDeleteOneOptions = ClientDeleteOneOptions()
  ) extends ClientWriteCommand {
    private[mongo4cats] def writeModel: ClientNamespacedWriteModel =
      ClientNamespacedWriteModel.deleteOne(namespace.toJava, filter.toBson, options)
  }

  final case class DeleteMany(
      namespace: MongoNamespace,
      filter: Filter,
      options: ClientDeleteManyOptions = ClientDeleteManyOptions()
  ) extends ClientWriteCommand {
    private[mongo4cats] def writeModel: ClientNamespacedWriteModel =
      ClientNamespacedWriteModel.deleteMany(namespace.toJava, filter.toBson, options)
  }
}
