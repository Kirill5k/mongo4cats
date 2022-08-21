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
import org.bson.conversions.Bson
import org.bson.{BsonDocument, BsonTimestamp}

import scala.concurrent.duration.Duration

sealed private[mongo4cats] trait QueryCommand

private[mongo4cats] object QueryCommand {
  final case class ShowRecordId(showRecordId: Boolean)                         extends QueryCommand
  final case class ReturnKey(returnKey: Boolean)                               extends QueryCommand
  final case class Comment(comment: String)                                    extends QueryCommand
  final case class Collation(collation: model.Collation)                       extends QueryCommand
  final case class Partial(partial: Boolean)                                   extends QueryCommand
  final case class MaxTime(duration: Duration)                                 extends QueryCommand
  final case class MaxAwaitTime(duration: Duration)                            extends QueryCommand
  final case class HintString(hint: String)                                    extends QueryCommand
  final case class Hint(hint: Bson)                                            extends QueryCommand
  final case class Max(index: Bson)                                            extends QueryCommand
  final case class Min(index: Bson)                                            extends QueryCommand
  final case class Skip(n: Int)                                                extends QueryCommand
  final case class Limit(n: Int)                                               extends QueryCommand
  final case class Sort(order: Bson)                                           extends QueryCommand
  final case class Filter(filter: Bson)                                        extends QueryCommand
  final case class Projection(projection: Bson)                                extends QueryCommand
  final case class BatchSize(size: Int)                                        extends QueryCommand
  final case class FullDocument(fullDocument: changestream.FullDocument)       extends QueryCommand
  final case class ResumeAfter(after: BsonDocument)                            extends QueryCommand
  final case class StartAfter(after: BsonDocument)                             extends QueryCommand
  final case class StartAtOperationTime(operationTime: BsonTimestamp)          extends QueryCommand
  final case class AllowDiskUse(allowDiskUse: Boolean)                         extends QueryCommand
  final case class BypassDocumentValidation(bypassDocumentValidation: Boolean) extends QueryCommand
  final case class Let(variables: Bson)                                        extends QueryCommand
}
