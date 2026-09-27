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

package mongo4cats.client

import com.mongodb.reactivestreams.client.{MongoClient => JMongoClient, MongoDatabase => JMongoDatabase}
import org.reactivestreams.Publisher

import java.lang.reflect.{InvocationHandler, Method, Proxy}

object SessionParityFixture {
  val names: List[String] = List("accounts", "events")

  def client(onList: Array[AnyRef] => Publisher[String]): JMongoClient =
    proxy(classOf[JMongoClient], "listDatabaseNames")(onList)

  def database(onCreate: Array[AnyRef] => Publisher[Void]): JMongoDatabase =
    proxy(classOf[JMongoDatabase], "createCollection")(onCreate)

  private def proxy[A](clazz: Class[A], expectedMethod: String)(onCall: Array[AnyRef] => AnyRef): A = {
    val handler = new InvocationHandler {
      override def invoke(instance: Any, method: Method, args: Array[AnyRef]): AnyRef =
        if (method.getName == expectedMethod) onCall(args)
        else throw new UnsupportedOperationException(s"Unexpected driver call: ${method.getName}")
    }
    clazz.cast(Proxy.newProxyInstance(clazz.getClassLoader, Array[Class[_]](clazz), handler))
  }
}
