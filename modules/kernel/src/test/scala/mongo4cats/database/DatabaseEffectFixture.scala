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

package mongo4cats.database

import com.mongodb.ReadPreference
import com.mongodb.reactivestreams.client.{ListCollectionNamesPublisher, ListCollectionsPublisher, MongoDatabase => JMongoDatabase}
import mongo4cats.bson.Document
import mongo4cats.client.ClientSession
import mongo4cats.codecs.CodecRegistry
import mongo4cats.collection.SearchIndexFixture
import mongo4cats.models.database.CreateCollectionOptions
import org.bson.{Document => JDocument}
import org.reactivestreams.{Publisher, Subscriber}

import java.lang.reflect.{InvocationHandler, Method, Proxy}

object DatabaseEffectFixture {
  final case class Operation[F[+_], S[_]](
      label: String,
      method: String,
      arguments: List[AnyRef],
      values: List[AnyRef],
      expected: Any,
      run: GenericMongoDatabase[F, S] => F[Any],
      defaultOptions: Boolean = false
  ) {
    def verifyArguments(actual: Array[AnyRef]): Unit = {
      require(actual.length == arguments.length + (if (defaultOptions) 1 else 0), s"Incorrect argument count for $label")
      require(
        actual.take(arguments.length).zip(arguments).forall { case (value, expected) =>
          (value eq expected) || value == expected
        },
        s"Incorrect arguments for $label"
      )
      if (defaultOptions) {
        val options = actual.last.asInstanceOf[CreateCollectionOptions]
        require(!options.isCapped && options.getSizeInBytes == 0L, "Expected default collection options")
      }
    }

    def response(error: Option[Throwable] = None): AnyRef = {
      val source = SearchIndexFixture.publisher(if (error.isDefined) Nil else values, error)
      method match {
        case "listCollectionNames" => listing(classOf[ListCollectionNamesPublisher], source)
        case "listCollections"     => listing(classOf[ListCollectionsPublisher[_]], source)
        case _                     => source
      }
    }
  }

  def database(onCall: (String, Array[AnyRef]) => AnyRef): JMongoDatabase =
    rawDatabase { (method, arguments) =>
      if (method == "getCodecRegistry") CodecRegistry.Default else onCall(method, arguments)
    }

  def rawDatabase(onCall: (String, Array[AnyRef]) => AnyRef): JMongoDatabase = {
    val handler = new InvocationHandler {
      override def invoke(instance: Any, method: Method, args: Array[AnyRef]): AnyRef =
        onCall(method.getName, Option(args).getOrElse(Array.empty[AnyRef]))
    }
    Proxy
      .newProxyInstance(classOf[JMongoDatabase].getClassLoader, Array[Class[_]](classOf[JMongoDatabase]), handler)
      .asInstanceOf[JMongoDatabase]
  }

  private def listing[A](interface: Class[_], source: Publisher[A]): AnyRef = {
    val handler = new InvocationHandler {
      override def invoke(instance: Any, method: Method, args: Array[AnyRef]): AnyRef = method.getName match {
        case "subscribe" =>
          source.subscribe(args(0).asInstanceOf[Subscriber[_ >: A]])
          null
        case name => throw new UnsupportedOperationException(s"Unexpected listing call: $name")
      }
    }
    Proxy.newProxyInstance(interface.getClassLoader, Array[Class[_]](interface), handler)
  }

  def operations[F[+_], S[_]](session: ClientSession[F]): List[Operation[F, S]] = {
    type Database = GenericMongoDatabase[F, S]
    val names      = List("first", "second")
    val metadata   = List(JDocument.parse("""{"name":"first","type":"collection"}"""))
    val command    = Document.parse("""{"ping":1}""")
    val result     = JDocument.parse("""{"ok":1}""")
    val preference = ReadPreference.secondaryPreferred()
    val options    = CreateCollectionOptions().capped(true).sizeInBytes(4096L)

    def paired[A](label: String, method: String, args: List[AnyRef], values: List[AnyRef], expected: Any, defaultOptions: Boolean = false)(
        plain: Database => F[A],
        sessioned: Database => F[A]
    ): List[Operation[F, S]] = List(
      Operation(label, method, args, values, expected, plain, defaultOptions),
      Operation(s"$label with session", method, session.underlying :: args, values, expected, sessioned, defaultOptions)
    )

    List(
      paired("list collection names", "listCollectionNames", Nil, names, names)(_.listCollectionNames, _.listCollectionNames(session)),
      paired("list collections", "listCollections", Nil, metadata, metadata.map(Document.fromJava))(
        _.listCollections,
        _.listCollections(session)
      ),
      paired("create collection with options", "createCollection", List("created", options), Nil, ())(
        _.createCollection("created", options),
        _.createCollection(session, "created", options)
      ),
      paired("create collection", "createCollection", List("created"), Nil, (), defaultOptions = true)(
        _.createCollection("created"),
        _.createCollection(session, "created")
      ),
      paired("run command with preference", "runCommand", List(command, preference), List(result), Document.fromJava(result))(
        _.runCommand(command, preference),
        _.runCommand(session, command, preference)
      ),
      paired("run command", "runCommand", List(command, ReadPreference.primary()), List(result), Document.fromJava(result))(
        _.runCommand(command),
        _.runCommand(session, command)
      ),
      paired("drop", "drop", Nil, Nil, ())(_.drop, _.drop(session))
    ).flatten
  }
}
