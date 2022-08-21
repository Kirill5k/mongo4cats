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
  UpdateOptions => JUpdateOptions
}

package object collection {

  type BulkWriteOptions = JBulkWriteOptions
  object BulkWriteOptions {
    def apply(
        ordered: Boolean = true,
        bypassDocumentValidation: Boolean = false
    ): BulkWriteOptions = new JBulkWriteOptions().ordered(ordered).bypassDocumentValidation(bypassDocumentValidation)
  }

  type RenameCollectionOptions = JRenameCollectionOptions
  object RenameCollectionOptions {
    def apply(dropTarget: Boolean = false): RenameCollectionOptions = new JRenameCollectionOptions().dropTarget(dropTarget)
  }

  type IndexOptions = JIndexOptions
  object IndexOptions {
    def apply(): IndexOptions = new JIndexOptions()
  }

  type UpdateOptions = JUpdateOptions
  object UpdateOptions {
    def apply(): UpdateOptions = new JUpdateOptions()
  }

  type ReplaceOptions = JReplaceOptions
  object ReplaceOptions {
    def apply(): ReplaceOptions = new JReplaceOptions()
  }

  type DropIndexOptions = JDropIndexOptions
  object DropIndexOptions {
    def apply(): DropIndexOptions = new JDropIndexOptions()
  }

  type FindOneAndReplaceOptions = JFindOneAndReplaceOptions
  object FindOneAndReplaceOptions {
    def apply(): FindOneAndReplaceOptions = new JFindOneAndReplaceOptions()
  }

  type DeleteOptions = JDeleteOptions
  object DeleteOptions {
    def apply(): DeleteOptions = new JDeleteOptions()
  }

  type CountOptions = JCountOptions
  object CountOptions {
    def apply(): CountOptions = new JCountOptions()
  }

  type InsertManyOptions = JInsertManyOptions
  object InsertManyOptions {
    def apply(): InsertManyOptions = new JInsertManyOptions()
  }

  type InsertOneOptions = JInsertOneOptions
  object InsertOneOptions {
    def apply(): InsertOneOptions = new JInsertOneOptions()
  }

  type FindOneAndUpdateOptions = JFindOneAndUpdateOptions
  object FindOneAndUpdateOptions {
    def apply(): FindOneAndUpdateOptions = new JFindOneAndUpdateOptions()
  }

  type FindOneAndDeleteOptions = JFindOneAndDeleteOptions
  object FindOneAndDeleteOptions {
    def apply(): FindOneAndDeleteOptions = new JFindOneAndDeleteOptions()
  }
}
