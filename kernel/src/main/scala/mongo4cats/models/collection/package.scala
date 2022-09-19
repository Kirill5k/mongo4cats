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

import com.mongodb.client.model.{
  BulkWriteOptions => JBulkWriteOptions,
  CountOptions => JCountOptions,
  DeleteOptions => JDeleteOptions,
  DropIndexOptions => JDropIndexOptions,
  FindOneAndDeleteOptions => JFindOneAndDeleteOptions,
  FindOneAndReplaceOptions => JFindOneAndReplaceOptions,
  FindOneAndUpdateOptions => JFindOneAndUpdateOptions,
  IndexOptions => JIndexOptions,
  InsertManyOptions => JInsertManyOptions,
  InsertOneOptions => JInsertOneOptions,
  RenameCollectionOptions => JRenameCollectionOptions,
  ReplaceOptions => JReplaceOptions,
  ReturnDocument,
  UpdateOptions => JUpdateOptions
}
import mongo4cats.operations.Sort

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}

package object collection {

  type BulkWriteOptions = JBulkWriteOptions
  object BulkWriteOptions {
    def apply(
        ordered: Boolean = true,
        bypassDocumentValidation: Boolean = false,
        comment: Option[String] = None
    ): BulkWriteOptions =
      new JBulkWriteOptions().ordered(ordered).bypassDocumentValidation(bypassDocumentValidation).comment(comment.orNull)
  }

  type RenameCollectionOptions = JRenameCollectionOptions
  object RenameCollectionOptions {
    def apply(dropTarget: Boolean = false): RenameCollectionOptions = new JRenameCollectionOptions().dropTarget(dropTarget)
  }

  type IndexOptions = JIndexOptions
  object IndexOptions {
    def apply(
        background: Boolean = false,
        unique: Boolean = false,
        sparse: Boolean = false,
        hidden: Boolean = false
    ): IndexOptions = new JIndexOptions().background(background).unique(unique).sparse(sparse).hidden(hidden)
  }

  type UpdateOptions = JUpdateOptions
  object UpdateOptions {
    def apply(
        upsert: Boolean = false,
        bypassDocumentValidation: Boolean = false,
        comment: Option[String] = None
    ): UpdateOptions = new JUpdateOptions().upsert(upsert).bypassDocumentValidation(bypassDocumentValidation).comment(comment.orNull)
  }

  type ReplaceOptions = JReplaceOptions
  object ReplaceOptions {
    def apply(
        upsert: Boolean = false,
        bypassDocumentValidation: Boolean = false,
        comment: Option[String] = None
    ): ReplaceOptions = new JReplaceOptions().upsert(upsert).bypassDocumentValidation(bypassDocumentValidation).comment(comment.orNull)
  }

  type DropIndexOptions = JDropIndexOptions
  object DropIndexOptions {
    def apply(maxTime: FiniteDuration = Duration.Zero): DropIndexOptions =
      new JDropIndexOptions().maxTime(maxTime.toMillis, TimeUnit.MILLISECONDS)
  }

  type FindOneAndReplaceOptions = JFindOneAndReplaceOptions
  object FindOneAndReplaceOptions {
    def apply(
        upsert: Boolean = false,
        maxTime: FiniteDuration = Duration.Zero,
        returnDocument: ReturnDocument = ReturnDocument.BEFORE,
        bypassDocumentValidation: Boolean = false,
        sort: Option[Sort] = None,
        comment: Option[String] = None
    ): FindOneAndReplaceOptions = new JFindOneAndReplaceOptions()
      .upsert(upsert)
      .maxTime(maxTime.toMillis, TimeUnit.MILLISECONDS)
      .returnDocument(returnDocument)
      .bypassDocumentValidation(bypassDocumentValidation)
      .comment(comment.orNull)
      .sort(sort.map(_.toBson).orNull)
  }

  type DeleteOptions = JDeleteOptions
  object DeleteOptions {
    def apply(comment: Option[String] = None): DeleteOptions = new JDeleteOptions().comment(comment.orNull)
  }

  type CountOptions = JCountOptions
  object CountOptions {
    def apply(
        limit: Int = 0,
        skip: Int = 0,
        maxTime: FiniteDuration = Duration.Zero,
        comment: Option[String] = None
    ): CountOptions = new JCountOptions().limit(limit).skip(skip).maxTime(maxTime.toMillis, TimeUnit.MILLISECONDS).comment(comment.orNull)
  }

  type InsertManyOptions = JInsertManyOptions
  object InsertManyOptions {
    def apply(
        ordered: Boolean = true,
        bypassDocumentValidation: Boolean = false,
        comment: Option[String] = None
    ): InsertManyOptions =
      new JInsertManyOptions().ordered(ordered).bypassDocumentValidation(bypassDocumentValidation).comment(comment.orNull)
  }

  type InsertOneOptions = JInsertOneOptions
  object InsertOneOptions {
    def apply(bypassDocumentValidation: Boolean = false, comment: Option[String] = None): InsertOneOptions =
      new JInsertOneOptions().bypassDocumentValidation(bypassDocumentValidation).comment(comment.orNull)
  }

  type FindOneAndUpdateOptions = JFindOneAndUpdateOptions
  object FindOneAndUpdateOptions {
    def apply(
        upsert: Boolean = false,
        returnDocument: ReturnDocument = ReturnDocument.BEFORE,
        bypassDocumentValidation: Boolean = false,
        maxTime: FiniteDuration = Duration.Zero,
        sort: Option[Sort] = None,
        comment: Option[String] = None
    ): FindOneAndUpdateOptions =
      new JFindOneAndUpdateOptions()
        .upsert(upsert)
        .returnDocument(returnDocument)
        .bypassDocumentValidation(bypassDocumentValidation)
        .maxTime(maxTime.toMillis, TimeUnit.MILLISECONDS)
        .sort(sort.map(_.toBson).orNull)
        .comment(comment.orNull)
  }

  type FindOneAndDeleteOptions = JFindOneAndDeleteOptions
  object FindOneAndDeleteOptions {
    def apply(
        maxTime: FiniteDuration = Duration.Zero,
        sort: Option[Sort] = None,
        comment: Option[String] = None
    ): FindOneAndDeleteOptions =
      new JFindOneAndDeleteOptions()
        .comment(comment.orNull)
        .maxTime(maxTime.toMillis, TimeUnit.MILLISECONDS)
        .sort(sort.map(_.toBson).orNull)
  }
}
