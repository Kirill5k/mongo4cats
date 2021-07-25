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

import scala.jdk.CollectionConverters._
import org.bson.{Document => JDocument}

object bson {

  type Document = JDocument
  object Document {
    val empty: Document                                = new JDocument()
    def apply[A](key: String, value: String): Document = new JDocument(key, value)
    def apply(entries: Map[String, AnyRef]): Document  = new JDocument(entries.asJava)
    def fromJson(json: String): Document               = JDocument.parse(json)
    def parse(json: String): Document                  = fromJson(json)
  }
}
