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
import com.mongodb.client.model.{changestream}
import com.mongodb.reactivestreams.client.{AggregatePublisher, ChangeStreamPublisher, DistinctPublisher, FindPublisher}
import org.bson.{BsonDocument, BsonTimestamp}
import org.bson.conversions.Bson
import org.reactivestreams.Publisher

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.Duration

sealed private[queries] trait QueryCommand[O[_] <: Publisher[_], T] {
  def run(pub: O[T]): O[T]
}

sealed private[queries] trait DistinctCommand[T]  extends QueryCommand[DistinctPublisher, T]
sealed private[queries] trait FindCommand[T]      extends QueryCommand[FindPublisher, T]
sealed private[queries] trait WatchCommand[T]     extends QueryCommand[ChangeStreamPublisher, T]
sealed private[queries] trait AggregateCommand[T] extends QueryCommand[AggregatePublisher, T]

private[queries] object FindCommand {

  final private[queries] case class ShowRecordId[T](showRecordId: Boolean) extends FindCommand[T] {
    override def run(pub: FindPublisher[T]): FindPublisher[T] =
      pub.showRecordId(showRecordId)
  }

  final private[queries] case class ReturnKey[T](returnKey: Boolean) extends FindCommand[T] {
    override def run(pub: FindPublisher[T]): FindPublisher[T] =
      pub.returnKey(returnKey)
  }

  final private[queries] case class Comment[T](comment: String) extends FindCommand[T] {
    override def run(pub: FindPublisher[T]): FindPublisher[T] =
      pub.comment(comment)
  }

  final private[queries] case class Collation[T](collation: model.Collation) extends FindCommand[T] {
    override def run(pub: FindPublisher[T]): FindPublisher[T] =
      pub.collation(collation)
  }

  final private[queries] case class Partial[T](partial: Boolean) extends FindCommand[T] {
    override def run(pub: FindPublisher[T]): FindPublisher[T] =
      pub.partial(partial)
  }

  final private[queries] case class MaxTime[T](duration: Duration) extends FindCommand[T] {
    override def run(pub: FindPublisher[T]): FindPublisher[T] =
      pub.maxTime(duration.toNanos, TimeUnit.NANOSECONDS)
  }

  final private[queries] case class Hint[T](hint: Bson) extends FindCommand[T] {
    override def run(pub: FindPublisher[T]): FindPublisher[T] =
      pub.hint(hint)
  }

  final private[queries] case class Max[T](index: Bson) extends FindCommand[T] {
    override def run(pub: FindPublisher[T]): FindPublisher[T] =
      pub.max(index)
  }

  final private[queries] case class Min[T](index: Bson) extends FindCommand[T] {
    override def run(pub: FindPublisher[T]): FindPublisher[T] =
      pub.min(index)
  }

  final private[queries] case class Skip[T](n: Int) extends FindCommand[T] {
    override def run(pub: FindPublisher[T]): FindPublisher[T] =
      pub.skip(n)
  }

  final private[queries] case class Limit[T](n: Int) extends FindCommand[T] {
    override def run(pub: FindPublisher[T]): FindPublisher[T] =
      pub.limit(n)
  }

  final private[queries] case class Sort[T](order: Bson) extends FindCommand[T] {
    override def run(pub: FindPublisher[T]): FindPublisher[T] =
      pub.sort(order)
  }

  final private[queries] case class Filter[T](filter: Bson) extends FindCommand[T] {
    override def run(pub: FindPublisher[T]): FindPublisher[T] =
      pub.filter(filter)
  }

  final private[queries] case class Projection[T](projection: Bson) extends FindCommand[T] {
    override def run(pub: FindPublisher[T]): FindPublisher[T] =
      pub.projection(projection)
  }
}

private[queries] object DistinctCommand {
  final private[queries] case class MaxTime[T](duration: Duration) extends DistinctCommand[T] {
    override def run(pub: DistinctPublisher[T]): DistinctPublisher[T] =
      pub.maxTime(duration.toMillis, TimeUnit.MILLISECONDS)
  }

  final private[queries] case class Filter[T](filter: Bson) extends DistinctCommand[T] {
    override def run(pub: DistinctPublisher[T]): DistinctPublisher[T] =
      pub.filter(filter)
  }

  final private[queries] case class BatchSize[T](size: Int) extends DistinctCommand[T] {
    override def run(pub: DistinctPublisher[T]): DistinctPublisher[T] =
      pub.batchSize(size)
  }

  final private[queries] case class Collation[T](collation: model.Collation) extends DistinctCommand[T] {
    override def run(pub: DistinctPublisher[T]): DistinctPublisher[T] =
      pub.collation(collation)
  }
}

