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

import com.mongodb.client.model.changestream.{
  ChangeStreamDocument => JChangeStreamDocument,
  OperationType,
  SplitEvent,
  TruncatedArray,
  UpdateDescription => JUpdateDescription
}
import mongo4cats.bson.{BsonValue, Document}

import java.time.Instant

final case class UpdateDescription(
    removedFields: List[String],
    updatedFields: Option[Document],
    truncatedArrays: List[TruncatedArray],
    disambiguatedPaths: Option[Document]
)

object UpdateDescription {
  private[mongo4cats] def fromJava(ud: JUpdateDescription): UpdateDescription =
    UpdateDescription(
      removedFields = Option(ud.getRemovedFields).fold(List.empty[String])(fromJavaList),
      updatedFields = Option(ud.getUpdatedFields).map(Document.fromJava),
      truncatedArrays = Option(ud.getTruncatedArrays).fold(List.empty[TruncatedArray])(fromJavaList),
      disambiguatedPaths = Option(ud.getDisambiguatedPaths).map(Document.fromJava)
    )

  private def fromJavaList[A](l: java.util.List[A]): List[A] = {
    val lb = scala.collection.mutable.ListBuffer.empty[A]
    l.forEach(e => lb += e)
    lb.toList
  }
}

/** Represents the {@code $changeStream} aggregation output document.
  *
  * @param resumeToken
  *   the resume token
  * @param namespace
  *   the namespace, derived from the "ns" field in a change stream document.
  * @param destinationNamespace
  *   the destinatation namespace, used to indicate the destination of a collection rename event.
  * @param operationType
  *   the operation type
  * @param fullDocument
  *   the full document, Contains the point-in-time post-image of the modified document if the post-image is available and either
  *   {@link FullDocument# REQUIRED} or {@link FullDocument# WHEN_AVAILABLE} was specified for the {@code fullDocument} option when creating
  *   the change stream. A post-image is always available for {@link OperationType# INSERT} and {@link OperationType# REPLACE} events.
  * @param fullDocumentBeforeChange
  *   the full document before change. Contains the pre-image of the modified or deleted document if the pre-image is available for the
  *   change event and either {@link FullDocumentBeforeChange# REQUIRED} or {@link FullDocumentBeforeChange# WHEN_AVAILABLE} was specified
  *   for the {@code fullDocumentBeforeChange} option when creating the change stream. If {@link FullDocumentBeforeChange# WHEN_AVAILABLE}
  *   was specified but the pre-image is unavailable, the value will be null.
  * @param documentKey
  *   a document containing the _id of the changed document
  * @param clusterTime
  *   the cluster time at which the change occured
  * @param updateDescription
  *   the update description
  * @param txnNumber
  *   the transaction number
  * @param lsid
  *   the identifier for the session associated with the transaction
  * @param wallTime
  *   the wall time of the server at the moment the change occurred
  * @param splitEvent
  *   the split event
  * @param extraElements
  *   any extra elements that are part of the change stream document but not otherwise mapped to fields
  * @since 4.11
  */
final case class ChangeStreamDocument[T](
    resumeToken: Document,
    namespace: Option[MongoNamespace],
    destinationNamespace: Option[MongoNamespace],
    operationType: Option[OperationType],
    fullDocument: Option[T],
    fullDocumentBeforeChange: Option[T],
    documentKey: Option[Document],
    clusterTime: Option[BsonValue],
    updateDescription: Option[UpdateDescription],
    txnNumber: Option[BsonValue],
    lsid: Option[Document],
    wallTime: Option[BsonValue],
    splitEvent: Option[SplitEvent],
    extraElements: Option[Document]
)

object ChangeStreamDocument {
  private[mongo4cats] def fromJava[T](cst: JChangeStreamDocument[T]): ChangeStreamDocument[T] =
    ChangeStreamDocument(
      resumeToken = Document.fromJava(cst.getResumeToken),
      namespace = Option(cst.getNamespace).map(MongoNamespace.fromJava),
      destinationNamespace = Option(cst.getDestinationNamespace).map(MongoNamespace.fromJava),
      fullDocument = Option(cst.getFullDocument),
      fullDocumentBeforeChange = Option(cst.getFullDocumentBeforeChange),
      documentKey = Option(cst.getDocumentKey).map(Document.fromJava),
      operationType = Option(cst.getOperationType),
      updateDescription = Option(cst.getUpdateDescription).map(UpdateDescription.fromJava),
      txnNumber = Option(cst.getTxnNumber).map(n => BsonValue.BInt64(n.longValue())),
      lsid = Option(cst.getLsid).map(Document.fromJava),
      splitEvent = Option(cst.getSplitEvent),
      extraElements = Option(cst.getExtraElements).map(Document.fromJava),
      clusterTime = Option(cst.getClusterTime).map(ct => BsonValue.timestamp(ct.getTime.toLong, ct.getInc)),
      wallTime = Option(cst.getWallTime).map(wt => BsonValue.instant(Instant.ofEpochMilli(wt.getValue)))
    )
}
