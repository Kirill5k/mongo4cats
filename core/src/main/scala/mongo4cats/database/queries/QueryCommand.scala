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

package mongo4cats.database.queries

import com.mongodb.client.model
import com.mongodb.client.model.changestream
import com.mongodb.reactivestreams.client.{AggregatePublisher, ChangeStreamPublisher, DistinctPublisher, FindPublisher}
import org.bson.{BsonDocument, BsonTimestamp}
import org.bson.conversions.Bson
import org.reactivestreams.Publisher

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

sealed private[queries] trait QueryCommand[O[_] <: Publisher[_], T] {
  def run(observable: O[T]): O[T]
}

sealed private[queries] trait DistinctCommand[T]  extends QueryCommand[DistinctPublisher, T]
sealed private[queries] trait FindCommand[T]      extends QueryCommand[FindPublisher, T]
sealed private[queries] trait WatchCommand[T]     extends QueryCommand[ChangeStreamPublisher, T]
sealed private[queries] trait AggregateCommand[T] extends QueryCommand[AggregatePublisher, T]

private[queries] object FindCommand {
  final case class Limit[T](n: Int) extends FindCommand[T] {
    override def run(observable: FindPublisher[T]): FindPublisher[T] =
      observable.limit(n)
  }

  final case class Sort[T](order: Bson) extends FindCommand[T] {
    override def run(observable: FindPublisher[T]): FindPublisher[T] =
      observable.sort(order)
  }

  final case class Filter[T](filter: Bson) extends FindCommand[T] {
    override def run(observable: FindPublisher[T]): FindPublisher[T] =
      observable.filter(filter)
  }

  final case class Projection[T](projection: Bson) extends FindCommand[T] {
    override def run(observable: FindPublisher[T]): FindPublisher[T] =
      observable.projection(projection)
  }
}

private[queries] object DistinctCommand {
  final case class Filter[T](filter: Bson) extends DistinctCommand[T] {
    override def run(observable: DistinctPublisher[T]): DistinctPublisher[T] =
      observable.filter(filter)
  }

  final case class BatchSize[T](size: Int) extends DistinctCommand[T] {
    override def run(observable: DistinctPublisher[T]): DistinctPublisher[T] =
      observable.batchSize(size)
  }

  final case class Collation[T](collation: model.Collation) extends DistinctCommand[T] {
    override def run(observable: DistinctPublisher[T]): DistinctPublisher[T] =
      observable.collation(collation)
  }
}

private[queries] object WatchCommand {
  final case class BatchSize[T](size: Int) extends WatchCommand[T] {
    override def run(observable: ChangeStreamPublisher[T]): ChangeStreamPublisher[T] =
      observable.batchSize(size)
  }

  final case class Collation[T](collation: model.Collation) extends WatchCommand[T] {
    override def run(observable: ChangeStreamPublisher[T]): ChangeStreamPublisher[T] =
      observable.collation(collation)
  }

  final case class FullDocument[T](fullDocument: changestream.FullDocument) extends WatchCommand[T] {
    override def run(observable: ChangeStreamPublisher[T]): ChangeStreamPublisher[T] =
      observable.fullDocument(fullDocument)
  }

  final case class MaxAwaitTime[T](duration: Duration) extends WatchCommand[T] {
    override def run(observable: ChangeStreamPublisher[T]): ChangeStreamPublisher[T] =
      observable.maxAwaitTime(duration.toNanos, TimeUnit.NANOSECONDS)
  }

  final case class ResumeAfter[T](after: BsonDocument) extends WatchCommand[T] {
    override def run(observable: ChangeStreamPublisher[T]): ChangeStreamPublisher[T] =
      observable.resumeAfter(after)
  }

  final case class StartAfter[T](after: BsonDocument) extends WatchCommand[T] {
    override def run(observable: ChangeStreamPublisher[T]): ChangeStreamPublisher[T] =
      observable.startAfter(after)
  }

  final case class StartAtOperationTime[T](operationTime: BsonTimestamp) extends WatchCommand[T] {
    override def run(observable: ChangeStreamPublisher[T]): ChangeStreamPublisher[T] =
      observable.startAtOperationTime(operationTime)
  }
}

private[queries] object AggregateCommand {
  final case class AllowDiskUse[T](allowDiskUse: Boolean) extends AggregateCommand[T] {
    override def run(observable: AggregatePublisher[T]): AggregatePublisher[T] =
      observable.allowDiskUse(allowDiskUse)
  }

  final case class MaxTime[T](duration: Duration) extends AggregateCommand[T] {
    override def run(observable: AggregatePublisher[T]): AggregatePublisher[T] =
      observable.maxTime(duration.toNanos, TimeUnit.NANOSECONDS)
  }

  final case class MaxAwaitTime[T](duration: Duration) extends AggregateCommand[T] {
    override def run(observable: AggregatePublisher[T]): AggregatePublisher[T] =
      observable.maxAwaitTime(duration.toNanos, TimeUnit.NANOSECONDS)
  }

  final case class BypassDocumentValidation[T](bypassDocumentValidation: Boolean) extends AggregateCommand[T] {
    override def run(observable: AggregatePublisher[T]): AggregatePublisher[T] =
      observable.bypassDocumentValidation(bypassDocumentValidation)
  }

  final case class Collation[T](collation: model.Collation) extends AggregateCommand[T] {
    override def run(observable: AggregatePublisher[T]): AggregatePublisher[T] =
      observable.collation(collation)
  }

  final case class Comment[T](comment: String) extends AggregateCommand[T] {
    override def run(observable: AggregatePublisher[T]): AggregatePublisher[T] =
      observable.comment(comment)
  }

  final case class Hint[T](hint: Bson) extends AggregateCommand[T] {
    override def run(observable: AggregatePublisher[T]): AggregatePublisher[T] =
      observable.hint(hint)
  }

  final case class BatchSize[T](batchSize: Int) extends AggregateCommand[T] {
    override def run(observable: AggregatePublisher[T]): AggregatePublisher[T] =
      observable.batchSize(batchSize)
  }
}
