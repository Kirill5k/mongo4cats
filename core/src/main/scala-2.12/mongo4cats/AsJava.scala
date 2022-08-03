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

import java.util.{concurrent => juc}
import java.{lang => jl, util => ju}
import scala.collection.mutable

trait AsJava extends scala.collection.convert.AsJavaConverters {

  def asJava[A](i: Iterator[A]): ju.Iterator[A]                                       = asJavaIterator(i)
  def asJava[A](i: Iterable[A]): jl.Iterable[A]                                       = asJavaIterable(i)
  def asJava[A](b: mutable.Buffer[A]): ju.List[A]                                     = bufferAsJavaList(b)
  def asJava[A](s: mutable.Seq[A]): ju.List[A]                                        = mutableSeqAsJavaList(s)
  def asJava[A](s: Seq[A]): ju.List[A]                                                = seqAsJavaList(s)
  def asJava[A](s: mutable.Set[A]): ju.Set[A]                                         = mutableSetAsJavaSet(s)
  def asJava[A](s: Set[A]): ju.Set[A]                                                 = setAsJavaSet(s)
  def asJava[K, V](m: mutable.Map[K, V]): ju.Map[K, V]                                = mutableMapAsJavaMap(m)
  def asJava[K, V](m: Map[K, V]): ju.Map[K, V]                                        = mapAsJavaMap(m)
  def asJava[K, V](m: scala.collection.concurrent.Map[K, V]): juc.ConcurrentMap[K, V] = mapAsJavaConcurrentMap(m)
}
