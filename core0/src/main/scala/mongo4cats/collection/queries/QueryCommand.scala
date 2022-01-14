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

package mongo4cats.collection.queries

import com.mongodb.client.model
import com.mongodb.client.model.changestream
import org.bson.{BsonDocument, BsonTimestamp}
import org.bson.conversions.Bson

import scala.concurrent.duration.Duration

sealed trait DistinctCommand extends Product with Serializable
sealed trait FindCommand extends Product with Serializable
sealed trait WatchCommand extends Product with Serializable
sealed trait AggregateCommand extends Product with Serializable

object FindCommand {
  final case class ShowRecordId(showRecordId: Boolean) extends FindCommand
  final case class ReturnKey(returnKey: Boolean) extends FindCommand
  final case class Comment(comment: String) extends FindCommand
  final case class Collation(collation: model.Collation) extends FindCommand
  final case class Partial(partial: Boolean) extends FindCommand
  final case class MaxTime(duration: Duration) extends FindCommand
  final case class MaxAwaitTime(duration: Duration) extends FindCommand
  final case class HintString(hint: String) extends FindCommand
  final case class Hint(hin: Bson) extends FindCommand
  final case class Max(index: Bson) extends FindCommand
  final case class Min(index: Bson) extends FindCommand
  final case class Limit(n: Int) extends FindCommand
  final case class Sort(order: Bson) extends FindCommand
  final case class Filter(filter: Bson) extends FindCommand
  final case class Projection(projection: Bson) extends FindCommand
  final case class Skip(skip: Int) extends FindCommand
}

object DistinctCommand {
  final case class MaxTime(duration: Duration) extends DistinctCommand
  final case class Filter(filter: Bson) extends DistinctCommand
  final case class BatchSize(size: Int) extends DistinctCommand
  final case class Collation(collation: model.Collation) extends DistinctCommand
}

object WatchCommand {
  final case class BatchSize(size: Int) extends WatchCommand
  final case class Collation(collation: model.Collation) extends WatchCommand
  final case class FullDocument(fullDocument: changestream.FullDocument) extends WatchCommand
  final case class MaxAwaitTime(duration: Duration) extends WatchCommand
  final case class ResumeAfter(after: BsonDocument) extends WatchCommand
  final case class StartAfter(after: BsonDocument) extends WatchCommand
  final case class StartAtOperationTime(operationTime: BsonTimestamp) extends WatchCommand
}

object AggregateCommand {
  final case class AllowDiskUse(allowDiskUse: Boolean) extends AggregateCommand
  final case class MaxTime(duration: Duration) extends AggregateCommand
  final case class MaxAwaitTime(duration: Duration) extends AggregateCommand
  final case class BypassDocumentValidation(bypassDocumentValidation: Boolean)
      extends AggregateCommand
  final case class Collation(collation: model.Collation) extends AggregateCommand
  final case class Comment(comment: String) extends AggregateCommand
  final case class Let(varibles: Bson) extends AggregateCommand
  final case class Hint(hint: Bson) extends AggregateCommand
  final case class BatchSize(batchSize: Int) extends AggregateCommand
}