private[queries] object WatchCommand {
  final private[queries] case class BatchSize[T](size: Int) extends WatchCommand[T] {
    override def run(pub: ChangeStreamPublisher[T]): ChangeStreamPublisher[T] =
      pub.batchSize(size)
  }

  final private[queries] case class Collation[T](collation: model.Collation) extends WatchCommand[T] {
    override def run(pub: ChangeStreamPublisher[T]): ChangeStreamPublisher[T] =
      pub.collation(collation)
  }

  final private[queries] case class FullDocument[T](fullDocument: changestream.FullDocument) extends WatchCommand[T] {
    override def run(pub: ChangeStreamPublisher[T]): ChangeStreamPublisher[T] =
      pub.fullDocument(fullDocument)
  }

  final private[queries] case class MaxAwaitTime[T](duration: Duration) extends WatchCommand[T] {
    override def run(pub: ChangeStreamPublisher[T]): ChangeStreamPublisher[T] =
      pub.maxAwaitTime(duration.toNanos, TimeUnit.NANOSECONDS)
  }

  final private[queries] case class ResumeAfter[T](after: BsonDocument) extends WatchCommand[T] {
    override def run(pub: ChangeStreamPublisher[T]): ChangeStreamPublisher[T] =
      pub.resumeAfter(after)
  }

  final private[queries] case class StartAfter[T](after: BsonDocument) extends WatchCommand[T] {
    override def run(pub: ChangeStreamPublisher[T]): ChangeStreamPublisher[T] =
      pub.startAfter(after)
  }

  final private[queries] case class StartAtOperationTime[T](operationTime: BsonTimestamp) extends WatchCommand[T] {
    override def run(pub: ChangeStreamPublisher[T]): ChangeStreamPublisher[T] =
      pub.startAtOperationTime(operationTime)
  }
}

private[queries] object AggregateCommand {
  final private[queries] case class AllowDiskUse[T](allowDiskUse: Boolean) extends AggregateCommand[T] {
    override def run(pub: AggregatePublisher[T]): AggregatePublisher[T] =
      pub.allowDiskUse(allowDiskUse)
  }

  final private[queries] case class MaxTime[T](duration: Duration) extends AggregateCommand[T] {
    override def run(pub: AggregatePublisher[T]): AggregatePublisher[T] =
      pub.maxTime(duration.toNanos, TimeUnit.NANOSECONDS)
  }

  final private[queries] case class MaxAwaitTime[T](duration: Duration) extends AggregateCommand[T] {
    override def run(pub: AggregatePublisher[T]): AggregatePublisher[T] =
      pub.maxAwaitTime(duration.toNanos, TimeUnit.NANOSECONDS)
  }

  final private[queries] case class BypassDocumentValidation[T](bypassDocumentValidation: Boolean) extends AggregateCommand[T] {
    override def run(pub: AggregatePublisher[T]): AggregatePublisher[T] =
      pub.bypassDocumentValidation(bypassDocumentValidation)
  }

  final private[queries] case class Collation[T](collation: model.Collation) extends AggregateCommand[T] {
    override def run(pub: AggregatePublisher[T]): AggregatePublisher[T] =
      pub.collation(collation)
  }

  final private[queries] case class Comment[T](comment: String) extends AggregateCommand[T] {
    override def run(pub: AggregatePublisher[T]): AggregatePublisher[T] =
      pub.comment(comment)
  }

  final private[queries] case class Let[T](variables: Bson) extends AggregateCommand[T] {
    override def run(pub: AggregatePublisher[T]): AggregatePublisher[T] =
      pub.let(variables)
  }

  final private[queries] case class Hint[T](hint: Bson) extends AggregateCommand[T] {
    override def run(pub: AggregatePublisher[T]): AggregatePublisher[T] =
      pub.hint(hint)
  }

  final private[queries] case class BatchSize[T](batchSize: Int) extends AggregateCommand[T] {
    override def run(pub: AggregatePublisher[T]): AggregatePublisher[T] =
      pub.batchSize(batchSize)
  }
}
