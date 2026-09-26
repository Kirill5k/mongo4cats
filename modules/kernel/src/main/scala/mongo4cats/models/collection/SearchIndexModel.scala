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

import com.mongodb.client.model.{SearchIndexModel => JSearchIndexModel}
import org.bson.conversions.Bson

/** Describes a Search or Vector Search index. An omitted name uses the server's `default` index name.
  *
  * Pass `SearchIndexType.vectorSearch` explicitly for vector definitions, including definitions built with Java driver builders. Definition
  * validation is delegated to the driver and server when the collection operation runs.
  */
final case class SearchIndexModel(
    definition: Bson,
    name: Option[String] = None,
    indexType: SearchIndexType = SearchIndexType.search
) {
  private[mongo4cats] def toJava: JSearchIndexModel = new JSearchIndexModel(name.orNull, definition, indexType)
}
