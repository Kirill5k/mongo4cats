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

package mongo4cats.bson

import java.{util => ju}
import java.util.{Map => JMap, Set => JSet}
import java.util.Map.{Entry => JEntry}

final private[bson] case class JEntryOnlyForJDocumentCreation(
    override val getKey: String,
    override val getValue: Object
) extends JEntry[String, Object] {

  // Copied from [[scala.collection.convert.JavaCollectionWrappers.MapWrapper.entrySet]].
  // It's important that this implementation conform to the contract
  // specified in the javadocs of java.util.Map.Entry.hashCode
  // See https://github.com/scala/bug/issues/10663
  override def hashCode: Int = ??? // Unused for JDocument creation.
  // (if (getKey == null) 0 else getKey.hashCode()) ^ (if (getValue == null) 0 else getValue.hashCode())

  override def setValue(value: Object): Object = ??? // Unused for JDocument creation.
}

final private[bson] case class MapOnlyForJDocumentCreation(
    override val size: Int,
    entries: Iterable[JEntryOnlyForJDocumentCreation]
) extends JMap[String, Object] {
  self =>

  /** An Optimized version of [[scala.collection.convert.JavaCollectionWrappers.MapWrapper.entrySet]], implement only what is needed by
    * [[java.util.HashMap#putMapEntries(java.util.Map, boolean)]] (`size` & `entrySet`).
    *   - No allocations for `prev` var.
    *   - No allocations when copying `Tuple2` to `JEntry`.
    */
  override def entrySet: JSet[JEntry[String, Object]] =
    new ju.AbstractSet[JEntry[String, Object]] {
      override val size: Int = self.size

      override def iterator: ju.Iterator[JEntry[String, Object]] = {
        val scalaIterator: Iterator[JEntryOnlyForJDocumentCreation] = entries.iterator

        new ju.Iterator[JEntry[String, Object]] {
          def hasNext: Boolean               = scalaIterator.hasNext
          def next(): JEntry[String, Object] = scalaIterator.next()

          override def remove(): Unit = ??? // Unused for JDocument creation.
        }
      }
    }

  override def isEmpty: Boolean                                = ??? // Unused for JDocument creation.
  override def containsKey(key: Any): Boolean                  = ??? // Unused for JDocument creation.
  override def containsValue(value: Any): Boolean              = ??? // Unused for JDocument creation.
  override def get(key: Any): Object                           = ??? // Unused for JDocument creation.
  override def put(key: String, value: Object): Object         = ??? // Unused for JDocument creation.
  override def remove(key: Any): Object                        = ??? // Unused for JDocument creation.
  override def putAll(m: JMap[_ <: String, _ <: Object]): Unit = ??? // Unused for JDocument creation.
  override def clear(): Unit                                   = ??? // Unused for JDocument creation.
  override def keySet(): JSet[String]                          = ??? // Unused for JDocument creation.
  override def values(): java.util.Collection[Object]          = ??? // Unused for JDocument creation.
}
