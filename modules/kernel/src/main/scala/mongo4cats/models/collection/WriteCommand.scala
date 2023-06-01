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

import com.mongodb.client.model.{
  DeleteManyModel,
  DeleteOneModel,
  InsertOneModel,
  ReplaceOneModel,
  UpdateManyModel,
  UpdateOneModel,
  WriteModel
}
import mongo4cats.AsJava
import mongo4cats.operations.{Filter, Update}
import org.bson.conversions.Bson

sealed trait WriteCommand[+T] {
  private[mongo4cats] def writeModel[T1 >: T]: WriteModel[T1]
}

object WriteCommand {
  final case class DeleteMany(filter: Filter, options: DeleteOptions = DeleteOptions()) extends WriteCommand[Nothing] {
    private[mongo4cats] def writeModel[T1 >: Nothing]: WriteModel[T1] =
      new DeleteManyModel[T1](filter.toBson, options)
  }

  final case class DeleteOne(filter: Filter, options: DeleteOptions = DeleteOptions()) extends WriteCommand[Nothing] {
    private[mongo4cats] def writeModel[T1 >: Nothing]: WriteModel[T1] =
      new DeleteOneModel[T1](filter.toBson, options)
  }

  final case class InsertOne[T](document: T) extends WriteCommand[T] {
    private[mongo4cats] def writeModel[T1 >: T]: WriteModel[T1] =
      new InsertOneModel[T1](document)
  }

  final case class ReplaceOne[T](filter: Filter, replacement: T, options: ReplaceOptions = ReplaceOptions()) extends WriteCommand[T] {
    private[mongo4cats] def writeModel[T1 >: T]: WriteModel[T1] =
      new ReplaceOneModel[T1](filter.toBson, replacement, options)
  }

  final case class UpdateMany(filter: Filter, update: Update, options: UpdateOptions = UpdateOptions()) extends WriteCommand[Nothing] {
    override private[mongo4cats] def writeModel[T1 >: Nothing]: WriteModel[T1] =
      new UpdateManyModel[T1](filter.toBson, update.toBson, options)
  }

  final case class PipelinedUpdateMany(filter: Filter, update: Seq[Bson], options: UpdateOptions = UpdateOptions())
      extends WriteCommand[Nothing] with AsJava {
    override private[mongo4cats] def writeModel[T1 >: Nothing]: WriteModel[T1] =
      new UpdateManyModel[T1](filter.toBson, asJava(update), options)
  }

  final case class UpdateOne(filter: Filter, update: Update, options: UpdateOptions = UpdateOptions()) extends WriteCommand[Nothing] {
    override private[mongo4cats] def writeModel[T1 >: Nothing]: WriteModel[T1] =
      new UpdateOneModel[T1](filter.toBson, update.toBson, options)
  }

  final case class PipelinedUpdateOne(filter: Filter, update: Seq[Bson], options: UpdateOptions = UpdateOptions())
      extends WriteCommand[Nothing] with AsJava {
    override private[mongo4cats] def writeModel[T1 >: Nothing]: WriteModel[T1] =
      new UpdateOneModel[T1](filter.toBson, asJava(update), options)
  }
}
